# -*- coding: utf-8 -*-
"""「更新请求体里一个可更新字段都没有」→ 400（W8，2026-10-03）。

判据（每条都带负向对照）：

  A 空对象 `{}`                    → HTTP 200 + body code 400
    （改前：admin-service 回 HTTP 500 + code 500；counselor-service 回
      HTTP 200 + code 500，且 message 里带着 `### SQL: UPDATE student_profile    WHERE id=?`）
  B 完全没有 body                  → HTTP 400 + body code 400（改前 HTTP 500 + code 500，
    且 message 泄漏完整 Java 方法签名）
  C JSON 语法坏                    → HTTP 400 + body code 400（改前 HTTP 500）
  D 只有 @TableField(exist=false) 的字段有值（如 studentCount）→ 200 + code 400
    （这些字段根本不进 SQL，所以 SET 子句还是空的）
  E 负向对照①：带一个真列 + **不存在的 id** → 200 + code 404
    —— 证明守卫**没有**把正常请求拦下（它一路走到了"id 不存在"那一关），且全程不写库
  F 负向对照②：带一个真列 + **真实临时行** → 200 + code 200，且**库里读回来确实变了**
    —— 证明守卫既不误拦、也不影响真写

⚠️ 只写探针自己建的临时行（跑完即删、AUTO_INCREMENT 复原），**绝不碰真实演示数据**。
⚠️ 只读 + 只写临时行；`messages`/`colleges` 的既有行一行都不动。
"""
import json
import subprocess
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:%s" % (sys.argv[1] if len(sys.argv) > 1 else "8170")
AUTH = {"X-User-Id": "1", "X-Role": "4", "Content-Type": "application/json"}
JSON_HEADERS = AUTH
DB = "academic_warning_system"
PROBE_NAME = "W8_PROBE_COLLEGE"
PROBE_CODE = "W8P"

failures = []


def check(name, ok, detail=""):
    print(("[PASS] " if ok else "[FAIL] ") + name + (("  " + detail) if detail else ""))
    if not ok:
        failures.append(name)


def call(method, path, body=None, headers=None, raw=False):
    data = None
    if body is not None:
        data = body if raw else json.dumps(body).encode("utf-8")
        if isinstance(data, str):
            data = data.encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data, method=method,
                                 headers=headers if headers is not None else JSON_HEADERS)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


def body_code(raw):
    try:
        return json.loads(raw.decode("utf-8")).get("code")
    except Exception:
        return None


def body_message(raw):
    try:
        return json.loads(raw.decode("utf-8")).get("message") or ""
    except Exception:
        return raw.decode("utf-8", "replace")


def sql(query):
    out = subprocess.run(["mysql", "--default-character-set=utf8mb4", "-uroot", "-proot",
                          DB, "-N", "-B", "-e", query],
                         capture_output=True)
    if out.returncode != 0:
        raise RuntimeError(out.stderr.decode("utf-8", "replace"))
    return out.stdout.decode("utf-8").strip()


# ── 准备：探针自建的临时学院行 ───────────────────────────────────────────
# 先清一遍：上一轮探针如果中途崩了会留下同名行（name 上有唯一键），不清掉这次插不进去。
sql("DELETE FROM colleges WHERE code='%s' OR name='%s';" % (PROBE_CODE, PROBE_NAME))
sql("INSERT INTO colleges (name, code, description, status) VALUES ('%s','%s','W8 probe tmp',1);"
    % (PROBE_NAME, PROBE_CODE))
tid = sql("SELECT id FROM colleges WHERE code='%s';" % PROBE_CODE)
if not tid:
    print("临时行没建出来，探针中止")
    sys.exit(2)
tid = int(tid)
print("临时学院行 id=%d（跑完即删）" % tid)

try:
    # A 空对象
    st, raw = call("PUT", "/admin/colleges/%d" % tid, {})
    check("A 空对象 {} → HTTP 200 + code 400",
          st == 200 and body_code(raw) == 400,
          "HTTP=%s code=%s" % (st, body_code(raw)))

    # B 完全没有 body
    st, raw = call("PUT", "/admin/colleges/%d" % tid, None)
    check("B 无 body → HTTP 400 + code 400",
          st == 400 and body_code(raw) == 400,
          "HTTP=%s code=%s" % (st, body_code(raw)))
    check("B2 无 body 的 message 不泄漏类名/方法签名",
          "com.academic" not in body_message(raw) and "public " not in body_message(raw),
          body_message(raw)[:80])

    # C JSON 坏
    st, raw = call("PUT", "/admin/colleges/%d" % tid, b'{bad json', raw=True)
    check("C JSON 语法坏 → HTTP 400 + code 400",
          st == 400 and body_code(raw) == 400,
          "HTTP=%s code=%s" % (st, body_code(raw)))

    # D 只有 exist=false 的字段
    st, raw = call("PUT", "/admin/colleges/%d" % tid, {"studentCount": 9, "teacherCount": 9})
    check("D 只有 exist=false 字段 → 200 + code 400",
          st == 200 and body_code(raw) == 400,
          "HTTP=%s code=%s" % (st, body_code(raw)))

    # E 负向对照①：真列 + 不存在的 id（不写库）
    st, raw = call("PUT", "/admin/colleges/999999", {"name": "X"})
    check("E 真列 + 不存在 id → 200 + code 404（守卫没误拦，也没写库）",
          st == 200 and body_code(raw) == 404,
          "HTTP=%s code=%s" % (st, body_code(raw)))

    # F 负向对照②：真列 + 真实临时行 → 真写进去
    st, raw = call("PUT", "/admin/colleges/%d" % tid, {"name": PROBE_NAME + "_R"})
    ok_call = st == 200 and body_code(raw) == 200
    actual = sql("SELECT name FROM colleges WHERE id=%d;" % tid)
    check("F 真列 + 真实临时行 → 200 + code 200 且库里确实改了",
          ok_call and actual == PROBE_NAME + "_R",
          "HTTP=%s code=%s 库里=%s" % (st, body_code(raw), actual))

    # H 用**完全相同**的值再 PUT 一次。
    #   这条钉的是 W7/W8 共同依赖的一个前提：MySQL 通过 Connector/J 默认返回**匹配行数**
    #   （CLIENT_FOUND_ROWS），不是"改动行数"。否则"0 行 = 这行不存在 ⇒ 404"就会在
    #   "用户点了保存但什么都没改"这种正常操作上误报 404。
    st, raw = call("PUT", "/admin/colleges/%d" % tid, {"name": PROBE_NAME + "_R"})
    check("H 值完全没变的更新 → 仍 200 + code 200（0 行才算不存在）",
          st == 200 and body_code(raw) == 200,
          "HTTP=%s code=%s" % (st, body_code(raw)))

    # 顺带：临时行还在（上面那些 400 一次都没写到它）
    still = sql("SELECT COUNT(*) FROM colleges WHERE id=%d;" % tid)
    check("G 前面所有 400 都没动过这行（仍在）", still == "1", "count=%s" % still)

finally:
    sql("DELETE FROM colleges WHERE code='%s';" % PROBE_CODE)
    # 复原自增值：写成 1 时 MySQL 会自己夹到 max(id)+1（= 探针插入前的那个值），
    # 所以这一句等价于"把自增计数器收回去"，不会把 id 重新发成 1。
    sql("ALTER TABLE colleges AUTO_INCREMENT = 1;")
    left = sql("SELECT COUNT(*) FROM colleges WHERE code='%s';" % PROBE_CODE)
    print("清理完成：残留临时行 %s，colleges 现有 %s 行"
          % (left, sql("SELECT COUNT(*) FROM colleges;")))
    if left != "0":
        failures.append("临时行没删干净")

print()
print("FAILURES: %d" % len(failures))
sys.exit(1 if failures else 0)
