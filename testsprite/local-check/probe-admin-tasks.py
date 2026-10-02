#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
管理端任务 CRUD 的端到端探针（W1.11，2026-10-02）。

被验对象：`AdminController` 的 `/admin/tasks/*` 四个端点。它们在 W1.11 之前是**整组桩**
（列表写死空数组；创建/改状态/删除只 log.info 一行就回 "…successfully"），
PC `AdminMessages.vue` 的「任务管理」tab 因此永远空、点什么都"成功"。

跑法：
    PYTHONUTF8=1 /d/Python/python testsprite/local-check/probe-admin-tasks.py [port]

默认 port=8070（admin-service 直连）。要过网关就传 8076 并把 `--via-gateway` 的
token 逻辑打开（本脚本默认直连，与 probe-backup.py 同一手法）。

判据要点（踩过的坑都在这里）：
  · 本项目错误约定是 **HTTP 200 + body.code 500**，所以断言看 `code` 不看 HTTP 状态；
  · 直连的身份头是 `X-User-Id` + `X-Role`（**不是** X-User-Role），且必须带
    「不带头 / 只带一个头」的负向对照 —— 否则"403 是因为没权限"和"403 是因为头名写错"
    分不开；
  · `created_by` 有指向 `users.id` 的真外键 ⇒ 解析不出操作人必须写 NULL，写 0 会 1452；
  · 本仓没有 MetaObjectHandler ⇒ `updated_at` 必须由 SQL 显式写，改状态后要**真的变大**。
"""

import json
import subprocess
import sys
import urllib.error
import urllib.request

PORT = sys.argv[1] if len(sys.argv) > 1 else "8070"
BASE = "http://localhost:%s" % PORT
ADMIN = {"X-User-Id": "1", "X-Role": "4", "X-Username": "admin"}
MYSQL = r"D:\JAVA\mysql\mysql-8.0.44-winx64\mysql-8.0.44-winx64\bin\mysql.exe"

PASS = []
FAIL = []


def check(name, ok, detail=""):
    (PASS if ok else FAIL).append(name)
    print("%s %s%s" % ("PASS" if ok else "FAIL", name, ("  <- " + str(detail)) if detail else ""))


def req(method, path, body=None, headers=None):
    """回 (http_status, parsed_json_or_None, raw_text)。"""
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        r.add_header(k, v)
    try:
        with urllib.request.urlopen(r, timeout=15) as resp:
            raw = resp.read().decode("utf-8", "replace")
            return resp.status, (json.loads(raw) if raw.strip().startswith("{") else None), raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw), raw
        except Exception:
            return e.code, None, raw


def sql(query):
    """只读数字/id/时间，别读中文（CLI stdout 是 CP936 乱码 ⇒ 中文一律走 API 回读）。"""
    out = subprocess.run(
        [MYSQL, "-uroot", "-proot", "--default-character-set=utf8mb4",
         "-D", "academic_warning_system", "-N", "-B", "-e", query],
        capture_output=True, text=True, encoding="utf-8", errors="replace")
    lines = [l for l in (out.stdout or "").splitlines() if l.strip()]
    return lines


def one(query):
    rows = sql(query)
    return rows[0] if rows else None


def count(query):
    v = one(query)
    return int(v) if v not in (None, "") else 0


def api(method, path, body=None, headers=ADMIN):
    st, js, raw = req(method, path, body, headers)
    return js if js is not None else {"_http": st, "_raw": raw}


def rows(**kw):
    q = "/admin/tasks/list?page=1&size=100"
    if kw.get("status"):
        q += "&status=" + kw["status"]
    js = api("GET", q)
    assert js.get("code") == 200, js
    return js["data"]["items"], js["data"]["total"]


print("=== 管理端任务 CRUD 探针 (W1.11) BASE=%s ===" % BASE)

base_rows = count("SELECT COUNT(*) FROM admin_tasks")
base_audit = count("SELECT COUNT(*) FROM audit_logs")
print("基线: admin_tasks=%d 行, audit_logs=%d 行" % (base_rows, base_audit))

created = []

# ---------- 1. 身份闸（负向对照三个） ----------
js = api("GET", "/admin/tasks/list", headers={})
check("不带任何身份头 → 403", js.get("_http") == 403, js)
js = api("GET", "/admin/tasks/list", headers={"X-User-Id": "1"})
check("只带 X-User-Id 缺 X-Role → 403", js.get("_http") == 403, js)
js = api("GET", "/admin/tasks/list", headers={"X-Role": "4"})
check("只带 X-Role 缺 X-User-Id → 403", js.get("_http") == 403, js)

# ---------- 2. 创建：入参校验 ----------
before = count("SELECT COUNT(*) FROM admin_tasks")
js = api("POST", "/admin/tasks/create", {"title": "   ", "priority": "high"})
check("空标题被拒（code != 200 且提示标题）",
      js.get("code") != 200 and "标题" in (js.get("message") or ""), js)
js = api("POST", "/admin/tasks/create", {"priority": "high"})
check("缺 title 字段被拒", js.get("code") != 200, js)
js = api("POST", "/admin/tasks/create", {"title": "probe-任务A", "priority": "urgent"})
check("非法优先级被拒（code != 200 且提示优先级）",
      js.get("code") != 200 and "优先级" in (js.get("message") or ""), js)
check("★ 三次非法创建后库行数没变（不是「报错了但已经写进去了」）",
      count("SELECT COUNT(*) FROM admin_tasks") == before,
      "before=%d after=%d" % (before, count("SELECT COUNT(*) FROM admin_tasks")))

# ---------- 3. 创建：正常路径 ----------
js = api("POST", "/admin/tasks/create",
         {"title": "probe-任务A", "description": "端到端探针", "priority": "high"})
check("创建成功 code==200 且回 id", js.get("code") == 200 and js.get("data", {}).get("id"), js)
a_id = js.get("data", {}).get("id")
created.append(a_id)

js = api("POST", "/admin/tasks/create", {"title": "probe-任务B"})
b_id = js.get("data", {}).get("id")
created.append(b_id)
check("不传优先级 → 默认 medium", js.get("code") == 200, js)

# ---------- 4. 落库核对（SQL 直读，避开中文乱码） ----------
row = one("SELECT id, priority, status, created_by, created_at IS NOT NULL, updated_at IS NOT NULL "
          "FROM admin_tasks WHERE id = %s" % a_id)
check("★ 任务真的落库了（不是只回了个成功）", row is not None, row)
if row:
    f = row.split("\t")
    check("  优先级存的是 high", f[1] == "high", f)
    check("  新建状态恒为 pending（页面「待处理」卡片）", f[2] == "pending", f)
    check("★ created_by = 1（真外键指向 users.id，不是 0）", f[3] == "1", f)
    check("  created_at / updated_at 都非空", f[4] == "1" and f[5] == "1", f)

row_b = one("SELECT priority FROM admin_tasks WHERE id = %s" % b_id)
check("  默认优先级在库里也是 medium", row_b == "medium", row_b)

# ---------- 5. 列表：形状与内容 ----------
items, total = rows()
by_id = {str(i["id"]): i for i in items}
check("列表 total 是真实行数（不是恒 0）", total == before + 2, "total=%s expect=%s" % (total, before + 2))
it = by_id.get(str(a_id))
check("列表里能拿到刚建的那条", it is not None, list(by_id.keys()))
if it:
    for key in ("id", "title", "description", "priority", "status",
                "createdBy", "createdByUsername", "createdAt", "updatedAt"):
        check("  列表项含键 %s（页面那一列靠它渲染）" % key, key in it, sorted(it.keys()))
    check("  createdByUsername 真 JOIN 到了 users.username=admin",
          it.get("createdByUsername") == "admin", it.get("createdByUsername"))
    check("  中文标题经 API 回读无损（CLI 会乱码，所以这里判）",
          it.get("title") == "probe-任务A", repr(it.get("title")))

items_p, total_p = rows(status="pending")
check("按状态筛选 pending 能查到这两条", total_p >= 2 and len(items_p) >= 2,
      "total=%s len=%s" % (total_p, len(items_p)))

# ---------- 6. 改状态 ----------
# ★ 先把它踩到一个远古时刻再改状态 —— 别拿"插入时刻 vs 改状态时刻"比，
#   两次调用常常落在同一秒（NOW() 秒级精度），那是个假阴性制造机。
sql("UPDATE admin_tasks SET updated_at = '2000-01-01 00:00:00' WHERE id = %s" % a_id)
js = api("POST", "/admin/tasks/%s/status" % a_id, {"status": "in_progress"})
check("改状态为 in_progress → 200", js.get("code") == 200, js)
check("  DB 里状态真的变了",
      one("SELECT status FROM admin_tasks WHERE id = %s" % a_id) == "in_progress")
after_upd = one("SELECT updated_at FROM admin_tasks WHERE id = %s" % a_id)
check("★ updated_at 真的被写了（本仓无 MetaObjectHandler，UPDATE 不显式写就停在 2000-01-01）",
      after_upd not in (None, "2000-01-01 00:00:00"), "after=%s" % after_upd)

js = api("POST", "/admin/tasks/%s/status" % a_id, {"status": "done"})
check("非法状态被拒（code != 200 且提示状态）",
      js.get("code") != 200 and "状态" in (js.get("message") or ""), js)
check("  被拒后库里状态没被改坏",
      one("SELECT status FROM admin_tasks WHERE id = %s" % a_id) == "in_progress")

js = api("POST", "/admin/tasks/999999/status", {"status": "completed"})
check("★ 改不存在的 id → 报「任务不存在」而不是「改好了」",
      js.get("code") != 200 and "任务不存在" in (js.get("message") or ""), js)

for st in ("pending", "completed"):
    js = api("POST", "/admin/tasks/%s/status" % b_id, {"status": st})
    check("三态都放行: %s" % st, js.get("code") == 200, js)

# ---------- 7. 删除 ----------
js = api("DELETE", "/admin/tasks/999999")
check("★ 删不存在的 id → 报「任务不存在」",
      js.get("code") != 200 and "任务不存在" in (js.get("message") or ""), js)

js = api("DELETE", "/admin/tasks/%s" % a_id)
check("删除存在的任务 → 200", js.get("code") == 200, js)
check("  DB 里那行真没了", count("SELECT COUNT(*) FROM admin_tasks WHERE id = %s" % a_id) == 0)
js = api("DELETE", "/admin/tasks/%s" % a_id)
check("★ 重复删同一条 → 第二次如实报失败（不是又回一次成功）", js.get("code") != 200, js)

api("DELETE", "/admin/tasks/%s" % b_id)

# ---------- 8. 审计日志真的记了 ----------
n_audit = count("SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'ADMIN_TASK'")
check("★ audit_logs 里有 ADMIN_TASK 的记录（创建/改状态/删除各记一条）", n_audit >= 5, n_audit)
n_creator = count("SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'ADMIN_TASK' AND user_id = 1")
check("  审计的操作人是 X-User-Id 对应的真用户（user_id=1）", n_creator >= 5, n_creator)

# ---------- 9. 收尾：回到基线 ----------
left = count("SELECT COUNT(*) FROM admin_tasks")
check("★ 收尾后 admin_tasks 回到基线（不留残余）", left == base_rows,
      "base=%d now=%d 残留=%s" % (base_rows, left, one("SELECT GROUP_CONCAT(id) FROM admin_tasks")))
check("  库里没有 probe- 开头的任务残留",
      count("SELECT COUNT(*) FROM admin_tasks WHERE title LIKE 'probe-%'") == 0)

print("\n=== %d PASS / %d FAIL ===" % (len(PASS), len(FAIL)))
if FAIL:
    print("失败项:")
    for f in FAIL:
        print("  - " + f)
sys.exit(1 if FAIL else 0)
