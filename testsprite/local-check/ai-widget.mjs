// AI 助手浮窗（components/AIAssistant.vue，挂在 4 个 layout 上 = 每一页都有）的实机核对。
//
// 只查一件事：**没配 DashScope key 时，它还会不会给出一个"看起来像 AI 答的"东西**。
// 修前：AIController 在模型没答时塞一句写死的话并回 success:true → 浮窗照常显示成 AI 回答。
// 修后：接口如实回失败，浮窗必须把**后端的原话**（未配置 API Key）显示出来。
// 另外 ai-service 修 SecurityConfig 之前是恒 403，那时这里连 Controller 都到不了。
//
// 用法: node ai-widget.mjs   （需 vite:5173 + 网关:8076 + 8071）
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const GATEWAY = 'http://localhost:8076'
const PORT = 9500 + Math.floor(Math.random() * 90)
const profile = mkdtempSync(join(tmpdir(), 'cdpaiw-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const fails = []
let closeBrowser = async () => { try { chrome.kill() } catch {} }
const CANNED = '已收到您的问题，请提供具体成绩数据以便精确分析'

const main = async () => {
  let target = null
  for (let i = 0; i < 60; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      target = list.find((t) => t.type === 'page')
      if (target) break
    } catch {}
    await sleep(250)
  }
  if (!target) throw new Error('Chrome 调试端口未就绪')
  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
  let id = 0
  const pending = new Map()
  const jsErrors = []
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params.exceptionDetails
      jsErrors.push((d?.exception?.description || d?.text || '').split('\n')[0].slice(0, 200))
    }
  }
  const send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) return { __err: r.result.exceptionDetails.exception?.description }
    return r.result?.result?.value
  }
  await send('Page.enable'); await send('Runtime.enable')
  closeBrowser = async () => { try { await send('Browser.close') } catch {} }

  const r0 = await fetch(`${GATEWAY}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: '1000000001', password: '123456' }),
  })
  const d = (await r0.json()).data
  await send('Page.navigate', { url: `${APP}/login` })
  await sleep(800)
  await evaluate(`(() => {
    localStorage.clear()
    localStorage.setItem('token', ${JSON.stringify(d.token)})
    localStorage.setItem('userId', String(${JSON.stringify(String(d.userId))}))
    localStorage.setItem('username', '1000000001')
    localStorage.setItem('name', ${JSON.stringify(d.name || '')})
    localStorage.setItem('role', String(${JSON.stringify(String(d.role))}))
  })()`)
  await send('Page.navigate', { url: `${APP}/counselor/dashboard` })
  await sleep(7000)

  // 浮窗默认是收起的：先点那个可拖拽的小球（.chat-trigger，handleClick 里判 hasMoved，
  // 不经过 mousedown 直接 .click() 等同于"没拖动"→ 会展开）
  const opened = await evaluate(`(() => {
    if (document.querySelector('.quick-btn')) return 'already-open'
    const t = document.querySelector('.chat-trigger')
    if (!t) return null
    t.click(); return 'chat-trigger'
  })()`)
  console.log(`打开浮窗方式: ${opened}`)
  if (!opened) { fails.push('页面上找不到 AI 助手浮窗的入口'); }
  await sleep(1200)
  const asked = await evaluate(`(() => {
    const q = document.querySelector('.quick-btn')
    if (q) { q.click(); return q.innerText.trim() }
    return null
  })()`)
  console.log(`点的问题: ${JSON.stringify(asked)}`)
  if (!asked) fails.push('浮窗打开了但没有快捷提问按钮可点')
  await sleep(6000)

  const bubbles = await evaluate(`[...document.querySelectorAll('.msg-bubble')].map(b => b.innerText.trim())`)
  console.log(`浮窗气泡: ${JSON.stringify(bubbles)}`)
  const last = Array.isArray(bubbles) ? bubbles[bubbles.length - 1] : null
  const all = (bubbles || []).join(' | ')
  const bad = all.includes(CANNED)
  console.log(`${bad ? '✗' : '✓'} 浮窗里没有写死的那句"AI 回答"（不该含 ${JSON.stringify(CANNED)}）`)
  if (bad) fails.push('AI 助手浮窗显示了写死的假回答')
  if (!/API Key|DASHSCOPE_KEY/.test(all)) {
    fails.push(`浮窗没有把后端的失败原因显示出来，实际=${JSON.stringify(last)}`)
    console.log('✗ 浮窗没显示失败原因')
  } else console.log('✓ 浮窗显示了后端给的失败原因')
  if (/服务暂时不可用/.test(all)) {
    fails.push('浮窗仍在用抹平后的"服务暂时不可用"')
    console.log('✗ 仍在用抹平后的文案')
  } else console.log('✓ 不再用抹平后的"服务暂时不可用"')

  console.log(`\nJS 异常: ${jsErrors.length ? JSON.stringify(jsErrors) : '无'}`)
  if (jsErrors.length) fails.push(`页面有 JS 异常: ${jsErrors[0]}`)
  console.log(fails.length ? `\n结果: FAIL (${fails.length})\n - ${fails.join('\n - ')}` : '\n结果: PASS')
  await closeBrowser()
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
  process.exit(fails.length ? 1 : 0)
}

main().catch(async (e) => {
  console.error('脚本异常:', e.message)
  await closeBrowser()
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
  process.exit(2)
})
