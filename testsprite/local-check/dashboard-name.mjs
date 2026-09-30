// 零依赖 CDP 核查：登录后 /student/dashboard 顶部大号姓名是否显示真名而非学号。
// 用法: node testsprite/local-check/dashboard-name.mjs
// 退出码 0=通过, 1=失败。不消耗 TestSprite credits。
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const PORT = 9333
const APP = 'http://localhost:5173'
const USER = '2023020616'
const PASS = '123456'
const EXPECT_NAME = '司明'
const EXPECT_NOT = '2023020616'

const profile = mkdtempSync(join(tmpdir(), 'cdp-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const fail = async (msg) => { console.log(`FAIL  ${msg}`); cleanup(1) }
const pass = async (msg) => { console.log(`PASS  ${msg}`); cleanup(0) }

function cleanup(code) {
  try { chrome.kill() } catch {}
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
  process.exit(code)
}

const main = async () => {
  // 等 chrome 的调试端口起来
  let target = null
  for (let i = 0; i < 60; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      target = list.find((t) => t.type === 'page')
      if (target) break
    } catch {}
    await sleep(250)
  }
  if (!target) return fail('Chrome 调试端口未就绪')

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })

  let id = 0
  const pending = new Map()
  const events = []
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id) }
    else if (m.method) events.push(m.method)
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

  // 1) 打开登录页
  await send('Page.navigate', { url: `${APP}/login` })
  await sleep(3000)

  // 2) 填表并提交（用原生 setter 触发 Vue 的 v-model）
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
  if (!filled?.a || !filled?.b || !filled?.c || !filled?.clicked) {
    return fail(`登录表单元素没找全: ${JSON.stringify(filled)}`)
  }

  // 3) 等跳转到仪表盘
  let path = ''
  for (let i = 0; i < 40; i++) {
    await sleep(500)
    path = await evaluate('location.pathname')
    if (path && path.includes('/student/dashboard')) break
  }
  if (!path || !path.includes('/student/dashboard')) {
    const alert = await evaluate(`(document.querySelector('.el-alert__title')||{}).textContent || ''`)
    return fail(`未跳到学生仪表盘, 当前路径=${path}, 页面提示="${alert}"`)
  }

  // 4) 等姓名元素渲染出来
  let text = null
  for (let i = 0; i < 20; i++) {
    text = await evaluate(`(document.querySelector('.hero-name')||{}).textContent || ''`)
    if (text && text.trim()) break
    await sleep(500)
  }

  const got = (text || '').trim()
  console.log(`      路径        = ${path}`)
  console.log(`      .hero-name  = "${got}"`)
  console.log(`      localStorage.name = "${await evaluate(`localStorage.getItem('name')||''`)}"`)

  if (!got) return fail('页面上找不到 .hero-name 元素（或它为空）')
  if (got.includes(EXPECT_NOT)) return fail(`大号姓名区显示了学号 ${EXPECT_NOT} —— 这正是要修的 bug`)
  if (got !== EXPECT_NAME) return fail(`大号姓名区显示 "${got}"，期望 "${EXPECT_NAME}"`)
  return pass(`大号姓名区显示 "${got}"，且不含学号`)
}

main().catch((e) => fail(`脚本异常: ${e.message}`))
