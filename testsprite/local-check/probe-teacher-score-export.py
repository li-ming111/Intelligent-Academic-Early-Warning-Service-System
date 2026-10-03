# -*- coding: utf-8 -*-
"""教师端成绩单导出探针（2026-10-02，W1.8）。

判据（带负向对照）：
  1. `/teachers/export/scores?course_id=1` → 200 + xlsx 内容类型 + 真是 xlsx
  2. 工作簿行数/姓名集合 = `/teachers/scores?course_id=1`（同一份数据，两个端点对得上）
  3. 已删的 `/teachers/download/scores` **与从没存在过的同形路径表现一致**
     （⚠️ 2026-10-03 W7 起：两段未知路径回 **404 + code 404**，与 `/nope/no` 同形；
      一段的未知路径会撞上 `@GetMapping("/{id}")` 的 Long 解析失败，回 **400 + 参数类型错误**
      （W7 之前这两条分别是 403 空体 / 500））
  4. 不带身份头 → 403

⚠️ 只读，不写任何表。
"""
import io
import json
import sys
import urllib.request
import urllib.error

import openpyxl

BASE = "http://localhost:%s" % (sys.argv[1] if len(sys.argv) > 1 else "8190")
COURSE = 1
TEACHER = 29
XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

failures = []


def check(name, ok, detail=""):
    print(("[PASS] " if ok else "[FAIL] ") + name + (("  " + detail) if detail else ""))
    if not ok:
        failures.append(name)


def get(path, headers=None):
    req = urllib.request.Request(BASE + path, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, r.headers.get("Content-Type", ""), r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get("Content-Type", ""), e.read()


AUTH = {"X-User-Id": str(TEACHER), "X-Role": "2"}

status, _, raw = get("/teachers/scores?course_id=%d" % COURSE, AUTH)
rows = json.loads(raw.decode("utf-8")).get("data") or []
print("baseline: /teachers/scores?course_id=%d -> http=%s 行数=%d" % (COURSE, status, len(rows)))
check("基线接口取到成绩", status == 200 and len(rows) > 0, "http=%s n=%d" % (status, len(rows)))

status, ctype, raw = get("/teachers/export/scores?course_id=%d" % COURSE, AUTH)
check("导出 HTTP 200", status == 200, "http=%s" % status)
check("内容类型是 xlsx", ctype.startswith(XLSX_TYPE), ctype)
check("响应体是 zip 容器（xlsx）而非 JSON",
      len(raw) > 2 and raw[0] == 0x50 and raw[1] == 0x4B,
      "前两字节=%s 长度=%d" % (list(raw[:2]), len(raw)))

try:
    ws = openpyxl.load_workbook(io.BytesIO(raw))["成绩单"]
    check("openpyxl 能打开工作簿", True)
    header = [c.value for c in ws[1]]
    check("表头正确", header[:3] == ["姓名", "班级", "平时成绩"], json.dumps(header, ensure_ascii=False))
    data = [r for r in ws.iter_rows(min_row=2, values_only=True) if any(v not in (None, "") for v in r)]
    check("数据行数 = 接口侧行数", len(data) == len(rows), "工作簿=%d 接口=%d" % (len(data), len(rows)))
    names = {r[0] for r in data}
    api_names = {r.get("studentName") for r in rows if r.get("studentName")}
    check("姓名集合一致", names == api_names,
          "差集=%s" % json.dumps(sorted(names ^ api_names), ensure_ascii=False))
except Exception as e:
    check("openpyxl 能打开工作簿", False, str(e))

# 已删端点：与"从没存在过的同形路径"同形（而不是 200 带数据）
#
# ⚠️ 2026-10-03 W7 之后本条断言的判据改过：以前这里硬编码 `gone_body == b""`，
#    而 W7 给下游服务补上了 `NoResourceFoundException → 404`，未知路径不再回
#    `403 空体` 而是 `404 + {"code":404,...}` —— 两条路径**依然同形**，
#    是这条断言把"空体"当成了同形的判据（探针假红，同 §6.37 那一族）。
#    现在按**真正要断言的东西**判：两条状态码相同、且都带 body（如果带的话）里
#    `code` 与 HTTP 状态一致 —— 这样 403 空体 / 404+code404 两种形状都算过，
#    而一旦退化成 `200 + 数据` 就会红。
def _body_code(body):
    try:
        return json.loads(body.decode("utf-8")).get("code")
    except Exception:
        return None


gone_status, gone_type, gone_body = get("/teachers/download/scores?course_id=%d" % COURSE, AUTH)
never_status, never_type, never_body = get("/teachers/xyz/scores?course_id=%d" % COURSE, AUTH)
check("已删的 /teachers/download/scores 与从未存在的同形路径同形",
      gone_status == never_status
      and gone_status not in (200,)
      and (gone_body == b"" or _body_code(gone_body) == gone_status),
      "已删=%s(code=%s) 从未存在=%s(code=%s)"
      % (gone_status, _body_code(gone_body), never_status, _body_code(never_body)))

check("不带身份头 → 403", get("/teachers/export/scores?course_id=%d" % COURSE)[0] == 403)

print()
print("FAILURES: %d" % len(failures))
sys.exit(1 if failures else 0)
