#!/usr/bin/env bash
# W18 —— 教师端两条「跨学生」越权读的收口 (2026-10-04)
#
# 病根: 两条读端点的 id 全由客户端给, 且**一条校验都没有**:
#   1) GET /teachers/students/{studentId}/credit-prediction   studentId = student_profile.id
#      —— 控制器里还写着注释把它辩护成"按设计就是教师看本班学生, 属合法的跨用户读, 不能拦";
#         但没有任何代码验证过这个 studentId 真在本人班上。
#   2) GET /teachers/scores/student/analyze?student_id&course_id
#      —— course_id 也是客户端给, 换个数字就读别的教师课程里任意学生的成绩/名次。
#
# 判据(成对, 缺一条都不算过):
#   cross : 教师 18(刘教授) 拿**于凤海班的**学生 / **于凤海的**课程 -> 必须被拒
#   own   : 教师 29(于凤海) 拿**自己班的**同一批 id                  -> 必须放行(200)
#   只测 cross 会把"接口整个坏了"读成"修好了"; 只测 own 会把"没装闸"读成"没误伤"。
#
# 硬约束: **纯只读**(两个端点方法体内零 insert/update/delete, 见收尾自证)。
#   刻意**不**做会改状态的调用。
#
# 用法: bash probe-w18-teacher-read-idor.sh
#   NEW_PORT=8195 (以当前源码从 target/classes 起的备用端口)
#   OLD_PORT=8090 (现役旧 jar —— 修前的天然对照组, 会如实报出泄漏)
#
# ⚠️ 起备用实例时 classpath 必须**前置** academic-common/target/classes:
#    dependency:build-classpath 解析到的是 ~/.m2 里的**旧 jar**,
#    缺 com.academic.common.entity.CourseRecommendation => 启动即 UnsatisfiedDependency。
set -u

NEW_PORT="${NEW_PORT:-8195}"
OLD_PORT="${OLD_PORT:-8090}"
DB="${DB:-academic_warning_system}"

# 活库真值: 司明 student_profile.id=3, 落在 class 1(计科2306班, 班主任 users.id=29 于凤海)。
# 教师 18(刘教授) 的班是 31/32(0 人), 课程 6; 课程 1(高等数学) 是 29 的。两套不相交。
STU=3

say() { printf '%s\n' "$*"; }

# 打一发, 只打印 "HTTP 码 + body 里的 code/message + 头 150 字节"。
hit() { # $1=port $2=path $3=headers
  local out http body
  out=$(eval curl -s -m 10 -w "'\n@@%{http_code}'" "'http://localhost:$1$2'" $3 2>&1)
  http=$(printf '%s' "$out" | sed -n 's/.*@@\([0-9]*\)$/\1/p')
  body=$(printf '%s' "$out" | sed 's/@@[0-9]*$//')
  printf 'HTTP=%-4s code=%-5s msg=%s\n' "${http:-?}" \
    "$(printf '%s' "$body" | sed -n 's/.*"code"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p')" \
    "$(printf '%s' "$body" | sed -n 's/.*"message"[[:space:]]*:"\([^"]*\)".*/\1/p')"
  printf '         body=%s\n' "$(printf '%s' "$body" | head -c 150)"
}

pair() { # $1=说明 $2=path $3=headers
  say "  [$1]"
  printf '    new(%s) ' "$NEW_PORT"; hit "$NEW_PORT" "$2" "$3"
  printf '    old(%s) ' "$OLD_PORT"; hit "$OLD_PORT" "$2" "$3"
}

ME29='-H "X-User-Id: 29" -H "X-Role: 2"'
ME18='-H "X-User-Id: 18" -H "X-Role: 2"'
ADMIN='-H "X-User-Id: 1" -H "X-Role: 4"'

# ---- 零写入哨兵: 任何请求之前先读一次行数 ----
before=$(MSYS2_ARG_CONV_EXCL='*' mysql -uroot -proot -D "$DB" -N -e \
  "SELECT CONCAT((SELECT COUNT(*) FROM scores),'/',(SELECT COUNT(*) FROM score_record),'/',(SELECT COUNT(*) FROM notifications));" 2>/dev/null | tr -d '\r')

say "=== W18: 教师端跨学生读端点 -- new=$NEW_PORT(修后) vs old=$OLD_PORT(修前) ==="
say ""

say "A. /students/${STU}/credit-prediction  (student_profile.id=${STU} 司明, 在 29 的班)"
pair "cross: 18 读于凤海班的司明   [期望 new 403 / old 200+真数据]" '/teachers/students/'"$STU"'/credit-prediction' "$ME18"
pair "own  : 29 读自己班的司明     [期望 200]"                    '/teachers/students/'"$STU"'/credit-prediction' "$ME29"
say ""

say "B. /scores/student/analyze?student_id=${STU}&course_id=1  (课程 1 高等数学 = 29 的课)"
pair "cross: 18 分析于凤海的课 1   [期望 new 403 / old 200+真数据]" "/teachers/scores/student/analyze?student_id=${STU}&course_id=1" "$ME18"
pair "own  : 29 分析自己的课 1     [期望 200]"                     "/teachers/scores/student/analyze?student_id=${STU}&course_id=1" "$ME29"
say ""

say "C. 失败形状与旁路"
printf '    new 无身份头(应 403 空体): '; hit "$NEW_PORT" '/teachers/students/'"$STU"'/credit-prediction' ''
printf '    new 管理员(应 200):        '; hit "$NEW_PORT" '/teachers/students/'"$STU"'/credit-prediction' "$ADMIN"
say ""

# ---- 零写入自证 ----
after=$(MSYS2_ARG_CONV_EXCL='*' mysql -uroot -proot -D "$DB" -N -e \
  "SELECT CONCAT((SELECT COUNT(*) FROM scores),'/',(SELECT COUNT(*) FROM score_record),'/',(SELECT COUNT(*) FROM notifications));" 2>/dev/null | tr -d '\r')
say "=== 零写入自证 ==="
if [ "$before" = "$after" ]; then
  say "  [OK] 行数不变 before=$before after=$after (scores/score_record/notifications)"
else
  say "  [!!] 行数变了! before=$before after=$after -- 立刻查库"
fi
say ""
say "=== 结束。两条 cross 只要 old 回 200 且带学生姓名/分数, 就是真实泄漏(证据, 非判据) ==="
