// 学院管理员端配色回归（PC 端 5173）。
//
// 背景：`theme.js` 的两个 roleMap 与 `App.vue:177` 的 roleMap 都缺 `'5'`，
// role=5 一路兜底到 default / admin，结果学院管理员和管理员是同一个紫色。
// 2026-09-29 补上 `collegeAdmin` 主题（#0EA5E9，与小程序端同值），
// 并把 9 个页面的 `.cad-hero` 从硬编码的辅导员蓝改成 `var(--theme-gradient)`。
//
// 本脚本把三件事一次验完：
//   1. 登录后根元素上的 --theme-primary / --el-color-primary 确实是天蓝；
//   2. 9 个页面都能打开、无 console 异常、无 4xx/5xx；
//   3. 每页 hero 的渐变里**没有**辅导员蓝 rgb(37,99,235)（也没漏成管理员紫 rgb(102,126,234)）。
//
// 用法: node collegeadmin-theme.mjs [用户名] [密码]
// 退出码 0=全通过, 1=有失败。
import { spawn, execFileSync } from 'node:child_process'
import { mkdtempSync, rmSync, mkdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || 'collegeadmin1'
const PASS = process.argv[3] || '123456'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9500 + Math.floor(Math.random() * 100)
const SETTLE_MS = 3500
const SHOT_DIR = 'D:\\ABS\\tmp\\shots'

const ROUTES = [
  '/college-admin/dashboard',
  '/college-admin/students',
  '/college-admin/teachers',
  '/college-admin/warnings',
  '/college-admin/classes',
  '/college-admin/analysis',
  '/college-admin/scores',
  '/college-admin/counselors',
  '/college-admin/export',
]

/** 期望的天蓝主色。写死成字面量：从主题文件 import 会让脚本自己给自己打分。 */
const WANT_PRIMARY = '#0EA5E9'
/** 不该出现的两种旧色（辅导员蓝 / 管理员紫），按 computed 后的 rgb() 形式比对 */
const FORBIDDEN_RGB = [
  ['37, 99, 235', '辅导员蓝 #2563eb'],
  ['102, 126, 234', '管理员紫 #667eea'],
]

const profile = mkdtempSync(join(tmpdir(), 'cdpca-'))
// 用 127.0.0.1 起调试端口，但 APP 仍然用 localhost —— vite 默认两种都听
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--window-size=1600,1000',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
let failures = 0
const fail = (msg) => { failures++; console.log(`  ❌ ${msg}`) }
const ok = (msg) => console.log(`  ✅ ${msg}`)

const cleanup = () => {
  try { chrome.kill() } catch {}
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
}

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
  if (!target) { console.log('FAIL  Chrome 调试端口未就绪'); cleanup(); process.exit(1) }

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })

  let id = 0
  const pending = new Map()
  let consoleErrors = []
  let netErrors = []

  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params?.exceptionDetails
      consoleErrors.push(`[exception] ${d?.exception?.description || d?.text || 'unknown'}`)
    }
    if (m.method === 'Runtime.consoleAPICalled' && m.params?.type === 'error') {
      consoleErrors.push('[console.error] ' + (m.params.args || []).map(a => a.value ?? a.description ?? '').join(' '))
    }
    if (m.method === 'Network.responseReceived') {
      const { status, url } = m.params?.response || {}
      if (status >= 400) netErrors.push(`${status} ${url}`)
    }
  }

  const send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })

  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) throw new Error(r.result.exceptionDetails.text)
    return r.result?.result?.value
  }

  const waitFor = async (expr, timeoutMs = 40000) => {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      try { if (await evaluate(expr)) return true } catch {}
      await sleep(350)
    }
    return false
  }

  await send('Page.enable')
  await send('Runtime.enable')
  await send('Network.enable')

  // ---------- 1. 登录 ----------
  await send('Page.navigate', { url: `${APP}/login` })
  const formReady = await waitFor(`!!document.querySelector('.lr-submit') && document.querySelectorAll('input').length >= 3`, 45000)
  if (!formReady) {
    console.log('FAIL  登录页未渲染（后端或 vite 未就绪？）')
    cleanup(); process.exit(1)
  }

  const filled = await evaluate(`(async () => {
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
    const shown = (await (async () => {
      // W1.12：验证码改由服务端签发，按钮会先显示「加载中…」——必须等它变成一个
      // 4 位码再读，否则填进去的是「加载中…」，症状是"登录失败"，看着像账号坏了。
      for (let i = 0; i < 60; i++) {
        const t = (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
        if (/^[A-Z0-9]{4}$/.test(t)) return t
        await new Promise((r) => setTimeout(r, 200))
      }
      return (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
    })())
    const c = setVal('input[placeholder="验证码"]', shown)
    const btn = document.querySelector('.lr-submit')
    if (btn) btn.click()
    return { a, b, c, captcha: shown, clicked: !!btn }
  })()`)
  if (!filled?.a || !filled?.clicked) { console.log(`FAIL  登录表单没填上: ${JSON.stringify(filled)}`); cleanup(); process.exit(1) }

  let path = ''
  for (let i = 0; i < 60; i++) {
    await sleep(500)
    path = await evaluate('location.pathname')
    if (path && path.includes('/college-admin/')) break
  }
  if (!path || !path.includes('/college-admin/')) {
    const alert = await evaluate(`(document.querySelector('.el-alert__title')||{}).textContent || ''`)
    console.log(`FAIL  登录后未进入学院管理员端，当前=${path} 提示="${alert}"`)
    cleanup(); process.exit(1)
  }
  console.log(`PASS  登录成功，落地 ${path}`)

  // ---------- 2. 主题变量 ----------
  await sleep(SETTLE_MS)
  const vars = await evaluate(`(() => {
    const cs = getComputedStyle(document.documentElement)
    const get = (k) => (cs.getPropertyValue(k) || '').trim()
    return {
      themePrimary: get('--theme-primary'),
      elPrimary: get('--el-color-primary'),
      themeGradient: get('--theme-gradient'),
      role: localStorage.getItem('role'),
    }
  })()`)
  console.log(`      localStorage.role = ${JSON.stringify(vars.role)}`)
  console.log(`      --theme-primary   = ${JSON.stringify(vars.themePrimary)}`)
  console.log(`      --el-color-primary= ${JSON.stringify(vars.elPrimary)}`)
  console.log(`      --theme-gradient  = ${JSON.stringify(vars.themeGradient)}`)

  if (String(vars.role) !== '5') {
    fail(`localStorage.role 是 ${JSON.stringify(vars.role)}，不是 '5' —— 后面所有断言都不成立`)
  }
  const norm = (s) => String(s || '').trim().toUpperCase()
  if (norm(vars.themePrimary) !== WANT_PRIMARY) fail(`--theme-primary 应为 ${WANT_PRIMARY}，实际 ${JSON.stringify(vars.themePrimary)}`)
  else ok(`--theme-primary = ${WANT_PRIMARY}`)
  if (norm(vars.elPrimary) !== WANT_PRIMARY) fail(`--el-color-primary 应为 ${WANT_PRIMARY}，实际 ${JSON.stringify(vars.elPrimary)}`)
  else ok(`--el-color-primary = ${WANT_PRIMARY}`)

  // ---------- 3. 逐页 ----------
  mkdirSync(SHOT_DIR, { recursive: true })

  for (const route of ROUTES) {
    consoleErrors = []
    netErrors = []

    // SPA 内部跳转（不整页刷新），这样 --theme-* 之类的运行时状态保持真实
    await evaluate(`window.history.pushState({}, '', ${JSON.stringify(route)}); window.dispatchEvent(new PopStateEvent('popstate'))`)
    const arrived = await waitFor(`location.pathname === ${JSON.stringify(route)}`, 10000)
    if (!arrived) { fail(`${route} 路由未切换`); continue }
    // vue-router 的 pushState 劫持未必吃 popstate，兜底做一次整页导航
    const rendered = await waitFor(`(document.body.innerText||'').trim().length > 40`, 15000)
    if (!rendered) {
      await send('Page.navigate', { url: `${APP}${route}` })
      await waitFor(`(document.body.innerText||'').trim().length > 40`, 40000)
    }
    await sleep(SETTLE_MS)

    const snap = await evaluate(`(() => {
      const hero = document.querySelector('.cad-hero')
      const cs = hero ? getComputedStyle(hero).backgroundImage : ''
      const cs2 = hero ? getComputedStyle(hero).backgroundColor : ''
      return {
        path: location.pathname,
        bodyLen: (document.body.innerText || '').trim().length,
        head: (document.body.innerText || '').trim().slice(0, 60),
        heroFound: !!hero,
        heroBg: cs || cs2,
      }
    })()`)

    // 截图存档（失败时最有用）
    try {
      const shot = await send('Page.captureScreenshot', { format: 'png' })
      const b64 = shot.result?.data
      if (b64) writeFileSync(join(SHOT_DIR, `collegeadmin${route.replace(/\//g, '-')}.png`), Buffer.from(b64, 'base64'))
    } catch {}

    const realNet = netErrors.filter(t => !/favicon|\.map$/.test(t))
    if (snap.bodyLen < 40) fail(`${route} 渲染为空（白屏），当前路径 ${snap.path}`)
    else if (realNet.length) fail(`${route} 有 ${realNet.length} 条 4xx/5xx：${realNet.slice(0, 3).join(' | ')}`)
    else if (consoleErrors.length) fail(`${route} 有 ${consoleErrors.length} 条 console 异常：${consoleErrors.slice(0, 2).join(' | ')}`)
    else ok(`${route} 渲染正常（${snap.bodyLen} 字）「${snap.head.split('\n')[0].slice(0, 28)}」`)

    if (!snap.heroFound) {
      fail(`${route} 找不到 .cad-hero（选择器可能改了，本页的配色没验到）`)
    } else {
      const bad = FORBIDDEN_RGB.filter(([rgb]) => snap.heroBg.includes(rgb))
      if (bad.length) fail(`${route} hero 仍是旧色：${bad.map(b => b[1]).join('、')} —— ${snap.heroBg}`)
      else if (!snap.heroBg.includes('14, 165, 233')) fail(`${route} hero 渐变里没有天蓝 rgb(14,165,233)：${snap.heroBg}`)
      else ok(`${route} hero 已用主题渐变（含天蓝）`)
    }
  }

  console.log(failures ? `\nFAIL  共 ${failures} 项不通过` : '\nPASS  9 个学院管理员页面配色回归全部通过')
  cleanup()
  process.exit(failures ? 1 : 0)
}

main().catch((e) => { console.log(`FAIL  脚本异常: ${e.message}`); cleanup(); process.exit(1) })
