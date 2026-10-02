#!/bin/bash
# W1.13 实测：学院管理员的「身份绑定」与「资源归属」两道闸。
#
# 修之前（2026-10-02 之前）：
#   · 所有接口的第一参数是 users.id，学院归属由它推出来 ⇒ 把 uid 换成别人的就换成别人的学院；
#   · /class-students 只查 class_id = 传入值，不看这个班属不属于本院
#     ⇒ 电子工程学院管理员（uid=56）请求 /class-students/56/1 拿到软件学院计科2306班 24 人姓名学号；
#   · /process-warning 与 /delete-warning 连操作人都不收 ⇒ 任何已认证用户拿任意 id 能改能删；
#   · 网关对 role 1/2/3 打到 /api/college-admin/** 一路 return true ⇒ 学生 token 也能进这个控制器。
#
# 判据一律是「HTTP 状态 + body.code + 数据行数」三件套，且每个"拒绝"都配一个"放行"对照 ——
# 只证明"挡住了"不够，还得证明"该通的还通"。
#
# 用法: bash probe-college-admin-scope.sh
# 退出码 0=全部符合预期
BASE=http://localhost:8076/api
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m  %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); printf '  \033[31mFAIL\033[0m  %s\n' "$1"; }
chk()  { if [ "$2" = "$3" ]; then ok "$1  ($2)"; else bad "$1  期望=$3 实际=$2"; fi; }

# 登录：W1.12 起必须带服务端签发的验证码
login() {
  local cap id code
  cap=$(curl -s "$BASE/auth/captcha" --max-time 10)
  id=$(printf '%s' "$cap" | sed -n 's/.*"captchaId":"\([^"]*\)".*/\1/p')
  code=$(printf '%s' "$cap" | sed -n 's/.*"captchaText":"\([^"]*\)".*/\1/p')
  curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
    -d "{\"username\":\"$1\",\"password\":\"123456\",\"captchaId\":\"$id\",\"captcha\":\"$code\"}" --max-time 10 \
    | sed -n 's/.*"token":"\([^"]*\)".*/\1/p'
}

# 发一次请求，回 "HTTP_CODE BODY_CODE BODY"
call() { # $1=method $2=token $3=path [$4=body]
  local m="$1" t="$2" p="$3" b="$4" out
  if [ -n "$b" ]; then
    out=$(curl -s -w '\n%{http_code}' -X "$m" -H "Authorization: Bearer $t" \
          -H 'Content-Type: application/json' -d "$b" "$BASE$p" --max-time 15)
  else
    out=$(curl -s -w '\n%{http_code}' -X "$m" -H "Authorization: Bearer $t" "$BASE$p" --max-time 15)
  fi
  local http body bc
  http=$(printf '%s' "$out" | tail -1)
  body=$(printf '%s' "$out" | sed '$d')
  bc=$(printf '%s' "$body" | sed -n 's/.*"code":\([0-9]*\).*/\1/p')
  printf '%s %s %s' "$http" "${bc:-none}" "$body"
}
# 从 call 的返回里取第 n 段
seg() { printf '%s' "$1" | cut -d' ' -f"$2"; }
body_of() { printf '%s' "$1" | cut -d' ' -f3-; }
# JSON 数组里元素的个数（按 "studentId" 出现次数数，比数 "id" 准）
count_students() { printf '%s' "$1" | grep -o '"studentId"' | wc -l | tr -d ' '; }

echo "==================== 准备：取三个身份 ===================="
T_A=$(login collegeadmin1)   # users.id=57 → 学院 1（软件学院）
T_B=$(login collegeadmin2)   # users.id=56 → 学院 2（电子工程学院）
T_S=$(login 2023020616)      # users.id=27 → 学生
for v in "$T_A" "$T_B" "$T_S"; do
  if [ -z "$v" ]; then echo "登录失败，拿不到 token"; exit 1; fi
done
echo "三个 token 都拿到了（collegeadmin1 / collegeadmin2 / 学生）"

# 临时预警行：**自己造的**，不用演示数据当靶子。
# 造完立刻记 id，跑完删掉并核对表行数回到原值。
export MSYS2_ARG_CONV_EXCL='*'
BEFORE=$(mysql -uroot -proot -D academic_warning_system -N -B -e "SELECT COUNT(*) FROM academic_warning;" 2>/dev/null)
TMP_ID=$(mysql -uroot -proot -D academic_warning_system -N -B -e \
  "INSERT INTO academic_warning (student_id, warning_level, status, description, created_at, updated_at)
   VALUES (3, 2, 0, 'W1.13-TEMP-PROBE', NOW(), NOW()); SELECT LAST_INSERT_ID();" 2>/dev/null)
echo "临时预警 id=$TMP_ID（student_profile.id=3 → class 1 → 学院 1）；原表行数=$BEFORE"
echo

echo "==================== ① 花名册：班级归属 ===================="
R=$(call GET "$T_B" "/college-admin/class-students/56/1")   # 乙院管理员读甲院的班
chk "乙院管理员读甲院的班(1) → body.code" "$(seg "$R" 2)" "403"
# 两种拒绝的签名不一样，别混：
#   下游控制器拒绝 = HTTP 200 + body.code 403（本工程 ApiResponse 约定）
#   网关拒绝       = HTTP 403 + body{code:403}（没有 data/timestamp/success 三个字段）
# 判据写成"HTTP 是 200 而 body.code 是 403"才算认对了是哪一层挡的。
chk "  └ 下游拒绝的签名 = HTTP 200 + body.code 403" "$(seg "$R" 1)" "200"
chk "  └ 一条学生都不返回" "$(count_students "$(body_of "$R")")" "0"

R=$(call GET "$T_A" "/college-admin/class-students/57/1")   # 甲院管理员读甲院的班
chk "甲院管理员读本院的班(1) → HTTP" "$(seg "$R" 1)" "200"
chk "  └ body.code" "$(seg "$R" 2)" "200"
chk "  └ 拿到 24 个学生（与库一致）" "$(count_students "$(body_of "$R")")" "24"

R=$(call GET "$T_A" "/college-admin/class-students/57/34")  # 甲院管理员读乙院的班
chk "甲院管理员读乙院的班(34) → body.code" "$(seg "$R" 2)" "403"

echo
echo "==================== ② 身份绑定：uid 换成别人的 ===================="
R=$(call GET "$T_A" "/college-admin/class-students/56/1")   # 甲院 token，URL 写乙院的 uid
chk "甲院 token + URL uid=56（乙院） → body.code" "$(seg "$R" 2)" "403"
R=$(call GET "$T_A" "/college-admin/dashboard/56")          # 只读接口同一个口子
chk "甲院 token + /dashboard/56 → body.code" "$(seg "$R" 2)" "403"
R=$(call GET "$T_A" "/college-admin/classes/56")
chk "甲院 token + /classes/56 → body.code" "$(seg "$R" 2)" "403"
R=$(call GET "$T_A" "/college-admin/profile/56")            # 档案原先连权限判断都没有
chk "甲院 token + /profile/56（别人的手机号邮箱） → body.code" "$(seg "$R" 2)" "403"
R=$(call GET "$T_A" "/college-admin/dashboard/57")          # 放行对照
chk "甲院 token + /dashboard/57（本人） → body.code" "$(seg "$R" 2)" "200"

echo
echo "==================== ③ 写操作：处理 / 删除预警 ===================="
R=$(call POST "$T_A" "/college-admin/process-warning" "{\"id\":$TMP_ID,\"status\":1}")
chk "处理预警不传 userId → body.code" "$(seg "$R" 2)" "400"
R=$(call POST "$T_B" "/college-admin/process-warning" "{\"userId\":56,\"id\":$TMP_ID,\"status\":1}")
chk "乙院管理员处理甲院的预警 → body.code" "$(seg "$R" 2)" "403"
ST=$(mysql -uroot -proot -D academic_warning_system -N -B -e "SELECT status FROM academic_warning WHERE id=$TMP_ID;" 2>/dev/null)
chk "  └ 库里 status 没被动过" "$ST" "0"

R=$(call DELETE "$T_B" "/college-admin/delete-warning/56/$TMP_ID")
chk "乙院管理员删甲院的预警 → body.code" "$(seg "$R" 2)" "403"
ST=$(mysql -uroot -proot -D academic_warning_system -N -B -e "SELECT COUNT(*) FROM academic_warning WHERE id=$TMP_ID;" 2>/dev/null)
chk "  └ 行还在（真删行接口，这条最要紧）" "$ST" "1"

R=$(call POST "$T_A" "/college-admin/process-warning" "{\"userId\":57,\"id\":$TMP_ID,\"status\":1}")
chk "甲院管理员处理本院的预警 → body.code" "$(seg "$R" 2)" "200"
ST=$(mysql -uroot -proot -D academic_warning_system -N -B -e "SELECT status FROM academic_warning WHERE id=$TMP_ID;" 2>/dev/null)
chk "  └ 库里 status 真的写成了 1" "$ST" "1"

R=$(call DELETE "$T_A" "/college-admin/delete-warning/57/$TMP_ID")
chk "甲院管理员删本院的预警 → body.code" "$(seg "$R" 2)" "200"
ST=$(mysql -uroot -proot -D academic_warning_system -N -B -e "SELECT COUNT(*) FROM academic_warning WHERE id=$TMP_ID;" 2>/dev/null)
chk "  └ 行真的没了" "$ST" "0"

echo
echo "==================== ④ 网关前缀闸：学生/教师/辅导员不该进来 ===================="
R=$(call GET "$T_S" "/college-admin/dashboard/27")
chk "学生 token 打 /college-admin/dashboard/27 → HTTP" "$(seg "$R" 1)" "403"
# 网关那层的签名是 HTTP 403 + body.code 403（与上面下游那层不同）
chk "  └ 网关拒绝的签名 = HTTP 403 + body.code 403" "$(seg "$R" 2)" "403"
R=$(call GET "$T_S" "/college-admin/warnings/57")
chk "学生 token 打 /college-admin/warnings/57 → HTTP" "$(seg "$R" 1)" "403"
R=$(call GET "$T_S" "/students/dashboard/27")   # 放行对照：学生自己的路径照常
chk "放行对照：学生 token 打自己的 /students/dashboard/27 → body.code" "$(seg "$R" 2)" "200"
R=$(call GET "$T_A" "/college-admin/dashboard/57")  # 放行对照：学院管理员照常
chk "放行对照：学院管理员 token 照常可用 → body.code" "$(seg "$R" 2)" "200"

echo
echo "==================== 收尾：核对演示数据没被动 ===================="
AFTER=$(mysql -uroot -proot -D academic_warning_system -N -B -e "SELECT COUNT(*) FROM academic_warning;" 2>/dev/null)
chk "academic_warning 表行数回到原值" "$AFTER" "$BEFORE"
LEFT=$(mysql -uroot -proot -D academic_warning_system -N -B -e "SELECT COUNT(*) FROM academic_warning WHERE description='W1.13-TEMP-PROBE';" 2>/dev/null)
chk "临时行已清理干净" "$LEFT" "0"
# 四条演示预警必须原封不动
DEMO=$(mysql -uroot -proot -D academic_warning_system -N -B -e \
  "SELECT GROUP_CONCAT(CONCAT(id,':',status) ORDER BY id) FROM academic_warning;" 2>/dev/null)
chk "四条演示预警 id:status 原样" "$DEMO" "395:0,396:0,397:0,398:0"

echo
echo "=================================================="
echo "通过 $PASS / 失败 $FAIL"
[ "$FAIL" = "0" ] || exit 1
