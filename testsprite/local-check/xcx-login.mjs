// 小程序端（H5 通道）登录核查。
//
// 与同目录 role-dashboard.mjs 的区别：
//  1. 打的是 5174（小程序端），不是 5173（PC 端）—— 两边都是 vite 默认端口，撞过；
//  2. uni-app H5 用 hash 路由，落地路径在 location.hash 里，不是 pathname；
//  3. 顺带统计 console 未捕获异常与 4xx/5xx 请求 —— 小程序里页面报错是"白屏"而不是
//     弹红框，只看渲染结果会把"整页没渲染"误判成"渲染了但没数据"。
//
// 用法:
//   node xcx-login.mjs <用户名> <密码> <期望落地路径片段> [必须包含] [必须不包含]
// 例:
//   node xcx-login.mjs 2023020616 123456 /pages/student/dashboard 司明
//
// 验登录之后的**内页**时加 NAV 环境变量（登录成功后脚本会再跳一次）：
//   NAV=/pages/student/scores node xcx-login.mjs 2023020616 123456 /pages/student/scores 课程数
// 直接传第三个参数是不行的：登录成功必然 reLaunch 到角色首页，会把入参路由覆盖掉。
//
// 退出码 0=通过, 1=失败。
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const [USER, PASS, PATH_FRAG, MUST_HAVE = '', MUST_NOT = ''] = process.argv.slice(2)

if (!USER || !PASS || !PATH_FRAG) {
  console.error('用法: node xcx-login.mjs <用户名> <密码> <期望路径片段> [必须包含] [必须不包含]')
  process.exit(2)
}

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
// 用 127.0.0.1 而不是 localhost：dev server 若只绑了 ::1，Chrome 解析 localhost
// 会随机挑到 IPv4 然后 ERR_CONNECTION_REFUSED。写死 IPv4 能立刻暴露这个问题。
const APP = process.env.XCX_BASE || 'http://127.0.0.1:5180'
const PORT = 9622
const SETTLE_MS = Number(process.env.SETTLE_MS || 4500)

const profile = mkdtempSync(join(tmpdir(), 'cdpxcx-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const cleanup = () => {
  try { chrome.kill() } catch {}
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
}
const done = (code, msg) => {
  console.log(`${code === 0 ? 'PASS' : 'FAIL'}  ${msg}`)
  cleanup()
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
  const consoleErrors = []
  const netErrors = []

  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params?.exceptionDetails
      consoleErrors.push(`[exception] ${d?.exception?.description || d?.text || 'unknown'}`)
    }
    if (m.method === 'Runtime.consoleAPICalled' && m.params?.type === 'error') {
      const args = (m.params.args || []).map(a => a.value ?? a.description ?? '').join(' ')
      consoleErrors.push(`[console.error] ${args}`)
    }
    if (m.method === 'Network.responseReceived') {
      const { status, url } = m.params?.response || {}
      // 只关心打到后端/静态资源的真实失败，忽略 devtools 自身的探测
      if (status >= 400) netErrors.push(`${status} ${url}`)
    }
    if (m.method === 'Network.loadingFailed') {
      const url = m.params?.requestId ? '' : ''
      netErrors.push(`FAILED ${m.params?.errorText} ${url}`)
    }
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
  await send('Network.enable')

  // vite 是按需编译的：首次访问某页时先编译再加载，冷启动可能十几秒。
  // 固定 sleep 会在首次运行时误判成"页面没渲染"。改成轮询等条件成立。
  const waitFor = async (expr, timeoutMs = 40000, label = '') => {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      try { if (await evaluate(expr)) return true } catch {}
      await sleep(400)
    }
    if (label) console.log(`      ⚠️ 等待超时：${label}`)
    return false
  }

  await send('Page.navigate', { url: APP })

  // 先确认导航真的落到目标地址 —— 若连到了别的 target（例如残留 Chrome 的 about:blank），
  // 后面所有选择器都会"找不到"，很容易被误读成"页面坏了"。
  const navigated = await waitFor(
    `(location.href || '').includes(${JSON.stringify(new URL(APP).host)})`, 15000, '未导航到应用地址'
  )
  if (!navigated) {
    const href = await evaluate('location.href')
    return done(1, `导航失败，当前 href=${href}（多半是连到了残留浏览器实例）`)
  }

  const formReady = await waitFor(
    `document.querySelectorAll('uni-input .uni-input-placeholder').length >= 3 && !!document.querySelector('.btn')`,
    45000, '登录表单未出现'
  )
  if (!formReady) {
    const snapshot = await evaluate(`JSON.stringify({ href: location.href, body: (document.body.innerText||'').slice(0,200) })`)
    return done(1, `登录页未渲染完成：${snapshot}`)
  }
  const startRoute = await evaluate('location.hash || location.pathname')
  console.log(`      入口路由 = ${startRoute}`)

  // 装观察器，记录目标元素的每次文本变化（闪变检测）
  if (MUST_NOT) {
    await evaluate(`(() => {
      window.__seq = []
      const sel = ${JSON.stringify(MUST_NOT)}
      const record = () => {
        const t = (document.body.innerText || '').trim()
        if (t && window.__seq[window.__seq.length - 1] !== t) window.__seq.push(t)
      }
      record()
      new MutationObserver(record).observe(document.documentElement, { childList: true, subtree: true, characterData: true })
      return true
    })()`)
  }

  const filled = await evaluate(`(() => {
    // uni-app H5 的 <input> **没有** placeholder 属性：placeholder 是
    // uni-input 内部一个同级的 div.uni-input-placeholder。
    // 所以只能"按占位文案找到 uni-input 外壳，再取里面的 input"。
    const byPh = (ph) => {
      const wrap = Array.from(document.querySelectorAll('uni-input'))
        .find(u => (u.querySelector('.uni-input-placeholder')?.textContent || '').trim() === ph)
      return wrap ? wrap.querySelector('input') : null
    }
    const setVal = (el, val) => {
      if (!el) return false
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set
      setter.call(el, val)
      // uni-app 的 v-model 监听 input 事件，缺一不可
      el.dispatchEvent(new Event('input', { bubbles: true }))
      el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    // 先解析元素再赋值：赋值后 placeholder 节点会被移除
    const elUser = byPh('请输入学号或工号')
    const elPass = byPh('请输入密码')
    const elCode = byPh('输入验证码')
    const shown = (document.querySelector('.captcha-code')?.textContent || '').trim()
    const a = setVal(elUser, ${JSON.stringify(USER)})
    const b = setVal(elPass, ${JSON.stringify(PASS)})
    const c = setVal(elCode, shown)
    const btn = document.querySelector('.btn')
    if (btn) btn.click()
    return { a, b, c, captcha: shown, clicked: !!btn }
  })()`)
  if (!filled?.a || !filled?.clicked) return done(1, `登录表单元素没找全: ${JSON.stringify(filled)}`)
  if (!filled.c) console.log('      ⚠️ 验证码输入框没填上，可能是选择器变了')

  // 等登录落地。带 NAV 时目标不是落地页，所以判"离开了登录页"即可，
  // 否则这个循环会白等满 40 轮（20 秒）才继续。
  const NAV_TARGET = process.env.NAV || ''
  let route = ''
  for (let i = 0; i < 40; i++) {
    await sleep(500)
    route = await evaluate('location.hash || location.pathname')
    if (!route) continue
    if (NAV_TARGET) {
      if (!route.includes('/pages/auth/')) break
    } else if (route.includes(PATH_FRAG)) {
      break
    }
  }

  await sleep(SETTLE_MS)

  // 登录后跳到指定内页再断言。登录成功必定 reLaunch 到角色首页，
  // 所以"验内页"只能发生在登录之后，不能在入口直接指定。
  const NAV = process.env.NAV || ''
  if (NAV) {
    await evaluate(`location.hash = ${JSON.stringify('#' + NAV.replace(/^#/, ''))}`)
    const ok = await waitFor(
      `(location.hash || '').includes(${JSON.stringify(NAV)})`, 25000, `NAV 到 ${NAV} 后路由未变化`
    )
    if (!ok) {
      const href = await evaluate('location.href')
      return done(1, `NAV 导航失败，当前 href=${href}`)
    }
    // 路由变了不代表页面渲染完（vite 按需编译 + 接口往返），等正文出现再断言
    await waitFor(`(document.body.innerText || '').trim().length > 20`, 25000, 'NAV 后正文为空')
    await sleep(SETTLE_MS)
  }

  const bodyText = ((await evaluate('document.body.innerText')) || '').trim()
  const settledRoute = await evaluate('location.hash || location.pathname')

  console.log(`      落地路由     = ${settledRoute}`)
  console.log(`      正文前 160 字 = ${JSON.stringify(bodyText.slice(0, 160))}`)
  if (consoleErrors.length) {
    console.log(`      console 异常 ${consoleErrors.length} 条：`)
    consoleErrors.slice(0, 8).forEach(t => console.log(`        ${t}`))
  } else {
    console.log('      console 异常 0 条')
  }
  const realNet = netErrors.filter(t => !/favicon|\.map$/.test(t))
  if (realNet.length) {
    console.log(`      ❌ 4xx/5xx ${realNet.length} 条：`)
    realNet.slice(0, 10).forEach(t => console.log(`        ${t}`))
  } else {
    console.log('      4xx/5xx 0 条')
  }

  if (!settledRoute || !settledRoute.includes(PATH_FRAG)) {
    return done(1, `未跳到 ${PATH_FRAG}, 当前=${settledRoute}`)
  }
  if (!bodyText) return done(1, '页面渲染为空（白屏）')
  if (MUST_NOT && bodyText.includes(MUST_NOT)) return done(1, `页面出现不该有的「${MUST_NOT}」`)
  if (MUST_HAVE && !bodyText.includes(MUST_HAVE)) return done(1, `页面未包含「${MUST_HAVE}」`)
  // 首屏闪变必须判失败：稳定态对、但中途闪过错误文案，是真实可复现的体验缺陷
  const seq = (await evaluate('window.__seq || []')) || []
  if (MUST_NOT && seq.some(t => t.includes(MUST_NOT)) && process.env.ALLOW_FLASH !== '1') {
    return done(1, `首屏出现过「${MUST_NOT}」后又被纠正，属可见闪变`)
  }
  if (realNet.length) return done(1, `登录过程中有 ${realNet.length} 条 4xx/5xx`)
  if (consoleErrors.length) return done(1, `登录过程中有 ${consoleErrors.length} 条 console 异常`)
  return done(0, `落地 ${settledRoute}，渲染正常，无接口错误、无 console 异常`)
}

main().catch((e) => done(1, `脚本异常: ${e.message}`))
