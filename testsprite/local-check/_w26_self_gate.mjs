/** W26 附探针：student-service 的读侧归属闸到底拦不拦跨学生读（对照「旧 jar vs 新代码」）。 */
const cases = [
  ['8179(patched) X-User-Id=30 → 27', 'http://localhost:8179/students/scores/27', { 'X-User-Id': '30', 'X-Role': '1' }],
  ['8179(patched) X-User-Id=27 → 27', 'http://localhost:8179/students/scores/27', { 'X-User-Id': '27', 'X-Role': '1' }],
  ['8179(patched) X-User-Id=1,role4 → 27', 'http://localhost:8179/students/scores/27', { 'X-User-Id': '1', 'X-Role': '4' }],
  ['8079(stale)   X-User-Id=30 → 27', 'http://localhost:8079/students/scores/27', { 'X-User-Id': '30', 'X-Role': '1' }],
]
for (const [name, url, h] of cases) {
  const r = await fetch(url, { headers: h })
  const t = await r.text()
  let body; try { body = JSON.parse(t) } catch { body = t.slice(0, 80) }
  const n = Array.isArray(body?.data) ? body.data.length : '-'
  console.log(name.padEnd(40), 'HTTP', r.status, 'code', body?.code ?? '-', 'rows', n, '|', (body?.message || '').slice(0, 40))
}
