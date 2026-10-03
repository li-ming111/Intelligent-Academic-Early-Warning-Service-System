#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
跨端数据一致性核对（2026-10-03，第四十二轮 W8.3）。

**问的问题不是"每个接口通不通"，而是"同一件事实在不同角色端看到的是不是同一个值"。**
通不通只证明路由没断；一致性才证明"数据在端与端之间传递是对的"。

选一个学生（司明）与他的班级（计科2306班）作为**同一批事实**，让四个角色各报一遍：

  事实 A. 司明有一条预警：id=395, warning_level=2(中级), fail_count=3,
          描述里点名「高等数学 56.5分」
  事实 B. 司明 高等数学 学期5 的成绩 = 56.5（等级 D）
  事实 C. 计科2306班 在读学生 = 24 人

真值一律以**活库直查**为准（scores 是权威成绩源）；四个端各报的值都要跟真值相等。
只要有一端报了别的数，就是"同一份数据在不同端不一致"。

用法: python probe-cross-end-consistency.py
前置: 网关 8076 + 全部服务在跑（活库 academic_warning_system）
退出码 0=全过 / 1=有失败
"""
import json
import subprocess
import sys
import urllib.request

# Windows 控制台默认 GBK，打不出 ✓/✗ 与部分中文 ⇒ 强制 UTF-8
try:
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
except Exception:
    pass

BASE = 'http://localhost:8076/api'
PASS = '123456'

ROLES = {
    'student':       ('2023020616',  27),   # /students/**  参数 = users.id
    'counselor':     ('1000000001',  53),   # /counselors/** 参数 = users.id（实测，不是 counselor_profiles.id）
    'teacher':       ('10001',       29),   # /teachers/**  参数 = users.id
    'college_admin': ('collegeadmin1', 57),
}

# 目标学生 / 班级
STU_UID = 27          # 司明 users.id
CLASS_ID = 1          # 计科2306班
WARN_ID = 395
COURSE_MATH = 1       # 高等数学
SEM = '5'

ok_count = 0
bad = []


def say(s=''):
    print(s)


def check(name, cond, detail=''):
    global ok_count
    if cond:
        ok_count += 1
        say('  ✓ %s' % name)
    else:
        bad.append(name)
        say('  ✗ %s' % name)
    if detail:
        say('      %s' % detail)


def mysql(sql):
    """活库直查（真值来源）。"""
    r = subprocess.run(
        ['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4', '-N', '-B',
         'academic_warning_system', '-e', sql],
        capture_output=True)
    return r.stdout.decode('utf-8', 'replace').strip()


def req(path, token=None, method='GET', body=None):
    url = BASE + path
    data = None
    headers = {}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if body is not None:
        data = json.dumps(body).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=20) as resp:
            return json.loads(resp.read().decode('utf-8', 'replace'))
    except Exception as e:
        return {'__error': str(e)}


def login(username):
    cap = req('/auth/captcha')
    d = cap.get('data') or {}
    body = {'username': username, 'password': PASS,
            'captchaId': d.get('captchaId'), 'captcha': d.get('captchaText')}
    r = req('/auth/login', method='POST', body=body)
    return ((r.get('data') or {}).get('token'))


def data_of(resp):
    """兼容 body 形状：{code,data} / 直接数组 / {error}。"""
    if not isinstance(resp, dict):
        return resp
    if '__error' in resp:
        return None
    if 'data' in resp:
        return resp['data']
    return resp


def eqnum(a, b, tol=1e-6):
    """数值比较（56.5 vs 56.50 是同一个数；'56.5' vs 56.50 也是）。

    2026-10-03 W8.3：本探针第一版拿 str 直接比，把 `56.5` 与真值 `56.50`
    读成两处红 —— 那是**探针自己的**假红（同 [[abs-build-and-harness-false-negatives]]）。
    """
    try:
        return abs(float(a) - float(b)) <= tol
    except (TypeError, ValueError):
        return False


# 教师端把 warning_level 渲染成中文（TeacherController.java:722），
# 而学生/辅导员端回的是数字 —— 同一个值两种形状。判定要按语义，不按字面。
LVL_TEXT = {'轻度': 1, '中度': 2, '严重': 3, '轻度预警': 1, '中度预警': 2, '严重预警': 3}


def level_num(v):
    """把 '中度' / 2 / '2' 统一成数字等级；认不出返回 None。"""
    if v is None:
        return None
    s = str(v).strip()
    if s in LVL_TEXT:
        return LVL_TEXT[s]
    try:
        return int(float(s))
    except ValueError:
        return None


def count_of(d):
    """班级人数：可能是数组、也可能是 {totalStudents:N} 对象（学生端就是这样）。"""
    if isinstance(d, list):
        return len(d)
    if isinstance(d, dict):
        for k in ('totalStudents', 'count', 'total'):
            if isinstance(d.get(k), int):
                return d[k]
        for k in ('records', 'list', 'students'):
            if isinstance(d.get(k), list):
                return len(d[k])
    return None


# ---------------------------------------------------------------- 起手
say('=== 跨端一致性核对（司明 / 计科2306班）===\n')

say('[0] 真值（活库直查，scores 为权威源）')
truth_math = mysql(
    "SELECT score_total FROM scores WHERE student_id=3 AND course_id=%d AND semester=%s;"
    % (COURSE_MATH, SEM))
truth_warn = mysql(
    "SELECT CONCAT(warning_level,'|',fail_count) FROM academic_warning WHERE id=%d;" % WARN_ID)
truth_class = mysql("SELECT COUNT(*) FROM student_profile WHERE class_id=%d;" % CLASS_ID)
say('    高等数学 学期5 总分 = %s' % truth_math)
say('    预警 %d: level|failCount = %s' % (WARN_ID, truth_warn))
say('    计科2306班 学生数 = %s\n' % truth_class)

say('[1] 四个角色登录')
tokens = {}
for role, (u, uid) in ROLES.items():
    t = login(u)
    tokens[role] = t
    check('%s 登录成功 (%s, users.id=%d)' % (role, u, uid), bool(t))
if not all(tokens.values()):
    say('\n有角色登不进来，后面的核对没意义，先停。')
    sys.exit(1)
say()

# ---------------------------------------------------------------- A. 预警
say('[A] 同一件事：司明的预警 %d（level=2 中级 / failCount=3）' % WARN_ID)
want_level, want_fail = (truth_warn.split('|') + ['', ''])[:2]

def find_warn(items, desc_key='description'):
    for it in items or []:
        if not isinstance(it, dict):
            continue
        if str(it.get('id')) == str(WARN_ID):
            return it
    return None

def field(it, *names):
    for n in names:
        if isinstance(it, dict) and n in it and it[n] is not None:
            return it[n]
    return None

# 学生端
d = data_of(req('/students/warnings/%d' % STU_UID, tokens['student']))
w = find_warn(d if isinstance(d, list) else [])
check('A1 学生端能查到预警 %d' % WARN_ID, w is not None,
      '返回条数=%s' % (len(d) if isinstance(d, list) else d))
if w:
    check('A2 学生端 level=%s failCount=%s（真值 %s/%s）'
          % (field(w, 'warningLevel'), field(w, 'failCount'), want_level, want_fail),
          str(field(w, 'warningLevel')) == want_level and str(field(w, 'failCount')) == want_fail)

# 辅导员端
d = data_of(req('/counselors/warnings?counselor_id=%d' % ROLES['counselor'][1], tokens['counselor']))
w = find_warn(d if isinstance(d, list) else [])
check('A3 辅导员端能查到预警 %d' % WARN_ID, w is not None,
      '返回条数=%s' % (len(d) if isinstance(d, list) else d))
if w:
    check('A4 辅导员端 level/failCount 与学生端同值',
          str(field(w, 'warningLevel')) == want_level and str(field(w, 'failCount')) == want_fail,
          'level=%s failCount=%s' % (field(w, 'warningLevel'), field(w, 'failCount')))

# 教师端
d = data_of(req('/teachers/warnings/%d' % ROLES['teacher'][1], tokens['teacher']))
w = find_warn(d if isinstance(d, list) else [])
check('A5 教师端能查到预警 %d' % WARN_ID, w is not None,
      '返回条数=%s' % (len(d) if isinstance(d, list) else d))
if w:
    # 形状：教师端 warningLevel 是中文（TeacherController:722），且**没有 failCount 字段** ——
    # 这是教师 UI 的契约（TeacherWarnings.vue 按 '严重'/'中度'/'轻度' 字面过滤），
    # 不是数据错误。所以这里比"语义等级"，而不是比字面。
    check('A6 教师端等级语义 = 真值 level=%s（回的是「%s」）' % (want_level, field(w, 'warningLevel')),
          level_num(field(w, 'warningLevel')) == int(want_level),
          'warningLevel=%s failCount=%s（教师端不提供该字段）'
          % (field(w, 'warningLevel'), field(w, 'failCount')))
    # 教师端不给结构化 failCount ⇒ 它**必须**在 description 文本里承载同样的门数，
    # 否则这条信息就在教师端整个丢了。这是可证伪的正向断言（不是"没字段就放过"）。
    desc = str(field(w, 'description') or '')
    check('A7 教师端在描述文本里承载挂科门数 = %s' % want_fail,
          ('累计%s门' % want_fail) in desc,
          'description=%s' % desc[:90])
    # 同一响应里 title（库里存的「中级预警」）与 warningLevel（映射出的「中度」）用词不同 ——
    # 记一笔供人判断，不作失败：两个词都指 level=2，且 UI 只渲染 warningLevel。
    if field(w, 'title') and str(field(w, 'title')) not in str(field(w, 'warningLevel')):
        say('      · 注：同一条里 title=「%s」而 warningLevel=「%s」（同一等级两种措辞）'
            % (field(w, 'title'), field(w, 'warningLevel')))
say()

# ---------------------------------------------------------------- B. 成绩
say('[B] 同一件事：司明 高等数学 学期%s = %s' % (SEM, truth_math))

def find_score(items):
    for it in items or []:
        if not isinstance(it, dict):
            continue
        if str(field(it, 'courseId', 'course_id')) == str(COURSE_MATH) and \
           str(field(it, 'semester')) == SEM:
            return it
    return None

d = data_of(req('/students/scores/%d' % STU_UID, tokens['student']))
s = find_score(d if isinstance(d, list) else [])
check('B1 学生端查到 高等数学 学期%s' % SEM, s is not None,
      '返回条数=%s' % (len(d) if isinstance(d, list) else d))
if s:
    v = field(s, 'scoreTotal', 'score_total')
    check('B2 学生端总分 = %s（真值 %s）' % (v, truth_math),
          eqnum(v, truth_math),
          '实体=%s' % json.dumps(s, ensure_ascii=False)[:160])

# 辅导员端：整班成绩
d = data_of(req('/counselors/scores/class/%d?counselor_id=%d' % (CLASS_ID, ROLES['counselor'][1]),
                tokens['counselor']))
flat = []
if isinstance(d, list):
    for row in d:
        if isinstance(row, dict) and isinstance(row.get('scores'), list):
            flat.extend(row['scores'])
            if str(field(row, 'studentId', 'student_id')) == '3':
                for sc in row['scores']:
                    sc.setdefault('_sid', 3)
        else:
            flat.append(row)
s = find_score(flat)
check('B3 辅导员端整班成绩里能定位到同一条', s is not None,
      '返回类型=%s 条数=%s' % (type(d).__name__, len(flat)))
if s:
    v = field(s, 'scoreTotal', 'score_total', 'total')
    check('B4 辅导员端该条总分 = %s（真值 %s）' % (v, truth_math),
          eqnum(v, truth_math),
          '实体=%s' % json.dumps(s, ensure_ascii=False)[:160])
say()

# ---------------------------------------------------------------- C. 班级人数
say('[C] 同一件事：计科2306班 = %s 人' % truth_class)

# 学生端回的是**对象**（{classId,totalStudents,rank,…}），不是名单数组 —— 见 W8.3 记录。
d = data_of(req('/students/class-members/%d' % STU_UID, tokens['student']))
n = count_of(d)
check('C1 学生端看到本班人数 = %s（真值 %s）' % (n, truth_class),
      n is not None and eqnum(n, truth_class),
      '返回形状=%s 实体=%s' % (type(d).__name__, json.dumps(d, ensure_ascii=False)[:120]))

d = data_of(req('/counselors/classes/%d/students' % CLASS_ID, tokens['counselor']))
n2 = count_of(d)
check('C2 辅导员端看到本班人数 = %s（真值 %s）' % (n2, truth_class),
      n2 is not None and eqnum(n2, truth_class))

d = data_of(req('/teachers/classes/%d/students' % CLASS_ID, tokens['teacher']))
n3 = count_of(d)
check('C3 教师端看到本班人数 = %s（真值 %s）' % (n3, truth_class),
      n3 is not None and eqnum(n3, truth_class))

say()
say('[D] 负向对照：证明判定函数会拒绝错值（否则上面全绿不作数）')
check('D1 eqnum 接受 56.5≈"56.50"，拒绝 56.5 vs 61',
      eqnum(56.5, '56.50') and not eqnum(56.5, 61))
check('D2 level_num 把「中度」读成 2，且拒绝「严重」当 2',
      level_num('中度') == 2 and level_num('严重') != 2)
check('D3 count_of 认得对象里的 totalStudents，且不把 24 认成 23',
      count_of({'totalStudents': 24}) == 24 and count_of({'totalStudents': 24}) != 23)
check('D4 描述断言会失败：真值为 3 时，含「累计2门」的文本必须判否',
      not (('累计%s门' % 3) in '累计2门课程不及格'))

say()
say('================ 汇总 ================')
say('通过 %d 项，失败 %d 项' % (ok_count, len(bad)))
for b in bad:
    say('  ✗ ' + b)
sys.exit(1 if bad else 0)
