// 验证码核查：账号密码都对、只把验证码填错，必须被拦在 /login 且提示「验证码错误」。
// 这条断言针对的是一个真实存在过的缺陷：Login.vue 只判验证码非空、从不与 captchaCode 比对，
// 填任何一个字符都能登录 —— 那时本脚本必然 FAIL（会直接跳进看板）。
// 用法: node captcha-wrong.mjs [用户名] [密码]     退出码 0=通过, 1=失败
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || '1000000001'
const PASS = process.argv[3] || '123456'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9500 + Math.floor(Math.random() * 100)
const profile = mkdtempSync(join(tmpdir(), 'cdpc-'))
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

  // ElMessage 是 3 秒自动消失的浮层，点完再去找就晚了 —— 先装观察器把所有提示文本记下来
  await evaluate(`(() => {
    window.__msgs = []
    const grab = () => {
      document.querySelectorAll('.el-message__content, .el-alert__title').forEach(e => {
        const t = (e.textContent || '').trim()
        if (t && !window.__msgs.includes(t)) window.__msgs.push(t)
      })
    }
    grab()
    new MutationObserver(grab).observe(document.documentElement, { childList: true, subtree: true, characterData: true })
    return true
  })()`)

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
    const shown = (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
    // 故意填错：保证和显示值不同，长度一致以免被"长度"这类旁路拦住
    const wrong = shown === 'AAAAAA' ? 'BBBBBB' : 'AAAAAA'
    const a = setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(USER)})
    const b = setVal('input[placeholder="请输入密码"]', ${JSON.stringify(PASS)})
    const c = setVal('input[placeholder="验证码"]', wrong)
    const btn = document.querySelector('.lr-submit')
    if (btn) btn.click()
    return { a, b, c, shown, wrong, clicked: !!btn }
  })()`)
  if (!filled?.a || !filled?.b || !filled?.c || !filled?.clicked) {
    return done(1, `登录表单元素没找全: ${JSON.stringify(filled)}`)
  }
  console.log(`      页面显示验证码 = "${filled.shown}"，实际填入 = "${filled.wrong}"`)

  await sleep(3000)
  const path = await evaluate('location.pathname')
  const msgs = (await evaluate('window.__msgs || []')) || []
  const nowShown = ((await evaluate(`(document.querySelector('.lr-captcha-btn')||{}).textContent || ''`)) || '').trim()

  console.log(`      当前路径        = ${path}`)
  console.log(`      出现过的提示    = ${JSON.stringify(msgs)}`)
  console.log(`      之后显示验证码  = "${nowShown}"`)

  if (!path.includes('/login')) {
    return done(1, `验证码填错却登录成功了，已跳到 ${path} —— 验证码没有真正校验`)
  }
  if (!msgs.some((m) => m.includes('验证码'))) {
    return done(1, `停在登录页但没有出现含「验证码」的提示，提示为 ${JSON.stringify(msgs)}`)
  }
  if (nowShown && nowShown === filled.shown) {
    console.log(`      注意：验证码填错后没有换一张（仍是 "${nowShown}"）`)
  }
  return done(0, `验证码填错被拦在登录页，提示含「验证码」`)
}

main().catch((e) => done(1, `脚本异常: ${e.message}`))
