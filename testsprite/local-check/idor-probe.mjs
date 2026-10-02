import { captchaFields } from './_captcha.mjs'
// 越权(横向)只读探针：用**学生**的令牌去打只有管理员/别人该能用的接口，看谁放行。
//
// 目的：手册 §7 里"改密码/个人设置信任 body 或路径里的 userId、不做归属校验"这条
// 目前是**读代码得出的结论**。答辩前把它变成实测事实（哪些真的能读通、哪些被拦），
// 免得被追问时只能说"应该可以"。
//
// 只做**读**，不发任何写请求 —— 不改数据、不改密码、不建账号。
// 刻意**不碰** `/users/by-username/{name}`：那个接口返回原始 User 实体，**含 password 哈希**，
// 读进终端没有任何意义（也无法支撑"越权"的结论，能拿到 DTO 就已经说明了问题）。
//
// 用法: node idor-probe.mjs         退出码恒为 0（这是取证，不是断言通过/失败）
const G = 'http://localhost:8076'

const login = async (u, p) => {
  const r = await fetch(G + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: u, password: p, ...(await captchaFields(G)) }),
  }).then((x) => x.json())
  return r?.data?.token || null
}

// 返回 {http, code, shape} —— 只描述结构与**少量非敏感字段**，不打印整包
const probe = async (path, token) => {
  try {
    const r = await fetch(G + path, { headers: { Authorization: 'Bearer ' + token } })
    const text = await r.text()
    let j = null
    try { j = JSON.parse(text) } catch {}
    const d = j?.data
    let shape = typeof d
    if (Array.isArray(d)) shape = `array(${d.length})`
    else if (d && typeof d === 'object') shape = `object{${Object.keys(d).slice(0, 8).join(',')}}`
    return { http: r.status, code: j?.code, shape, sample: d && !Array.isArray(d) ? JSON.stringify(d).slice(0, 120) : '' }
  } catch (e) { return { http: 'ERR', code: '-', shape: e.message, sample: '' } }
}

const st = await login('2023020616', '123456')     // 学生 司明, users.id=27
const co = await login('1000000001', '123456')     // 辅导员, users.id=53
console.log('学生令牌:', st ? '拿到' : '拿不到')
console.log('辅导员令牌:', co ? '拿到' : '拿不到')
if (!st) process.exit(0)

// 每个角色都试：这些路径按设计只有管理员/对应角色该能用
const PATHS = [
  ['/api/users/info/53', '读别人的用户资料（辅导员 53）'],
  ['/api/users/53/settings', '读别人的个人设置（辅导员 53）'],
  ['/api/users/list?page=1&size=3', '列全部用户（含邮箱/电话）'],
  ['/api/users/count', '用户总数'],
  ['/api/admin/users/page?page=1&size=3', '管理员用户分页'],
  ['/api/admin/permissions/roles', '管理员角色表'],
  ['/api/admin/statistics/overview', '管理员总览统计'],
  ['/api/counselors/dashboard/53', '辅导员的看板数据'],
]

for (const who of [['学生', st], ['学生→改冒充辅导员的id', null]]) {}
for (const [label, tok] of [['学生 2023020616', st], ['辅导员 1000000001', co]]) {
  if (!tok) continue
  console.log(`\n===== 用「${label}」的令牌去请求 =====`)
  for (const [p, desc] of PATHS) {
    const r = await probe(p, tok)
    const verdict = r.http === 200 && r.code === 200 ? '放行' : (r.http === 403 ? '403 拒绝' : `${r.http}${r.code && r.code !== 200 ? `/code=${r.code}` : ''}`)
    console.log(`  ${String(verdict).padEnd(12)} ${p}`)
    console.log(`               ${desc}  → ${r.shape}${r.sample ? '  ' + r.sample : ''}`)
  }
}
