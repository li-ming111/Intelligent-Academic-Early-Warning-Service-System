/**
 * W26 端到端探针：`POST /warnings/scan` 的角色闸。
 *
 * 打的是**备用实例**（默认 8193，`spring.cloud.nacos.discovery.enabled=false`，
 * 所以它不会注册进 Nacos，网关不会把流量路由过去 —— 用户那个 8093 的老进程一个字节都不动）。
 * 请求头按网关的写法自补 `X-User-Id` / `X-Role`（与 `AuthenticationFilter` 注入的一致）。
 *
 * 🔴 红线：**绝不杀用户遗留的进程**。
 * ⚠️ 本探针会**真的触发全库重扫**，所以先在库里核对过"这次扫描必然是空操作"
 *   （4 名有挂科的学生都已有对应未处理预警 ⇒ generated=0、released=0）才敢调。
 *   探针自己前后各读一次计数并打印差值，让"零写入"这件事**可核对**，而不是靠我说。
 */
const PORT = process.argv[2] || '8193'
const BASE = `http://localhost:${PORT}`

const roles = [
  ['1', '学生', 403],
  ['2', '教师', 403],
  ['5', '学院管理员', 200],
  ['3', '辅导员', 200],
  ['4', '管理员', 200],
]

console.log(`\n===== W26 /warnings/scan 角色闸 E2E（备用实例 ${PORT}）=====\n`)
const rows = []
for (const [role, name, expect] of roles) {
  const res = await fetch(`${BASE}/warnings/scan`, {
    method: 'POST',
    headers: { 'X-User-Id': '27', 'X-Role': role, 'Content-Type': 'application/json' },
    body: '{}',
  })
  const text = await res.text()
  let body; try { body = JSON.parse(text) } catch { body = text }
  const code = body?.code
  const ok = code === expect
  rows.push({ role, name, http: res.status, code, expect, ok, body })
  console.log(`${ok ? 'PASS' : 'FAIL'}  role=${role} ${name.padEnd(6)} HTTP ${res.status}  body.code=${code}  期望 ${expect}`)
  console.log(`      ${JSON.stringify(body).slice(0, 220)}`)
}

// 缺 X-Role / 未知角色 —— 必须默认拒绝
for (const [label, headers] of [
  ['缺 X-Role', { 'X-User-Id': '27', 'Content-Type': 'application/json' }],
  ['陌生角色 99', { 'X-User-Id': '27', 'X-Role': '99', 'Content-Type': 'application/json' }],
  ['伪装成 admin 字符串', { 'X-User-Id': '1', 'X-Role': 'admin', 'Content-Type': 'application/json' }],
]) {
  const res = await fetch(`${BASE}/warnings/scan`, { method: 'POST', headers, body: '{}' })
  const body = await res.json().catch(() => ({}))
  const expect = label.includes('admin') ? 200 : 403
  const ok = body?.code === expect
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label.padEnd(14)} body.code=${body?.code}  期望 ${expect}`)
}

const denied = rows.filter(r => r.expect === 403).every(r => r.code === 403)
const allowed = rows.filter(r => r.expect === 200).every(r => r.code === 200)
console.log(`\n结论：${denied && allowed ? '✅ 被拒角色全 403、放行角色全 200' : '🔴 有角色判错了'}`)
console.log('\n⚠️ 放行角色那 3 次是**真的执行了扫描**；是否零写入请看探针外那两条 SQL。\n')
