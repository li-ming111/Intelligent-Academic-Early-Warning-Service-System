// PC 端（vite 5173）截图，用来肉眼核对**视觉类改动**。
//
// 为什么需要它：把学生「消息中心」从 `views/Messages.vue` 换成 `views/student/NotificationCenter.vue`
// 之后，新页面内部用的是 `el-container/el-aside/el-main`（全仓唯一这么写的页面），
// 而它现在要嵌在 `StudentLayout` 的正文区里 —— 这种嵌套**构建和冒烟都照过**，
// 只有截图能看出"侧栏跑出可视区/正文被压扁"。
//
// 用法: node pc-shot.mjs <outPrefix> <path>...      例: node pc-shot.mjs after-msg /student/messages
// 输出: <outPrefix>-<序号>.png（1440x900 视口）
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.PC_BASE || 'http://localhost:5173'
const USER = process.env.PC_USER || '2023020616'
const PASS = process.env.PC_PASS || '123456'

const prefix = process.argv[2]
const paths = process.argv.slice(3)
if (!prefix || !paths.length) { console.error('用法: node pc-shot.mjs <outPrefix> <path>...'); process.exit(2) }

const PORT = 9800 + Math.floor(Math.random() * 90)
const profile = mkdtempSync(join(tmpdir(), 'cdppc-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--hide-scrollbars',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const cleanup = () => { try { chrome.kill() } catch {}; try { rmSync(profile, { recursive: true, force: true }) } catch {} }

const main = async () => {
  let target = null
  for (let i = 0; i < 80; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      target = list.find((t) => t.type === 'page')
      if (target) break
    } catch {}
    await sleep(250)
  }
  if (!target) { console.error('调试端口未就绪'); cleanup(); process.exit(1) }
  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
  let id = 0
  const pending = new Map()
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id) }
  }
  const send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) =>
    (await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })).result?.result?.value

  await send('Page.enable'); await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })

  await send('Page.navigate', { url: `${APP}/login` })
  await sleep(3000)
  const filled = await evaluate(`(() => {
    const setVal = (sel, val) => {
      const el = document.querySelector(sel); if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    const shown = (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
    const a = setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(USER)})
    const b = setVal('input[placeholder="请输入密码"]', ${JSON.stringify(PASS)})
    const c = setVal('input[placeholder="验证码"]', shown)
    const btn = document.querySelector('.lr-submit'); if (btn) btn.click()
    return { a, b, c, clicked: !!btn }
  })()`)
  if (!filled?.a || !filled?.clicked) { console.error(`登录表单没找全: ${JSON.stringify(filled)}`); cleanup(); process.exit(1) }
  let path = ''
  for (let i = 0; i < 40; i++) { await sleep(500); path = await evaluate('location.pathname'); if (path && path !== '/login') break }
  if (!path || path === '/login') { console.error('登录失败'); cleanup(); process.exit(1) }
  console.log(`已登录 ${USER} → ${path}`)

  let n = 0
  for (const p of paths) {
    n++
    await send('Page.navigate', { url: `${APP}${p}` })
    await sleep(5000)
    const name = `${prefix}-${String(n).padStart(2, '0')}.png`
    const r = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
    const data = r.result?.data
    if (!data) { console.error(`截图失败 ${name}`); continue }
    writeFileSync(name, Buffer.from(data, 'base64'))
    console.log(`${name}  ← ${p}`)
  }
  cleanup()
  process.exit(0)
}

main().catch((e) => { console.error('FAIL', e.message); cleanup(); process.exit(1) })
