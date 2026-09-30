// 零依赖 CDP 核查：密码错误时登录页必须显示红色错误提示，而不是毫无反应。
// 用法: node testsprite/local-check/login-wrong-password.mjs
// 退出码 0=通过, 1=失败。不消耗 TestSprite credits。
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const PORT = 9334
const APP = 'http://localhost:5173'
const WRONG_PASS = 'WrongPass999'

const profile = mkdtempSync(join(tmpdir(), 'cdp2-'))
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
    const a = setVal('input[placeholder="请输入学号或工号"]', '2023020616')
    const b = setVal('input[placeholder="请输入密码"]', ${JSON.stringify(WRONG_PASS)})
    // 验证码已改为真实校验，必须读页面上显示的那个（.lr-captcha-btn 的文字）填回去
    const shown = (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
    const c = setVal('input[placeholder="验证码"]', shown)
    const btn = document.querySelector('.lr-submit')
    if (btn) btn.click()
    return { a, b, c, captcha: shown, clicked: !!btn }
  })()`)
  if (!filled?.a || !filled?.clicked) return done(1, `登录表单元素没找全: ${JSON.stringify(filled)}`)

  // 等错误提示出现（或超时判定为"毫无反应"）
  let alertText = ''
  for (let i = 0; i < 24; i++) {
    await sleep(500)
    alertText = await evaluate(`(document.querySelector('.el-alert__title')||{}).textContent || ''`)
    if (alertText && alertText.trim()) break
  }
  const path = await evaluate('location.pathname')
  const hasNav = await evaluate(`!!document.querySelector('.el-menu, .sidebar, aside')`)
  const got = (alertText || '').trim()

  console.log(`      路径        = ${path}`)
  console.log(`      错误提示     = "${got}"`)

  if (path && path.includes('/student/dashboard')) return done(1, `密码错误竟然登录成功了，跳到了 ${path}`)
  if (hasNav) return done(1, '密码错误却出现了学生端导航栏，疑似登录成功')
  if (!got) return done(1, '密码错误时页面上没有任何错误提示 —— 这就是要修的「毫无反应」bug')
  if (!got.includes('密码错误')) return done(1, `错误提示文案是 "${got}"，未包含「密码错误」`)
  return done(0, `密码错误时如实显示提示 "${got}"`)
}

main().catch((e) => done(1, `脚本异常: ${e.message}`))
