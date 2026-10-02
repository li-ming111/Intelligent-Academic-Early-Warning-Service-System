# -*- coding: utf-8 -*-
"""运行时备份 / 恢复探针（2026-10-02，W1.10）。

判据（带负向对照）：
  1. `POST /admin/backup` 真在磁盘上生成 .sql（文件存在、非空、是 mysqldump 产物）
  2. 文件里**不含 USE 语句** —— 这是"恢复演练灌进临时库不会被拉回演示库"的前提，
     也是本设计最要紧的一条安全性质
  3. `GET /admin/backups/list` 列的就是磁盘上的真文件（新文件出现在列表里、大小对得上）
  4. `POST /admin/backup/{name}/restore` 在**临时库**上真灌一遍：表数 = 活库表数，
     且演练结束后临时库**已删除**（不留残骸）
  5. `DELETE /admin/backup/{name}` 真删文件；列表回到基线（**净变化 0**）
  6. 负向对照：非 .sql 名字 / 路径穿越 → 被拒；不带身份头 → 403

⚠️ 本探针**自己创建、自己删除**备份文件与临时库，不动任何既有演示数据。
⚠️ 中文一律经 API 回读判定，不经 CLI stdout（CP936 乱码）。
"""
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:%s" % (sys.argv[1] if len(sys.argv) > 1 else "8170")
ADMIN = {"X-User-Id": "1", "X-Role": "4", "X-Username": "admin"}
MYSQL = r"D:\JAVA\mysql\mysql-8.0.44-winx64\mysql-8.0.44-winx64\bin\mysql.exe"

failures = []


def check(name, ok, detail=""):
    print(("[PASS] " if ok else "[FAIL] ") + name + (("  " + detail) if detail else ""))
    if not ok:
        failures.append(name)


def call(method, path, headers=None):
    req = urllib.request.Request(BASE + path, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8"))
        except Exception:
            return e.code, {}


def backup_list():
    _, body = call("GET", "/admin/backups/list", ADMIN)
    data = body.get("data") or {}
    return data.get("items") or [], data


def mysql_scalar(sql):
    """只读数字/单值；中文不经 CLI（CP936 乱码）。"""
    out = subprocess.run([MYSQL, "-uroot", "-proot", "-N", "-B", "-e", sql],
                         capture_output=True, text=True, errors="replace")
    return out.stdout.strip()


live_tables = int(mysql_scalar(
    "SELECT COUNT(*) FROM information_schema.tables "
    "WHERE table_schema='academic_warning_system';"))
print("baseline: 活库表数=%d" % live_tables)

base_items, base_data = backup_list()
print("baseline: 备份数=%d dir=%s" % (len(base_items), base_data.get("dir")))
check("列表接口自称已实现", base_data.get("implemented") is True, str(base_data.get("implemented")))
check("列表回真实目录", bool(base_data.get("dir")), str(base_data.get("dir")))

# --- 1. 备份 ---
status, body = call("POST", "/admin/backup", ADMIN)
created = body.get("data") or {}
check("备份接口返回成功", status == 200 and body.get("code") == 200,
      "http=%s code=%s msg=%s" % (status, body.get("code"), body.get("message")))
name = created.get("name")
check("返回了 .sql 文件名", bool(name) and name.endswith(".sql"), str(name))

path = os.path.join(base_data.get("dir") or "", name or "")
check("文件真在磁盘上", os.path.isfile(path), path)
size = os.path.getsize(path) if os.path.isfile(path) else 0
check("文件非空", size > 0, "%d 字节" % size)

head = ""
use_stmt = None
if os.path.isfile(path):
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        text = f.read()
    head = text[:200]
    use_stmt = next((ln for ln in text.splitlines()
                     if ln.strip().upper().startswith("USE ")), None)

check("是 mysqldump 的产物", "MySQL dump" in head, head.splitlines()[0][:60] if head else "(空)")
check("⚠️ 文件里不含 USE 语句（否则恢复演练会被拉回演示库）",
      use_stmt is None, repr(use_stmt))

# --- 3. 列表认这个文件 ---
items, _ = backup_list()
found = next((i for i in items if i.get("id") == name), None)
check("列表里出现了这份新备份", found is not None, "n=%d" % len(items))
check("列表大小与磁盘一致", found is not None and found.get("sizeBytes") == size,
      "%s vs %s" % ((found or {}).get("sizeBytes"), size))
check("列表备份数 +1", len(items) == len(base_items) + 1,
      "%d -> %d" % (len(base_items), len(items)))

# --- 4. 恢复演练 ---
status, body = call("POST", "/admin/backup/%s/restore" % name, ADMIN)
result = body.get("data") or {}
check("恢复演练返回成功", status == 200 and body.get("code") == 200,
      "http=%s code=%s msg=%s" % (status, body.get("code"), body.get("message")))
check("演练校验为真", result.get("verified") is True, str(result.get("verified")))
check("临时库表数 = 活库表数", result.get("tableCount") == live_tables,
      "临时库=%s 活库=%d" % (result.get("tableCount"), live_tables))
scratch = result.get("scratchDb") or ""
check("临时库名带 _restore_ 后缀", "_restore_" in scratch, scratch)

left = mysql_scalar("SELECT COUNT(*) FROM information_schema.schemata "
                    "WHERE schema_name LIKE '%s%%';" % (scratch.replace("_", "\\_") if scratch else "zzz"))
check("演练后临时库已删除（不留残骸）", left == "0", "剩余同名库=%s" % left)

check("演练没有覆盖演示库（表数不变）",
      int(mysql_scalar("SELECT COUNT(*) FROM information_schema.tables "
                       "WHERE table_schema='academic_warning_system';")) == live_tables,
      "活库表数仍为 %d" % live_tables)

# --- 6. 负向对照 ---
# 本仓的 ApiResponse 约定是 **HTTP 200 + body.code=500**（前端拦截器只认 code），
# 所以判据是 code 而不是 HTTP 状态码 —— 写成 `status != 200` 会把"如实报错"判成失败。
status, body = call("POST", "/admin/backup/not-a-backup.txt/restore", ADMIN)
check("非 .sql 名字被拒（如实报错，不是假成功）",
      body.get("code") != 200 and "非法" in str(body.get("message")),
      "http=%s code=%s msg=%s" % (status, body.get("code"), body.get("message")))

# 被拒时必须是在**碰数据库之前**就被挡住：不能留下演练库残骸
check("被拒的请求没有建出临时库",
      mysql_scalar("SELECT COUNT(*) FROM information_schema.schemata "
                   "WHERE schema_name LIKE 'academic\\_warning\\_system\\_restore\\_%';") == "0")

status, body = call("POST", "/admin/backup/..%2F..%2Fevil.sql/restore", ADMIN)
check("路径穿越名被拒（不是 200 成功）", body.get("code") != 200,
      "http=%s code=%s msg=%s" % (status, body.get("code"), body.get("message")))
check("穿越名没有在备份目录外落文件",
      not os.path.exists(os.path.join(os.path.dirname(base_data.get("dir") or ""), "evil.sql")),
      os.path.dirname(base_data.get("dir") or ""))
check("穿越名没有在备份目录里落文件",
      not os.path.exists(os.path.join(base_data.get("dir") or "", "evil.sql")))

status, _ = call("POST", "/admin/backup", None)
check("不带身份头备份 → 403", status == 403, "http=%s" % status)

# --- 5. 删除（净变化 0）---
status, body = call("DELETE", "/admin/backup/%s" % name, ADMIN)
check("删除备份成功", status == 200 and body.get("code") == 200,
      "http=%s code=%s" % (status, body.get("code")))
check("文件已从磁盘消失", not os.path.isfile(path), path)

items, _ = backup_list()
check("列表回到基线（净变化 0）", len(items) == len(base_items),
      "%d -> %d" % (len(base_items), len(items)))

status, body = call("DELETE", "/admin/backup/%s" % name, ADMIN)
check("重复删除如实回失败（不是假成功）", body.get("code") != 200, str(body.get("message")))

print()
print("FAILURES: %d" % len(failures))
sys.exit(1 if failures else 0)
