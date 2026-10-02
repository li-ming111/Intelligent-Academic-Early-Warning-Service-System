/**
 * 网关准入判定端到端探针（W3.3，2026-10-02）。
 *
 * 走**真网关**（8076），用仓库里的**开发默认密钥**签 JWT（不打印、不外传），
 * 逐条打「角色 × 路径 × 期望状态码」。
 *
 * 判据分两组：
 *  A. **自定义角色**（users.role ≥ 6，管理员在权限管理页新建的）—— 本轮新增的能力。
 *     改动前这类角色**永远翻译不出角色码**（RolePermissionCache 里是个 switch(1..5)、
 *     default "unknown"），勾多少权限都等于没勾。
 *  B. **内置角色回归** —— 改动前后必须逐条相同。这组是护栏：
 *     本轮把权限表接进了判定，最怕的就是"顺手"把内置角色也改成查表，
 *     那会把 role 1/2/3 的业务接口大面积 403 掉。
 *
 * 用法：`node probe-gateway-rbac.mjs [gatewayPort] [自定义角色id] [bound|unbound]`
 *   - `bound`（默认）：库里那个自定义角色**已绑定** PERM_SYSTEM_MANAGE ⇒ A1 期望 200；
 *   - `unbound`：**解除绑定**后（等过一个刷新周期）再跑 ⇒ A1 期望 403。
 *     这是本探针的负向对照：同一份代码、同一个 token，只差数据库里一条绑定行，
 *     结果必须从"放行"翻成"拒绝" —— 否则说明网关根本没在查权限表。
 */
import crypto from 'node:crypto'

const PORT = process.argv[2] || '8076'
const CUSTOM_ROLE = Number(process.argv[3] || 6)
const PHASE = process.argv[4] || 'bound'
const BOUND = PHASE !== 'unbound'
const SECRET = 'academic-jwt-secret-key-for-academic-warning-system-2026'

const b64url = (buf) => Buffer.from(buf).toString('base64url')

function mintToken(userId, username, role) {
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const now = Math.floor(Date.now() / 1000)
  const payload = b64url(JSON.stringify({
    userId, username, role, sub: username, iat: now, exp: now + 3600
  }))
  const sig = crypto.createHmac('sha256', SECRET).update(`${header}.${payload}`).digest('base64url')
  return `${header}.${payload}.${sig}`
}

async function call(path, { role, userId = 1, token } = {}) {
  const headers = {}
  const t = token !== undefined ? token : (role ? mintToken(userId, `probe${role}`, role) : null)
  if (t) headers.Authorization = `Bearer ${t}`
  const res = await fetch(`http://localhost:${PORT}${path}`, { headers })
  return res.status
}

const results = []
const check = (name, ok, detail = '') => {
  results.push({ name, ok })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`)
}

const ADMIN_API = '/api/admin/permissions/roles'   // 管理面（SYSTEM 模块，admin-service 直查库）

// ---------- A. 自定义角色：本轮新增的能力 ----------
const custom = await call(ADMIN_API, { role: CUSTOM_ROLE })
const expectedA1 = BOUND ? 200 : 403
check(`A1. 自定义角色(role=${CUSTOM_ROLE}) ${BOUND ? '有' : '**没有**'}「系统管理」权限 ⇒ 管理面${BOUND ? '放行' : '拒绝'}`,
  custom === expectedA1, `HTTP ${custom}（期望 ${expectedA1}）`)

const customTeacher = await call('/api/teachers/list', { role: CUSTOM_ROLE })
check('A2. 自定义角色进不去教师面（权限表没覆盖该前缀 ⇒ fail-closed）',
  customTeacher === 403, `HTTP ${customTeacher}（期望 403）`)

const customNoToken = await call(ADMIN_API, {})
check('A3. 不带 token ⇒ 401（准入判定之前先过认证）',
  customNoToken === 401, `HTTP ${customNoToken}（期望 401）`)

// ---------- B. 内置角色回归护栏 ----------
const studentAdmin = await call(ADMIN_API, { role: 1, userId: 9 })
check('B1. 学生打管理面 ⇒ 403（改动前后一致）',
  studentAdmin === 403, `HTTP ${studentAdmin}`)

const studentTeacher = await call('/api/teachers/list', { role: 1, userId: 9 })
check('B2. 学生打教师面 ⇒ 403（路径前缀层，改动前后一致）',
  studentTeacher === 403, `HTTP ${studentTeacher}`)

const teacherAdmin = await call(ADMIN_API, { role: 2, userId: 8 })
check('B3. 教师打管理面 ⇒ 403（改动前后一致）',
  teacherAdmin === 403, `HTTP ${teacherAdmin}`)

const adminAdmin = await call(ADMIN_API, { role: 4, userId: 1 })
check('B4. 管理员打管理面 ⇒ 放行（改动前后一致）',
  adminAdmin === 200, `HTTP ${adminAdmin}`)

// ---------- D. 角色 id 在 roles 表里根本不存在 ----------
// 这不是假想：删角色之后，手里还握着旧 token 的用户就是这种状态。
// 判据是「拒绝」，且**不能**因为"查不到所以碰巧 false"—— 见 RolePermissionCacheTest 第 4 条。
const ghostAdmin = await call(ADMIN_API, { role: 99, userId: 1 })
check('D1. token 里的角色 id 不在 roles 表 ⇒ 管理面拒绝（fail-closed）',
  ghostAdmin === 403, `HTTP ${ghostAdmin}（期望 403）`)

const ghostCourse = await call('/api/courses/count', { role: 99, userId: 1 })
check('D2. 同上，课程面也拒绝（不是只堵了管理面）',
  ghostCourse === 403, `HTTP ${ghostCourse}（期望 403）`)

console.log(`\n汇总：${results.filter(r => r.ok).length}/${results.length} 通过`)
process.exit(results.every(r => r.ok) ? 0 : 1)
