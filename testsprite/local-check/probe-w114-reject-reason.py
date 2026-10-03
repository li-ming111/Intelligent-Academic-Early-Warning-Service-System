# -*- coding: utf-8 -*-
"""
W1.14 端到端探针：拒绝班级申请时填的理由，真的落库、真的能回读。

背景：`POST /admin/class-management/reject/{id}` 的 `@RequestBody Map<String,String> rejectData`
**收下后一次都没读**，两个前端却都收了理由也真的发了出去。同批 `counselor_class_application`
连列都没有。本探针走完整链路（HTTP → controller → MyBatis → MySQL），不是只看单测。

做法：
  ① 自己 INSERT 一条**临时**申请行（class_name 带探针标记），拿到 id；
  ② 经 HTTP 调拒绝接口，理由里带中文；
  ③ 再从**列表接口**读回来，比对理由与状态；
  ④ 负向对照：不带头 / 只带 X-User-Id ⇒ 必须 403；
  ⑤ 收尾：DELETE 掉**自己插的那条**（绝不碰任何已有数据行）。

用 Python 而不是 curl 的原因：CLI 发中文会被 MSYS 编成 CP936 ⇒ HTTP 500
`Invalid UTF-8 middle byte`；而且 stdout 的中文是 CP936 乱码，判据没法写。

用法：/d/Python/python probe-w114-reject-reason.py
"""
import json
import subprocess
import sys
import urllib.error
import urllib.request

# ⚠️ 2026-10-03：Windows 控制台默认 GBK，而本脚本的判据标签里用了 `⇒`(U+21D2) ——
# 它**不在 GBK 里** ⇒ `print()` 抛 UnicodeEncodeError。更糟的是崩在"打印一条 PASS"上，
# 于是断言全对、脚本却异常退出，**还跳过了收尾的 DELETE**（本次在
# teacher_class_application 里留下 2 行 W114-PROBE-*）。
# 只把编不出来的字符换成 '?'，不改编码（与其他探针的控制台行为保持一致）。
sys.stdout.reconfigure(errors="replace")

ADMIN = "http://localhost:8070"
COUNSELOR = "http://localhost:8074"
TEACHER = "http://localhost:8090"
MYSQL = ["mysql", "-uroot", "-proot", "-D", "academic_warning_system",
         "--default-character-set=utf8mb4", "-N", "-B", "-e"]

# 探针标记：只出现在本脚本插的行上，收尾按它删
MARK = "W114-PROBE"

failures = []
checks = 0


def sql(stmt):
    """跑一条 SQL，返回 tab 分隔的行列表（每行是一个 list）。"""
    out = subprocess.run(MYSQL + [stmt], capture_output=True)
    if out.returncode != 0:
        raise RuntimeError("mysql failed: " + out.stderr.decode("utf-8", "replace"))
    text = out.stdout.decode("utf-8", "replace")
    return [line.split("\t") for line in text.splitlines() if line.strip()]


def http(method, url, headers=None, body=None):
    data = None
    hdrs = dict(headers or {})
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        hdrs["Content-Type"] = "application/json; charset=utf-8"
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


def check(name, ok, detail=""):
    global checks
    checks += 1
    if ok:
        print("  [PASS] %s" % name)
    else:
        print("  [FAIL] %s  %s" % (name, detail))
        failures.append(name)


AUTH = {"X-User-Id": "1", "X-Role": "4"}


# --------------------------------------------------------------------------
# 起手先清上一轮的残留：上一轮若中途崩了（比如打印时 UnicodeEncodeError），
# 收尾那两句 DELETE 根本没执行，会留下 W114-PROBE-* 行 —— 而 `class_id` 那一列
# 这些行都写 999999，不影响别的数据，但会污染"申请表总行数"这个核对。
for _t in ("teacher_class_application", "counselor_class_application"):
    sql("DELETE FROM %s WHERE class_name LIKE '%s-%%';" % (_t, MARK))

# --------------------------------------------------------------------------
print("== 1. 教师申请：拒绝理由落库并回读 ==")
sql("INSERT INTO teacher_class_application"
    "(teacher_id, class_id, reason, status, create_time, class_name, teacher_name,"
    " teacher_username, application_type) VALUES"
    "(1, 999999, 'probe', 0, NOW(), '%s-TEACHER', 'probe', 'probe', 0);" % MARK)
tid = sql("SELECT id FROM teacher_class_application WHERE class_name = '%s-TEACHER'" % MARK)[0][0]
print("  临时申请行 id=%s" % tid)

REASON = "材料不全，请补齐班级证明"
st, body = http("POST", "%s/admin/class-management/reject/%s?type=teacher" % (ADMIN, tid),
                AUTH, {"reason": REASON})
check("拒绝接口返回 200", st == 200, "HTTP %s %s" % (st, body[:200]))

st, body = http("GET", "%s/admin/class-management/applications?page=1&size=999" % ADMIN, AUTH)
check("列表接口返回 200", st == 200, "HTTP %s" % st)
row = None
if st == 200:
    payload = json.loads(body)
    for item in (payload.get("data") or {}).get("items") or []:
        if str(item.get("id")) == tid and item.get("type") == "teacher":
            row = item
            break
check("列表里能找到这条申请", row is not None)
if row:
    check("status 翻成 2（已拒绝）", row.get("status") == 2, "实际 %r" % row.get("status"))
    check("rejectReason 与提交的一字不差", row.get("rejectReason") == REASON,
          "实际 %r" % row.get("rejectReason"))

# 直接查库对照：接口说写了，库里到底有没有
db = sql("SELECT status, reject_reason FROM teacher_class_application WHERE id = %s" % tid)[0]
check("库里 status=2", db[0] == "2", "实际 %r" % db[0])
check("库里 reject_reason 与提交的一致", db[1] == REASON, "实际 %r" % db[1])

# 申请人自己那一侧（teacher-service，与 admin-service 是**另一个服务**）也要读得回来
st, body = http("GET", "%s/teachers/class-management/requests?teacherId=1" % TEACHER, AUTH)
trow = None
if st == 200:
    for item in (json.loads(body).get("data") or []):
        if str(item.get("id")) == tid:
            trow = item
            break
check("教师端「申请记录」读得回拒绝理由",
      trow is not None and trow.get("rejectReason") == REASON,
      "HTTP %s，实际 %r" % (st, (trow or {}).get("rejectReason")))

# --------------------------------------------------------------------------
print("== 2. 教师申请：空白理由必须存 NULL（不是空串）==")
sql("INSERT INTO teacher_class_application"
    "(teacher_id, class_id, reason, status, create_time, class_name, teacher_name,"
    " teacher_username, application_type) VALUES"
    "(1, 999999, 'probe', 0, NOW(), '%s-BLANK', 'probe', 'probe', 0);" % MARK)
bid = sql("SELECT id FROM teacher_class_application WHERE class_name = '%s-BLANK'" % MARK)[0][0]
http("POST", "%s/admin/class-management/reject/%s?type=teacher" % (ADMIN, bid), AUTH, {"reason": "   "})
brow = sql("SELECT status, reject_reason FROM teacher_class_application WHERE id = %s" % bid)[0]
check("库里 status=2", brow[0] == "2", "实际 %r" % brow[0])
check("空白理由存的是 NULL 而不是空串", brow[1] == "NULL", "实际 %r" % brow[1])

# --------------------------------------------------------------------------
print("== 3. 负向对照：身份头 ==")
st_none, _ = http("POST", "%s/admin/class-management/reject/%s?type=teacher" % (ADMIN, bid), {}, {"reason": "x"})
check("不带头 => 403", st_none == 403, "实际 HTTP %s" % st_none)
st_half, _ = http("POST", "%s/admin/class-management/reject/%s?type=teacher" % (ADMIN, bid),
                  {"X-User-Id": "1"}, {"reason": "x"})
check("只带 X-User-Id、缺 X-Role => 403", st_half == 403, "实际 HTTP %s" % st_half)
st_role, _ = http("POST", "%s/admin/class-management/reject/%s?type=teacher" % (ADMIN, bid),
                  {"X-Role": "4"}, {"reason": "x"})
check("只带 X-Role、缺 X-User-Id => 403", st_role == 403, "实际 HTTP %s" % st_role)

# --------------------------------------------------------------------------
print("== 4. 辅导员申请：另一条实现（counselor-service）也要存理由 ==")
sql("INSERT INTO counselor_class_application"
    "(counselor_id, class_id, reason, status, create_time, class_name, counselor_name,"
    " application_type) VALUES"
    "(1, 999999, 'probe', 0, NOW(), '%s-COUNSELOR', 'probe', 0);" % MARK)
cid = sql("SELECT id FROM counselor_class_application WHERE class_name = '%s-COUNSELOR'" % MARK)[0][0]
CREASON = "该班已由另一位辅导员接管"
st, body = http("POST", "%s/counselors/class-management/applications/%s/review" % (COUNSELOR, cid),
                AUTH, {"status": 2, "reason": CREASON})
check("辅导员审核接口返回 200", st == 200, "HTTP %s %s" % (st, body[:200]))

cdb = sql("SELECT status, reject_reason FROM counselor_class_application WHERE id = %s" % cid)[0]
check("库里 status=2", cdb[0] == "2", "实际 %r" % cdb[0])
check("库里 reject_reason 与提交的一致", cdb[1] == CREASON, "实际 %r" % cdb[1])

st, body = http("GET", "%s/counselors/class-management/requests?counselorId=1" % COUNSELOR, AUTH)
crow = None
if st == 200:
    for item in (json.loads(body).get("data") or []):
        if str(item.get("id")) == cid:
            crow = item
            break
check("申请人自己的列表能读回拒绝理由",
      crow is not None and crow.get("rejectReason") == CREASON,
      "实际 %r" % (crow or {}).get("rejectReason"))

# --------------------------------------------------------------------------
print("== 5. 收尾：只删本探针自己插的两行 ==")
sql("DELETE FROM teacher_class_application WHERE class_name LIKE '%s-%%';" % MARK)
sql("DELETE FROM counselor_class_application WHERE class_name LIKE '%s-%%';" % MARK)
left_t = sql("SELECT COUNT(*) FROM teacher_class_application WHERE class_name LIKE '%s-%%'" % MARK)[0][0]
left_c = sql("SELECT COUNT(*) FROM counselor_class_application WHERE class_name LIKE '%s-%%'" % MARK)[0][0]
check("临时行已清干净（教师表）", left_t == "0", "剩 %s" % left_t)
check("临时行已清干净（辅导员表）", left_c == "0", "剩 %s" % left_c)

# 全表行数（对照：演示数据一行没动）
print("  教师申请表总行数 = %s" % sql("SELECT COUNT(*) FROM teacher_class_application")[0][0])
print("  辅导员申请表总行数 = %s" % sql("SELECT COUNT(*) FROM counselor_class_application")[0][0])

print()
print("== 结果：%d 项检查，%d 项失败 ==" % (checks, len(failures)))
for f in failures:
    print("  FAILED: %s" % f)
sys.exit(1 if failures else 0)
