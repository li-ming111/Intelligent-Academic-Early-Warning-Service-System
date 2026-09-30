// 零依赖 CDP 核查：辅导员登录后 /counselor/dashboard 问候语里的姓名应为真名，而非兜底文案「辅导员」。
// 用法: node testsprite/local-check/counselor-dashboard-name.mjs
// 退出码 0=通过, 1=失败。不消耗 TestSprite credits。
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const PORT = 9335
const APP = 'http://localhost:5173'
const USER = '1000000001'
const PASS = '123456'
const EXPECT_NAME = '司明'
const FALLBACK = '辅导员'

const profile = mkdtempSync(join(tmpdir(), 'cdp3-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const done = (code, msg) => {
  console.log(`${code === 0 ? 'PASS' : 'FAIL'}  ${msg}`)
  try { chrome.kill() } catch {}
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
  process.exit(code)
}

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
  if (!target) return done(1, 'Chrome 调试端口未就绪')

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
  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) throw new Error(r.result.exceptionDetails.text)
    return r.result?.result?.value
  }

  await send('Page.enable')
  await send('Runtime.enable')
  await send('Page.navigate', { url: `${APP}/login` })
  await sleep(3000)

  const filled = await evaluate(`(() => {
    const setVal = (sel, val) => {
      const el = document.querySelector(sel)
      if (!el) return false
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set
      setter.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true }))
      el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    const a = setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(USER)})
    const b = setVal('input[placeholder="请输入密码"]', ${JSON.stringify(PASS)})
    // 验证码已改为真实校验，必须读页面上显示的那个（.lr-captcha-btn 的文字）填回去
    const shown = (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
    const c = setVal('input[placeholder="验证码"]', shown)
    const btn = document.querySelector('.lr-submit')
    if (btn) btn.click()
    return { a, b, c, captcha: shown, clicked: !!btn }
  })()`)
  if (!filled?.a || !filled?.clicked) return done(1, `登录表单元素没找全: ${JSON.stringify(filled)}`)

  let path = ''
  for (let i = 0; i < 40; i++) {
    await sleep(500)
    path = await evaluate('location.pathname')
    if (path && path.includes('/counselor/dashboard')) break
  }
  if (!path || !path.includes('/counselor/dashboard')) {
    const alert = await evaluate(`(document.querySelector('.el-alert__title')||{}).textContent || ''`)
    return done(1, `未跳到辅导员仪表盘, 当前路径=${path}, 页面提示="${alert}"`)
  }

  // 多等几拍，给 loadDashboard / loadCounselorInfo 留出写 localStorage 的时间
  let text = null
  for (let i = 0; i < 20; i++) {
    text = await evaluate(`(document.querySelector('.cd-greet')||{}).textContent || ''`)
    if (text && text.trim()) break
    await sleep(500)
  }
  await sleep(1500)

  const got = (text || '').trim()
  console.log(`      路径            = ${path}`)
  console.log(`      .cd-greet       = "${got}"`)
  console.log(`      localStorage.userName = "${await evaluate(`localStorage.getItem('userName')||''`)}"`)
  console.log(`      localStorage.name     = "${await evaluate(`localStorage.getItem('name')||''`)}"`)

  if (!got) return done(1, '页面上找不到 .cd-greet 元素（或它为空）')
  if (got.includes(FALLBACK)) return done(1, `问候语显示兜底文案「${FALLBACK}」而非真名 —— 这正是要修的 bug`)
  if (!got.includes(EXPECT_NAME)) return done(1, `问候语显示 "${got}"，未包含真名「${EXPECT_NAME}」`)
  return done(0, `问候语如实显示真名: "${got}"`)
}

main().catch((e) => done(1, `脚本异常: ${e.message}`))
