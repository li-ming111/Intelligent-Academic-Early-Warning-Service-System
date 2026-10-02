#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
登录验证码的端到端探针（W1.12，2026-10-02）。

被验对象：`GET /api/auth/captcha` 与 `POST /api/auth/login` 的验证码校验。

改之前：验证码是**纯前端**的 —— PC `Login.vue` 与小程序 `pages/auth/login.vue`
各自用 `Math.random()` 生成 4 位串、提交前自己比一比；`AuthController.login` 只收
`username` / `password`。也就是说**直接 POST 一次登录接口就绕过去了**，
页面上那个验证码对脚本等于不存在。

跑法：
    PYTHONUTF8=1 /d/Python/python testsprite/local-check/probe-captcha.py [gateway_port] [user_port]

默认 gateway_port=8076、user_port=8082。要两个都起：
    gateway  8076（`--spring.config.additional-location=file:D:/abs-runtime/gw-extra.yml`，免 Nacos）
    user-service 8082

判据要点（踩过的坑都在这里）：
  · 本项目错误约定是 **HTTP 200 + body.code 500** ⇒ 断言看 `code` 与 `data.token`，
    **不能**只看 HTTP 状态；
  · **登录失败**（密码错/用户不存在）反而是 HTTP 200 + code 200 + `data.message`
    （`client.js` 对该路径特判），所以"没有 token"才是可靠判据，`code` 不是；
  · 白名单必须**两处**都加（网关 `WHITE_LIST` + user-service `permitAll`），
    只加一处会得到 403 —— 所以这里要**同时**从网关和直连各打一次，
    并配一个"不在白名单里的匿名路径 → 403"的负向对照，
    否则分不清"白名单生效了"和"这个服务本来就全放行"；
  · 一次性消费要**两个方向**都验：填错之后原码失效、填对之后原码也失效。

不打印口令与 token（只打印存在性/长度）。
"""

import json
import subprocess
import sys
import urllib.error
import urllib.request

GW_PORT = sys.argv[1] if len(sys.argv) > 1 else "8076"
USER_PORT = sys.argv[2] if len(sys.argv) > 2 else "8082"
GW = "http://localhost:%s" % GW_PORT
DIRECT = "http://localhost:%s" % USER_PORT

USERNAME = "2023020616"
PASSWORD = "123456"

MYSQL = r"D:\JAVA\mysql\mysql-8.0.44-winx64\mysql-8.0.44-winx64\bin\mysql.exe"
ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

PASS = []
FAIL = []
INFO = []


def check(name, ok, detail=""):
    (PASS if ok else FAIL).append(name)
    print("%s %s%s" % ("PASS" if ok else "FAIL", name, ("  <- " + str(detail)) if detail else ""))


def info(name, value):
    INFO.append("%s = %s" % (name, value))
    print("INFO %s = %s" % (name, value))


def req(method, url, body=None):
    """回 (http_status, parsed_json_or_None, raw_text)。"""
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header("Content-Type", "application/json")
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
    out = subprocess.run(
        [MYSQL, "-uroot", "-proot", "--default-character-set=utf8mb4",
         "-D", "academic_warning_system", "-N", "-B", "-e", query],
        capture_output=True, text=True, encoding="utf-8", errors="replace")
    return [l for l in (out.stdout or "").splitlines() if l.strip()]


def count(query):
    rows = sql(query)
    return int(rows[0]) if rows else 0


def issue(base=GW):
    """签发一个验证码，回 (captchaId, captchaText) 或 (None, None)。"""
    st, js, raw = req("GET", base + "/api/auth/captcha")
    if js is None or js.get("code") != 200 or not isinstance(js.get("data"), dict):
        return None, None
    d = js["data"]
    return d.get("captchaId"), d.get("captchaText")


def login(username, password, captcha_id=None, captcha=None, base=GW):
    """按前端真实形状发登录。只把给了的字段放进 body。"""
    body = {"username": username, "password": password}
    if captcha_id is not None:
        body["captchaId"] = captcha_id
    if captcha is not None:
        body["captcha"] = captcha
    return req("POST", base + "/api/auth/login", body)


def logged_in(js):
    """登录成功 = code 200 且 data 里有 token（失败时 data 只有 message）。"""
    return (js is not None and js.get("code") == 200
            and isinstance(js.get("data"), dict) and bool(js["data"].get("token")))


def why(js):
    """从响应里抽一句人话，用于 FAIL 明细（不含 token）。"""
    if js is None:
        return "<no json>"
    d = js.get("data")
    if isinstance(d, dict) and d.get("message"):
        return "code=%s data.message=%s" % (js.get("code"), d.get("message"))
    return "code=%s message=%s" % (js.get("code"), js.get("message"))


print("=== 登录验证码探针 (W1.12)  GW=%s  DIRECT=%s ===" % (GW, DIRECT))

base_logins = count("SELECT COUNT(*) FROM login_logs")

# ==================== 1. 签发端点：两处白名单都真的加了 ====================

st, js, raw = req("GET", GW + "/api/auth/captcha")
check("★ 经网关**匿名** GET /api/auth/captcha → 200（网关 WHITE_LIST 里有它）",
      st == 200 and js is not None and js.get("code") == 200, "HTTP=%s %s" % (st, raw[:160]))
gw_id, gw_code = issue()

st_d, js_d, raw_d = req("GET", DIRECT + "/api/auth/captcha")
check("★ 直连 user-service **匿名** GET /api/auth/captcha → 200（下游 permitAll 里有它）",
      st_d == 200 and js_d is not None and js_d.get("code") == 200,
      "HTTP=%s %s" % (st_d, raw_d[:160]))

# 负向对照：同一个服务、同样不带任何头，打一条**不在白名单里**的路径。
# 这条如果也 200，说明上面那条 200 根本不是白名单的功劳（服务整体放开了）。
st_n, js_n, raw_n = req("GET", DIRECT + "/api/auth/info/1")
check("负向对照：直连匿名打**不在白名单**的 /api/auth/info/1 → 403（证明白名单是逐条列的）",
      st_n == 403, "HTTP=%s body=%r" % (st_n, raw_n[:80]))

check("  签发的码是 4 位", bool(gw_code) and len(gw_code) == 4, repr(gw_code))
check("  码只用不易混的字符集（不含 0/O/1/I）",
      bool(gw_code) and all(c in ALPHABET for c in gw_code), repr(gw_code))
check("  captchaId 非空", bool(gw_id) and len(gw_id) >= 16, repr(gw_id))

id2, _ = issue()
check("  两次签发拿到不同的 id", bool(gw_id) and bool(id2) and gw_id != id2)

# ==================== 2. 登录必须带码（改之前这里能过） ====================

# ★ 这条就是"改之前的样子"：只要用户名口令，直接 POST。
st, js, raw = login(USERNAME, PASSWORD)
check("★ 负向对照：只发 username/password（改前的绕过形状）→ 拒绝，拿不到 token",
      not logged_in(js), "HTTP=%s %s" % (st, why(js)))

fresh_id, fresh_code = issue()
st, js, raw = login(USERNAME, PASSWORD, captcha_id=fresh_id)
check("  只带 captchaId 不带 captcha → 拒绝", not logged_in(js), why(js))

id_a, _ = issue()
st, js, raw = login(USERNAME, PASSWORD, captcha="ABCD")
check("  只带 captcha 不带 captchaId → 拒绝", not logged_in(js), why(js))

st, js, raw = login(USERNAME, PASSWORD,
                    captcha_id="00000000-0000-0000-0000-000000000000", captcha="ABCD")
check("  编造的 captchaId → 拒绝", not logged_in(js), why(js))

# ==================== 3. 码填错 / 一次性消费 ====================

id_b, code_b = issue()
wrong = "".join("A" if c != "A" else "B" for c in code_b)
st, js, raw = login(USERNAME, PASSWORD, captcha_id=id_b, captcha=wrong)
check("  码填错 → 拒绝", not logged_in(js), why(js))

# 填错之后，**同一个 id** 用对的码也不该再通过（失败也消费）
st, js, raw = login(USERNAME, PASSWORD, captcha_id=id_b, captcha=code_b)
check("★ 填错之后同一个 captchaId 再用**对的码** → 仍然拒绝（失败也消费，防爆破）",
      not logged_in(js), why(js))

# ==================== 4. 正常登录 ====================

id_c, code_c = issue()
st, js, raw = login(USERNAME, PASSWORD, captcha_id=id_c, captcha=code_c)
check("★ 新码填对 + 口令正确 → 登录成功（有 token）", logged_in(js), why(js))
if logged_in(js):
    d = js["data"]
    info("登录成功", "userId=%s role=%s token长度=%s" % (d.get("userId"), d.get("role"), len(d.get("token") or "")))

# ==================== 5. 成功之后原码也不能再用（防重放） ====================

st, js, raw = login(USERNAME, PASSWORD, captcha_id=id_c, captcha=code_c)
check("★ 刚登录成功过的那个码再发一次 → 拒绝（成功也消费，防重放）",
      not logged_in(js), why(js))

# ==================== 6. 码对但口令错：验证码不是绕过口令的通道 ====================

id_e, code_e = issue()
st, js, raw = login(USERNAME, "not-the-password", captcha_id=id_e, captcha=code_e)
check("  码对但口令错 → 拒绝（验证码不是绕过口令的通道）", not logged_in(js), why(js))

# ==================== 7. 大小写/空格：用户手输的常态 ====================

id_f, code_f = issue()
st, js, raw = login(USERNAME, PASSWORD, captcha_id="  " + id_f + "  ", captcha=" " + code_f.lower() + " ")
check("  码小写 + 首尾空格 → 仍能登录（服务端 trim + 忽略大小写）", logged_in(js), why(js))

# ==================== 8. 顺带：登录日志真的记了（W1.9 的链路没被这次改动弄坏） ====================

now_logins = count("SELECT COUNT(*) FROM login_logs")
info("login_logs 行数", "%d -> %d" % (base_logins, now_logins))
check("  成功登录会写 login_logs（本次至少 +1）", now_logins > base_logins,
      "base=%d now=%d" % (base_logins, now_logins))

print("\n=== %d PASS / %d FAIL ===" % (len(PASS), len(FAIL)))
if FAIL:
    print("失败项:")
    for f in FAIL:
        print("  - " + f)
sys.exit(1 if FAIL else 0)
