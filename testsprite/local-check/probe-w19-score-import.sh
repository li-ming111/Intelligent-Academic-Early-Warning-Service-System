#!/usr/bin/env bash
# W19 -- 两个「导入成绩」按钮的端到端判据 (2026-10-04)
#
# 病根: 前端发 multipart，后端收 @RequestBody。两条入口都从未成功过一次。
#   PC AdminDataExport.vue  -> /admin/import/scores   (旧契约: @RequestBody List<Map>)
#   PC TeacherScores.vue    -> /teachers/scores/import (旧契约: @RequestBody Map)
#
# 判据(成对，缺一条都不算过):
#   new(修后备用端口) 发**前端真实形状**(multipart/FormData) -> 必须 200 且真的落库
#   old(现役旧 jar)   发**同一个形状**                        -> 必须 415(证据, 不是判据)
#   归属/格式反例必须在**读文件之前**被拒，且一行都不写。
#
# 硬约束: 只写**探针自建**的行(学号 9999000001)，收尾全部删除。
#   🔴 绝不拿真实演示数据试删除。
#
# 用法: bash probe-w19-score-import.sh
#
# ⚠️ 起备用实例时 classpath 必须**前置** academic-common/target/classes:
#    dependency:build-classpath 解析到的是 ~/.m2 里的**旧 jar**，缺新加的类型 => 启动即挂。
set -u

DB="${DB:-academic_warning_system}"
NEW_ADMIN="${NEW_ADMIN:-8170}"
NEW_TEACHER="${NEW_TEACHER:-8195}"
OLD_ADMIN="${OLD_ADMIN:-8070}"
OLD_TEACHER="${OLD_TEACHER:-8090}"

PROBE_NO="9999000001"
PROBE_NAME="探针同学W19"
DIR="testsprite/local-check"

ADMIN='-H "X-User-Id: 1" -H "X-Role: 4"'
T29='-H "X-User-Id: 29" -H "X-Role: 2"'
T18='-H "X-User-Id: 18" -H "X-Role: 2"'

say() { printf '%s\n' "$*"; }
sql() { MSYS2_ARG_CONV_EXCL='*' mysql -uroot -proot -D "$DB" --default-character-set=utf8mb4 -N -e "$1" 2>/dev/null | tr -d '\r'; }

# 打一发 multipart/FormData，原样打印 HTTP 码 + body（不做字段解析 —— 判据要的是原始证据）
form() { # $1=port $2=path $3=file $4=extra-F-args $5=headers
  local out http body
  out=$(eval MSYS2_ARG_CONV_EXCL='*' curl -s -m 30 -w "'\n@@%{http_code}'" -X POST \
        "'http://localhost:$1$2'" -F "'file=@$DIR/$3'" $4 $5 2>&1)
  http=$(printf '%s' "$out" | sed -n 's/.*@@\([0-9]*\)$/\1/p')
  body=$(printf '%s' "$out" | sed 's/@@[0-9]*$//' | tr -d '\r' | tr '\n' ' ')
  printf 'HTTP=%-4s %s\n' "${http:-?}" "$(printf '%s' "$body" | head -c 300)"
}

counts() { sql "SELECT CONCAT('scores=',(SELECT COUNT(*) FROM scores),' score_record=',(SELECT COUNT(*) FROM score_record),' probe_rows=',(SELECT COUNT(*) FROM score_record WHERE student_id=(SELECT id FROM student_profile WHERE student_id='$PROBE_NO')));"; }

say "=== W19: 成绩导入端到端 -- new(admin $NEW_ADMIN / teacher $NEW_TEACHER) vs old(admin $OLD_ADMIN / teacher $OLD_TEACHER) ==="
say ""
say "探针学生: $PROBE_NO ($PROBE_NAME), profile_id=$(sql "SELECT id FROM student_profile WHERE student_id='$PROBE_NO'")"
BEFORE=$(counts); say "BEFORE: $BEFORE"
say ""

say "A. 管理员端 /admin/import/scores -- 前端真实形状(multipart + 模板列)"
printf '    new(%s) ' "$NEW_ADMIN"; form "$NEW_ADMIN" /admin/import/scores _w19_admin_scores.xlsx "" "$ADMIN"
printf '    old(%s) ' "$OLD_ADMIN"; form "$OLD_ADMIN" /admin/import/scores _w19_admin_scores.xlsx "" "$ADMIN"
say "    -> 落库: $(sql "SELECT CONCAT('score_record=',COUNT(*)) FROM score_record WHERE student_id=(SELECT id FROM student_profile WHERE student_id='$PROBE_NO')")   $(sql "SELECT CONCAT('scores=',COUNT(*)) FROM scores WHERE student_id=(SELECT id FROM student_profile WHERE student_id='$PROBE_NO')")"
say ""

say "B. 教师端 /teachers/scores/import -- 前端真实形状(FormData: file+courseId+className)"
printf '    new(%s) ' "$NEW_TEACHER"; form "$NEW_TEACHER" /teachers/scores/import _w19_teacher_scores.xlsx '-F "courseId=1" -F "className=W19ProbeClass" -F "semester=5"' "$T29"
printf '    old(%s) ' "$OLD_TEACHER"; form "$OLD_TEACHER" /teachers/scores/import _w19_teacher_scores.xlsx '-F "courseId=1" -F "className=W19ProbeClass" -F "semester=5"' "$T29"
say ""

say "C. 反例 -- 全部必须被拒且**一行都不写**"
C1=$(counts)
printf '    cross 教师18 导课程1(于凤海的): '; form "$NEW_TEACHER" /teachers/scores/import _w19_teacher_scores.xlsx '-F "courseId=1"' "$T18"
printf '    表头没有学号:                   '; form "$NEW_ADMIN" /admin/import/scores _w19_bad_header.xlsx "" "$ADMIN"
printf '    学号不存在:                     '; form "$NEW_ADMIN" /admin/import/scores _w19_ghost_student.xlsx "" "$ADMIN"
printf '    学期非数字(2025-2026-1):        '; form "$NEW_TEACHER" /teachers/scores/import _w19_teacher_scores.xlsx '-F "courseId=1" -F "semester=2025-2026-1"' "$T29"
printf '    无身份头:                       '; form "$NEW_TEACHER" /teachers/scores/import _w19_teacher_scores.xlsx '-F "courseId=1"' ""
sleep 1
C2=$(counts)
if [ "$C1" = "$C2" ]; then say "    [OK] 反例区间零写入: $C1"; else say "    [!!] 反例居然写了库! before=$C1 after=$C2"; fi
say ""

say "D. 新增: 教师端模板端点 /teachers/scores/template"
MSYS2_ARG_CONV_EXCL='*' curl -s -m 20 -o "$DIR/_w19_tpl.xlsx" -w '    HTTP=%{http_code} type=%{content_type} bytes=%{size_download}\n' \
  "http://localhost:$NEW_TEACHER/teachers/scores/template" -H "X-User-Id: 29" -H "X-Role: 2"
python - "$DIR/_w19_tpl.xlsx" <<'PY'
import sys, openpyxl
try:
    s = openpyxl.load_workbook(sys.argv[1]).active
    print("    表头:", [c.value for c in s[1]])
except Exception as e:
    print("    [!!] 不是合法 xlsx:", e)
PY
say ""

say "E. 新增: 导出成绩单带「学号」列"
MSYS2_ARG_CONV_EXCL='*' curl -s -m 20 -o "$DIR/_w19_export.xlsx" -w '    HTTP=%{http_code} bytes=%{size_download}\n' \
  "http://localhost:$NEW_TEACHER/teachers/export/scores?course_id=1" -H "X-User-Id: 29" -H "X-Role: 2"
python - "$DIR/_w19_export.xlsx" <<'PY'
import sys, openpyxl
try:
    s = openpyxl.load_workbook(sys.argv[1]).active
    print("    表头:", [c.value for c in s[1]])
    print("    第一行数据:", [c.value for c in s[4]][:3])
except Exception as e:
    print("    [!!] 不是合法 xlsx:", e)
PY
say ""

say "F. 探针自建行的落库明细"
sql "SELECT CONCAT('  score_record id=',id,' course=',course_id,' score=',score,' grade=',IFNULL(grade,'NULL'),' gpa=',IFNULL(grade_point,'NULL'),' level=',IFNULL(score_level,'NULL'),' teacher=',teacher_id,' semester=',IFNULL(semester,'NULL')) FROM score_record WHERE student_id=(SELECT id FROM student_profile WHERE student_id='$PROBE_NO');"
sql "SELECT CONCAT('  scores       id=',id,' course=',course_id,' total=',score_total,' grade=',grade,' gpa=',grade_point,' semester=',semester) FROM scores WHERE student_id=(SELECT id FROM student_profile WHERE student_id='$PROBE_NO');"
say ""
say "=== 结束。清理见 cleanup-w19-probe.sql（只删探针自建行） ==="
