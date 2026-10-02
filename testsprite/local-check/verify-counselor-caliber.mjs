// 校验「辅导员管理班级」的口径在**库与四个接口**上是否一致。
//
// 背景：辅导员司明（users.id=53，档案属软件学院 college_id=1）原本名下挂了 8 个班
// —— 软件学院 6 个 + 电子工程学院 2 个（classes 34/35）。于是：
//   辅导员端「管理班级」= 8，而同页「有学生的班级」= 1，同一个词两个数；
//   且 8 里那两个班不属他的学院，与本人档案直接矛盾。
// 修法是清掉 classes 34/35 的 counselor_id → 管理班级应为 6，与学院管理员端同值。
//
// 用法：node verify-counselor-caliber.mjs before|after
//   before → 期望 8（修复前，用来证明这个脚本确实能看见问题）
//   after  → 期望 6（修复后）
//
// 凭据走 MYSQL_PWD 环境变量而不是 -p 命令行，避免出现在进程列表里。
import { spawnSync } from 'node:child_process'
import { captchaFields } from './_captcha.mjs'

const ROLE = process.argv[2]
if (!['before', 'after'].includes(ROLE)) {
  console.error('用法: node verify-counselor-caliber.mjs before|after')
  process.exit(2)
}

const GW = process.env.GW || 'http://localhost:8076'
const EXPECT_MANAGED = ROLE === 'before' ? 8 : 6
const EXPECT_HASCNSL = ROLE === 'before' ? 0 : 0 // 两个班无论前后都不该有辅导员（修复前是 53）

/** 跑一条 SQL，返回二维数组（制表符分隔）。 */
function q(sql) {
  const r = spawnSync('mysql', ['-uroot', '--default-character-set=utf8mb4', '-N', 'academic_warning_system', '-e', sql],
    { encoding: 'utf8', env: { ...process.env, MYSQL_PWD: 'root' } })
  if (r.status !== 0) throw new Error('mysql 失败: ' + (r.stderr || '').trim())
  return (r.stdout || '').trim().split('\n').filter(Boolean).map(l => l.split('\t'))
}

const results = []
function check(name, actual, expected) {
  const ok = String(actual) === String(expected)
  results.push({ name, actual, expected, ok })
  console.log(`${ok ? '✓' : '✗'} ${name}：实测 ${actual}，期望 ${expected}`)
}

async function api(path, token) {
  const r = await fetch(GW + '/api' + path, { headers: token ? { Authorization: 'Bearer ' + token } : {} })
  return r.json()
}
async function login(username) {
  const r = await fetch(GW + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: '123456', ...(await captchaFields(GW)) })
  })
  return ((await r.json()).data || {}).token || ''
}

// ---------- 1. 库：真源 ----------
console.log('\n── 库（classes.counselor_id 是唯一被代码读取的关联）')
check('classes 34/35 的 counselor_id',
  q('SELECT IFNULL(GROUP_CONCAT(IFNULL(counselor_id,"NULL")),"-") FROM classes WHERE id IN (34,35)')[0][0],
  ROLE === 'before' ? '53,53' : 'NULL,NULL')
check('司明(53) 名下班级数', q('SELECT COUNT(*) FROM classes WHERE counselor_id=53')[0][0], EXPECT_MANAGED)
check('其中不在软件学院(college_id=1)的班数',
  q('SELECT COUNT(*) FROM classes WHERE counselor_id=53 AND college_id<>1')[0][0], 0)
check('其中真的有学生的班数',
  q('SELECT COUNT(*) FROM classes WHERE counselor_id=53 AND id IN (SELECT DISTINCT class_id FROM student_profile WHERE class_id IS NOT NULL)')[0][0], 1)
check('影子表 counselor_class_management 行数（应与真源一致）',
  q('SELECT COUNT(*) FROM counselor_class_management WHERE counselor_id=53')[0][0], EXPECT_MANAGED)

// ---------- 2. 辅导员端接口 ----------
console.log('\n── 接口')
const ct = await login('1000000001')
const my = await api('/counselors/class-management/my-classes?counselorId=53', ct)
const myRows = Array.isArray(my.data) ? my.data : []
check('辅导员端 /class-management/my-classes 返回班数', myRows.length, EXPECT_MANAGED)
check('  其中跨学院的班数', myRows.filter(c => Number(c.collegeId) !== 1).length, 0)

// ---------- 3. 学院管理员端接口（Math.min 截断 bug 的落点）----------
const at = await login('collegeadmin1')
const dash = (await api('/college-admin/dashboard/57', at)).data || {}
check('学院管理员端 classCount（软件学院班级数）', dash.classCount, 6)
check('学院管理员端 counselorCount（有辅导员的班级）', dash.counselorCount, 6)

// ---------- 4. 两端必须同值：这是"两端配色/口径一致"的同一条要求 ----------
console.log('\n── 跨端一致性')
check('辅导员端「管理班级」 == 学院管理员端「班级」', myRows.length, dash.classCount)

const bad = results.filter(r => !r.ok)
console.log(`\n==== ${ROLE}: ${results.length - bad.length}/${results.length} 通过 ====`)
process.exit(bad.length ? 1 : 0)
