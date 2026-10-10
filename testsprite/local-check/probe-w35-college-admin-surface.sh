set -u
# 学院管理员端（role 5）全接口面核验 —— 直连 admin-service :8070。
# 目的：答"学院管理端修复完了吗"——不止 dashboard 一条，把 18 个接口整面跑一遍。
# 承重判据：现役正在使用的 collegeadmin1(user_id=57, college_id=1) 每条接口都 **body.code=200**
#   （修前是恒 403「无权限」，因为 getCollegeId 读不到档案行）。
# 只打 **GET 读端点**；写端点（POST /process-warning、DELETE /delete-warning）刻意不碰。
# 零写入自证：users / students? / academic_warning 行数打前打后一致。
BASE="http://localhost:8070"
SECRET="academic-jwt-secret-key-for-academic-warning-system-2026"
DB=academic_warning_system
CADM=57          # collegeadmin1，学院 1
CLASS=1         # 学院 1 的班
PROFILE=3       # 班 1 的学生档案 id
TEACHER=1       # 学院 1 的 teacher_profile.id（teachers 列表返回的 id，前端 row.id 传的就是它）
mint() { python - "$SECRET" "$1" "$2" "$3" <<'PY'
import hmac,hashlib,base64,json,time,sys
secret,uid,username,role=sys.argv[1].encode(),int(sys.argv[2]),sys.argv[3],int(sys.argv[4])
b64=lambda b: base64.urlsafe_b64encode(b).rstrip(b"=")
h=b64(json.dumps({"alg":"HS256","typ":"JWT"},separators=(",",":")).encode()); now=int(time.time())
p=b64(json.dumps({"userId":uid,"username":username,"role":role,"sub":username,"iat":now,"exp":now+7200},separators=(",",":")).encode())
s=b64(hmac.new(secret,h+b"."+p,hashlib.sha256).digest())
sys.stdout.write((h+b"."+p+b"."+s).decode())
PY
}
TOK=$(mint $CADM collegeadmin1 5)
cnt() { MSYS2_ARG_CONV_EXCL='*' mysql -uroot -proot -D "$DB" -N -e "$1" 2>/dev/null | tr -d '\r'; }
before=$(cnt "SELECT COUNT(*) FROM users;")$(cnt "SELECT COUNT(*) FROM student_profile;")$(cnt "SELECT COUNT(*) FROM academic_warning;")
get() { # $1=label $2=path(不含/api)  打印 HTTP + body.code + data 规模
  local out http code dat
  out=$(curl -s -m 12 -w '\n@@%{http_code}' "$BASE$2" -H "X-User-Id: $CADM" -H "X-Role: 5" -H "X-Username: collegeadmin1")
  http=$(printf '%s' "$out" | sed -n 's/.*@@\([0-9]*\)$/\1/p')
  body=$(printf '%s' "$out" | sed 's/@@[0-9]*$//')
  code=$(printf '%s' "$body" | sed -n 's/.*"code"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p')
  size=$(printf '%s' "$body" | wc -c)
  printf '  %-42s HTTP=%-4s code=%-4s %sB\n' "$1" "${http:-?}" "${code:-?}" "$size"
}
echo "=== 学院管理端全接口面：collegeadmin1(uid=$CADM, college 1) 直连 $BASE ==="
get "profile"              "/college-admin/profile/$CADM"
get "dashboard ★"          "/college-admin/dashboard/$CADM"
get "classes"              "/college-admin/classes/$CADM"
get "students"             "/college-admin/students/$CADM"
get "student-detail"       "/college-admin/student-detail/$CADM/$PROFILE"
get "teachers"             "/college-admin/teachers/$CADM"
get "teacher-detail"       "/college-admin/teacher-detail/$CADM/$TEACHER"
get "warnings"             "/college-admin/warnings/$CADM"
get "scores-stats"         "/college-admin/scores-stats/$CADM"
get "score-analysis"       "/college-admin/score-analysis/$CADM"
get "class-comparison"     "/college-admin/class-comparison/$CADM"
get "counselors"           "/college-admin/counselors/$CADM"
get "assistance"           "/college-admin/assistance/$CADM"
get "assistance-plans"     "/college-admin/assistance-plans/$CADM"
get "class-students"       "/college-admin/class-students/$CADM/$CLASS"
get "score-query"          "/college-admin/score-query/$CADM"
echo "--- 负对照（闸两个方向各一条）---"
get "★甲院管理员查乙院的班(classId=99)" "/college-admin/class-students/$CADM/99"
get "★非本人 uid(userId=56)打 dashboard"  "/college-admin/dashboard/56"
after=$(cnt "SELECT COUNT(*) FROM users;")$(cnt "SELECT COUNT(*) FROM student_profile;")$(cnt "SELECT COUNT(*) FROM academic_warning;")
echo "--- 零写入自证: users+student_profile+academic_warning = $before -> $after ---"
[ "$before" = "$after" ] && echo "OK 行数不变" || echo "!! 行数变了"
