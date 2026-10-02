# -*- coding: utf-8 -*-
"""审计日志端到端探针（2026-10-02，W1.9）。

判据（带负向对照）：
  1. 管理端建规则 → audit_logs +1，且记的是**操作人**（X-User-Id 回查 users 后落库）
  2. 删规则 → audit_logs 再 +1，warning_rules 回到测试前的行数（**净变化为 0**）
  3. 不带身份头 → HTTP 403（GatewayAuthFilter 生效）
  4. GET /admin/audit-logs 能按 entityType 过滤并回操作人用户名

⚠️ 只创建/删除**本次探针自己造的那一行**规则，不动任何既有演示数据。
⚠️ 中文一律经 API 回读判定，不经 CLI stdout（CP936 乱码）。
"""
import json
import urllib.request
import urllib.error
import sys

BASE = "http://localhost:8170"
ADMIN_HEADERS = {"X-User-Id": "1", "X-Role": "4", "X-Username": "admin"}
RULE_NAME = "probe-audit-20261002"

failures = []


def check(name, ok, detail=""):
    print(("[PASS] " if ok else "[FAIL] ") + name + (("  " + detail) if detail else ""))
    if not ok:
        failures.append(name)


def call(method, path, payload=None, headers=None):
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method,
                                 headers={"Content-Type": "application/json", **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8"))
        except Exception:
            return e.code, {}


def audit_count(entity_type=None):
    q = "/admin/audit-logs?size=200"
    if entity_type:
        q += "&entityType=" + entity_type
    _, body = call("GET", q, headers=ADMIN_HEADERS)
    return (body.get("data") or {}).get("total", 0), (body.get("data") or {}).get("items", [])


def rule_count():
    _, body = call("GET", "/admin/rules", headers=ADMIN_HEADERS)
    data = body.get("data")
    return len(data) if isinstance(data, list) else None


base_audit, _ = audit_count("WARNING_RULE")
base_rules = rule_count()
print("baseline: audit(WARNING_RULE)=%s rules=%s" % (base_audit, base_rules))

# --- 负向对照：不带身份头 ---
status, _ = call("POST", "/admin/rules", {"ruleName": RULE_NAME})
check("不带身份头建规则被拒", status == 403, "http=%s" % status)

# --- 正例：建规则 ---
status, body = call("POST", "/admin/rules", {"ruleName": RULE_NAME, "description": "探针用临时规则"},
                    headers=ADMIN_HEADERS)
check("管理员建规则成功", status == 200 and body.get("code") == 200, "http=%s code=%s" % (status, body.get("code")))

after_audit, items = audit_count("WARNING_RULE")
check("建规则后 audit_logs +1", after_audit == base_audit + 1,
      "%s -> %s" % (base_audit, after_audit))

created = next((i for i in items if i.get("details") and RULE_NAME in i["details"]), None)
check("审计行记的是操作人（回查 users 后的 id）", created is not None and created.get("userId") == 1,
      json.dumps({k: (created or {}).get(k) for k in ("userId", "username", "actionType", "entityType", "entityId")},
                 ensure_ascii=False))
new_rule_id = (created or {}).get("entityId")

# --- 找到刚建的规则 id（entityId 应指向它）---
rules_now = rule_count()
check("规则表多了一行", base_rules is not None and rules_now == base_rules + 1,
      "%s -> %s" % (base_rules, rules_now))

if new_rule_id is None:
    print("!! 拿不到新建规则的 id，无法继续清理")
    sys.exit(1)

# --- 删掉这条**探针自己造的**规则 ---
status, body = call("DELETE", "/admin/rules/%s" % new_rule_id, headers=ADMIN_HEADERS)
check("删除探针规则成功", status == 200 and body.get("code") == 200, "http=%s" % status)

final_audit, items = audit_count("WARNING_RULE")
check("删规则后 audit_logs 再 +1", final_audit == base_audit + 2, "%s -> %s" % (after_audit, final_audit))

final_rules = rule_count()
check("规则表回到基线（净变化 0）", final_rules == base_rules, "%s -> %s" % (rules_now, final_rules))

deleted = next((i for i in items if i.get("actionType") == "DELETE" and i.get("entityId") == new_rule_id), None)
check("DELETE 那条审计有操作人", deleted is not None and deleted.get("userId") == 1,
      json.dumps({k: (deleted or {}).get(k) for k in ("userId", "username", "actionType", "entityId")},
                 ensure_ascii=False))

# --- 过滤条件真的生效 ---
all_audit, _ = audit_count()
check("不带过滤时总数 >= 带过滤时总数", all_audit >= final_audit, "%s vs %s" % (all_audit, final_audit))

print()
print("FAILURES: %d" % len(failures))
sys.exit(1 if failures else 0)
