# -*- coding: utf-8 -*-
"""
W10 写侧归属闸 —— 端到端 A/B 取证。

        BEFORE = 现役栈（网关 8076 → teacher-service 8090，跑的是**旧 jar**）
        AFTER  = 刚编译的 target/classes 起的备用端口 8187 / 8188
        （12 个现役服务都 java -jar 占着 jar，所以不能 mvn package，
          只能从 target/classes 起 —— 见手册 §6.39）

同一个临时行、同一个请求，先看旧包放行、再看新包拦下。**这才叫"修好了"**，
光看单测绿不算 —— 单测没证明网关那条路上真的收得到 X-User-Id。

RED LINE 遵守情况：全程只碰**本探针自建**的临时行（id 从 9000001 起，remark 带 W10_PROBE），
跑完删干净。**没有**动任何真实演示数据。
"""
import json
import subprocess
import sys
import urllib.request

# Windows 控制台是 GBK：`->`/`*` 这类字符打不出来会抛 UnicodeEncodeError，
# 一旦抛在上半场，下面的 cleanup() 就**跑不到** —— 临时行留在库里。
# 本轮亲历：加了这个之前，探针崩在 C1，score_record 9000001 和 scores 262 都留在库里。
sys.stdout.reconfigure(errors='replace')

DB = 'academic_warning_system'
TEMP_ID = 9000001
MARK = 'W10_PROBE'
PROBE_STUDENT = 3      # student_profile.id（司明）
PROBE_COURSE = 9       # 深度学习 —— 司明名下没有这门课，保证同步进 scores 时是 INSERT 而非改真行
PROBE_SEMESTER = '9'   # 不存在的学期，同样是"只可能新建"
OWNER_TEACHER = 29     # 记录录入人
OTHER_TEACHER = 18     # 攻击者：另一个教师


def sql(q, db=DB):
    r = subprocess.run(['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4',
                        '-N', '-B', db, '-e', q],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    if r.returncode != 0:
        raise RuntimeError(r.stderr)
    return [l for l in r.stdout.strip().split('\n') if l != '']


def one(q):
    rows = sql(q)
    return rows[0] if rows else None


def req(method, url, headers=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header('Content-Type', 'application/json')
    for k, v in (headers or {}).items():
        r.add_header(k, v)
    try:
        with urllib.request.urlopen(r, timeout=20) as resp:
            raw = resp.read().decode('utf-8', 'replace')
            return resp.status, json.loads(raw) if raw.strip().startswith('{') else raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode('utf-8', 'replace')
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw


def code_of(resp):
    _, j = resp
    return j.get('code') if isinstance(j, dict) else None


def db_score():
    v = one(f"SELECT score FROM score_record WHERE id={TEMP_ID};")
    return v


def db_privacy(uid):
    return one(f"SELECT privacy_level FROM student_profile WHERE user_id={uid};")


results = []
def check(tag, ok, detail=''):
    results.append((tag, ok, detail))
    print(f"  [{'PASS' if ok else 'FAIL'}] {tag}  {detail}")


# ---------------------------------------------------------------- 建临时行
def setup():
    cleanup()
    sql(f"""INSERT INTO score_record
            (id, course_id, student_id, teacher_id, student_name, class_name, score,
             score_level, semester, year, remark, score_total, grade, grade_point, modified_by)
            VALUES ({TEMP_ID}, {PROBE_COURSE}, {PROBE_STUDENT}, {OWNER_TEACHER}, '探针临时行', '探针班',
                    80.00, 3, '{PROBE_SEMESTER}', 2024, '{MARK}', 80.00, 'B', 3.00, NULL);""")
    assert one(f"SELECT COUNT(*) FROM score_record WHERE id={TEMP_ID};") == '1', '临时行没建上'


def reset_row():
    sql(f"UPDATE score_record SET score=80.00, score_total=80.00, grade='B', grade_point=3.00, "
        f"remark='{MARK}' WHERE id={TEMP_ID};")


def cleanup():
    sql(f"DELETE FROM score_audit_log WHERE course_id={PROBE_COURSE} AND student_id={PROBE_STUDENT} "
        f"AND reason LIKE '%{MARK}%';")
    sql(f"DELETE FROM scores WHERE student_id={PROBE_STUDENT} AND course_id={PROBE_COURSE} "
        f"AND semester='{PROBE_SEMESTER}';")
    sql(f"DELETE FROM score_record WHERE id={TEMP_ID};")


print('=' * 70)
print('W10 写侧归属闸 · 端到端 A/B')
print(f'临时行 score_record.id={TEMP_ID}（录入人=教师{OWNER_TEACHER}，司明档案{PROBE_STUDENT}·课{PROBE_COURSE}）')
print('=' * 70)

setup()

# 兜底：哪怕中途抛异常也要把临时行删掉（红线是"不碰真实演示数据"，自建行同理不能留）。
import atexit
atexit.register(cleanup)

# ---------------------------------------------------------------- BEFORE
print('\n--- BEFORE：现役栈（8090 上的旧 jar）---')
reset_row()
for base, label in [('http://localhost:8090', '直连 8090'),
                    ('http://localhost:8076', '经网关 8076（要令牌）')]:
    if '8076' in base:
        continue  # 网关那条下面单独走令牌
    r = req('PUT', f'{base}/teachers/scores/{TEMP_ID}',
            {'X-User-Id': str(OTHER_TEACHER), 'X-Role': '2'}, {'score': 99, 'remark': MARK})
    before_after = db_score()
    check(f'{label} · 教师{OTHER_TEACHER} 改教师{OWNER_TEACHER}的成绩',
          code_of(r) == 200 and str(before_after).startswith('99'),
          f'body.code={code_of(r)} 库里 score={before_after}  <- 旧包**放行了**')

# ---------------------------------------------------------------- AFTER
print('\n--- AFTER：新包（8187，从 target/classes 起）---')

# C1 正向对照：本人改自己的行 —— 必须成功且真的落库
reset_row()
r = req('PUT', 'http://localhost:8187/teachers/scores/%d' % TEMP_ID,
        {'X-User-Id': str(OWNER_TEACHER), 'X-Role': '2'}, {'score': 85, 'remark': MARK})
check('C1 正向对照：录入人本人改分 -> 200 且库真变',
      code_of(r) == 200 and str(db_score()).startswith('85'),
      f'body.code={code_of(r)} 库里 score={db_score()}')

# C2 真判据：别人改 -> 403 且库**不动**
reset_row()
r = req('PUT', 'http://localhost:8187/teachers/scores/%d' % TEMP_ID,
        {'X-User-Id': str(OTHER_TEACHER), 'X-Role': '2'}, {'score': 99, 'remark': MARK})
check('C2 真判据：教师18 改教师29 的成绩 -> 403 且库不动',
      code_of(r) == 403 and str(db_score()).startswith('80'),
      f'body.code={code_of(r)} 库里 score={db_score()}')

# C3 管理员旁路
reset_row()
r = req('PUT', 'http://localhost:8187/teachers/scores/%d' % TEMP_ID,
        {'X-User-Id': '1', 'X-Role': '4'}, {'score': 88, 'remark': MARK})
check('C3 管理员(X-Role:4) 改任何行 -> 仍放行',
      code_of(r) == 200 and str(db_score()).startswith('88'),
      f'body.code={code_of(r)} 库里 score={db_score()}')

# C4 无身份头（坏令牌）。
# ⚠️ 这里有两种**形状不同**的拒绝，判据必须都收：
#   ① 整个头都没有      -> 挡在 controller **之前**（下游 Spring Security）-> HTTP 403 + **空体**
#   ② 头在但是空串/乱码 -> 进得了 controller，守卫 fail-closed           -> HTTP 200 + body.code 403
# 只断言一种形状就会把另一种读成"没拦住"。本轮亲历：第一版只判 code==403，
# C4a 报红而其实**拦住了**（库一分没动）—— 红的是判据，不是代码。
reset_row()
r = req('PUT', 'http://localhost:8187/teachers/scores/%d' % TEMP_ID, {'X-Role': '2'},
        {'score': 99, 'remark': MARK})
http, body = r
check('C4a 完全没有 X-User-Id -> 拒绝（HTTP 403 空体，挡在 controller 之前）',
      http == 403 and str(db_score()).startswith('80'),
      f'http={http} body={body!r} 库里 score={db_score()}')

reset_row()
r = req('PUT', 'http://localhost:8187/teachers/scores/%d' % TEMP_ID,
        {'X-User-Id': '', 'X-Role': '2'}, {'score': 99, 'remark': MARK})
check('C4b X-User-Id 是空串 -> 进得了 controller，守卫 fail-closed（200 + code 403）',
      code_of(r) == 403 and str(db_score()).startswith('80'),
      f'body.code={code_of(r)} 库里 score={db_score()}')

reset_row()
r = req('PUT', 'http://localhost:8187/teachers/scores/%d' % TEMP_ID,
        {'X-User-Id': 'abc', 'X-Role': '2'}, {'score': 99, 'remark': MARK})
check('C4c X-User-Id 不是数字 -> 同上（getUserIdFromGateway 解析失败返回 null）',
      code_of(r) == 403 and str(db_score()).startswith('80'),
      f'body.code={code_of(r)} 库里 score={db_score()}')

# C5 负向对照：行不存在 -> 不能被守卫读成"不是你的"
r = req('PUT', 'http://localhost:8187/teachers/scores/9999999',
        {'X-User-Id': str(OTHER_TEACHER), 'X-Role': '2'}, {'score': 99, 'remark': MARK})
check('C5 负向对照：不存在的 id -> 不得回 403（那是"查不到"，不是"不是你的"）',
      code_of(r) != 403,
      f'body.code={code_of(r)}（期望 400/404/500，只要不是 403）')

# ---------------------------------------------------------------- 学生侧
print('\n--- 学生侧（8188，新包）---')
SELF_U, OTHER_U = 27, 30   # 司明 / 邱琳紫

r = req('PUT', f'http://localhost:8188/students/settings/{SELF_U}',
        {'X-User-Id': str(SELF_U), 'X-Role': '1'}, {})
check('S1 正向对照：改**本人**设置 -> 非 403（路由活着、本人放行）',
      code_of(r) != 403, f'body.code={code_of(r)}')

priv_before = db_privacy(OTHER_U)
r = req('POST', f'http://localhost:8188/students/settings/{OTHER_U}/privacy-level?privacyLevel=0',
        {'X-User-Id': str(SELF_U), 'X-Role': '1'}, None)
priv_after = db_privacy(OTHER_U)
# 先断言、再兜底复原 —— 顺序不能反（先复原就永远看不出"真的被改了"）
check('S2 真判据：学生27 改学生30 的隐私档位 -> 403 且库不动',
      code_of(r) == 403 and priv_after == priv_before,
      f'body.code={code_of(r)} 对方 privacy_level {priv_before}→{priv_after}')
if priv_after != priv_before:
    sql(f"UPDATE student_profile SET privacy_level={priv_before} WHERE user_id={OTHER_U};")
    print(f'       ↳ 守卫失守，已把 student30 的 privacy_level 复原为 {priv_before}')

r = req('PUT', f'http://localhost:8188/students/settings/{OTHER_U}',
        {'X-User-Id': str(SELF_U), 'X-Role': '1'}, {})
check('S3 真判据：学生27 改学生30 的设置 -> 403', code_of(r) == 403, f'body.code={code_of(r)}')

r = req('PUT', f'http://localhost:8188/students/settings/{OTHER_U}',
        {'X-User-Id': '1', 'X-Role': '4'}, {})
check('S4 管理员旁路：改学生30 的设置 -> 非 403', code_of(r) != 403, f'body.code={code_of(r)}')

# 订阅族：* 两套 id 空间
r = req('POST', f'http://localhost:8188/students/subscription/{SELF_U}/update',
        {'X-User-Id': str(SELF_U), 'X-Role': '1'}, {})
check('S5 * 订阅族：拿自己的 users.id(27) 当档案 id 提交 -> 必须 403（证明比的是 profile.id）',
      code_of(r) == 403, f'body.code={code_of(r)}')

r = req('POST', f'http://localhost:8188/students/subscription/3/update',
        {'X-User-Id': str(SELF_U), 'X-Role': '1'}, {})
check('S6 * 订阅族：拿自己的**档案 id(3)** 提交 -> 非 403（空间选对了才放行）',
      code_of(r) != 403, f'body.code={code_of(r)}')

# ---------------------------------------------------------------- 清理
cleanup()
left = [one(f"SELECT COUNT(*) FROM score_record WHERE id={TEMP_ID};"),
        one(f"SELECT COUNT(*) FROM scores WHERE student_id={PROBE_STUDENT} AND course_id={PROBE_COURSE} AND semester='{PROBE_SEMESTER}';"),
        one(f"SELECT COUNT(*) FROM score_audit_log WHERE course_id={PROBE_COURSE} AND student_id={PROBE_STUDENT} AND reason LIKE '%{MARK}%';")]
check('Z 清理：临时行全部删除', all(x == '0' for x in left), f'剩余 score_record/scores/audit = {left}')

bad = [t for t, ok, _ in results if not ok]
print('\n' + '=' * 70)
print(f"合计 {len(results)} 项，红 {len(bad)} 项" + (f"：{bad}" if bad else ""))
print('=' * 70)
sys.exit(1 if bad else 0)
