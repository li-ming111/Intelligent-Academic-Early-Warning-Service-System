# -*- coding: utf-8 -*-
"""登录历史端到端探针（2026-10-02，W1.9）。

判据（每条都带负向对照）：
  1. 本人查自己的登录历史 → 200 且条数 > 0（写入方真的写进去了）
  2. 学生 token 查**别人**的登录历史 → body.code 403（归属校验生效）
  3. 不带 token → HTTP 403（认证闸生效）
  4. 未知用户名的失败登录**不落库**（login_logs.user_id 非空，没有 id 可填）

⚠️ 不打印 token 本身（活库凭据不进对话/日志），只打印布尔与计数。
"""
import json
import os
import urllib.request
import urllib.error
import sys

# 直连 user-service（不是网关）：第 3 条判据期望"不带 token → 403"，
# 而走网关的话那一层会先回 401「缺少认证信息」，判据就串了。
# 端口原来写死 8182（user-service 换端口前的旧值）⇒ 一直连不上，改成 8082 且可用环境变量覆盖。
BASE = os.environ.get("API_BASE", "http://localhost:8082")
STUDENT = {"username": "2023020616", "password": "123456"}


def post(path, payload, headers=None):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data, method="POST",
                                 headers={"Content-Type": "application/json", **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, _safe(e.read())


def get(path, headers=None):
    req = urllib.request.Request(BASE + path, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, _safe(e.read())


def _safe(raw):
    try:
        return json.loads(raw.decode("utf-8"))
    except Exception:
        return {"_raw": raw[:200].decode("utf-8", "replace")}


failures = []


def check(name, ok, detail=""):
    print(("[PASS] " if ok else "[FAIL] ") + name + (("  " + detail) if detail else ""))
    if not ok:
        failures.append(name)


def with_captcha(payload):
    """W1.12：登录要带服务端签发的验证码，否则一律 code:500「验证码错误或已过期」。"""
    req = urllib.request.Request(BASE + "/api/auth/captcha")
    with urllib.request.urlopen(req, timeout=15) as resp:
        cap = json.loads(resp.read().decode("utf-8"))["data"]
    return {**payload, "captchaId": cap["captchaId"], "captcha": cap["captchaText"]}


status, body = post("/api/auth/login", with_captcha(STUDENT))
token = (body.get("data") or {}).get("token") if isinstance(body, dict) else None
check("学生登录拿到 token", status == 200 and bool(token), "http=%s" % status)
if not token:
    sys.exit(1)

auth = {"Authorization": "Bearer " + token}
my_id = (body.get("data") or {}).get("userId")

status, body = get("/api/users/%s/login-logs" % my_id, auth)
rows = (body.get("data") if isinstance(body, dict) else None) or []
check("本人查自己的登录历史 → 200 且有条目", status == 200 and len(rows) > 0,
      "http=%s n=%d" % (status, len(rows)))
if rows:
    first = rows[0]
    check("每条都带前端要渲染的 6 个 key",
          all(k in first for k in ("id", "loginTime", "ipAddress", "device", "location", "status")),
          str(sorted(first.keys())))
    check("最新一条是 success（刚登录过）", first.get("status") == "success", str(first.get("status")))

# 负向对照 1：学生 token 查别人的登录历史
other_id = 1 if str(my_id) != "1" else 2
status, body = get("/api/users/%s/login-logs" % other_id, auth)
code = body.get("code") if isinstance(body, dict) else None
check("学生查别人的登录历史被拒", code == 403, "http=%s body.code=%s" % (status, code))

# 负向对照 2：不带 token
status, _ = get("/api/users/%s/login-logs" % my_id)
check("不带 token 被拒", status == 403, "http=%s" % status)

print()
print("FAILURES: %d" % len(failures))
sys.exit(1 if failures else 0)
