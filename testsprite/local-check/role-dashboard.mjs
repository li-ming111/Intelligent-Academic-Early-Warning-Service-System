// 通用角色仪表盘核查：登录后跳转到指定页面，检查指定元素的文本。
// 用法:
//   node role-dashboard.mjs <用户名> <密码> <路径片段> <选择器> <必须包含> [必须不包含]
// 例:
//   node role-dashboard.mjs teacher001 123456 /teacher/dashboard .tdh-greeting 刘教授 教师
// 退出码 0=通过, 1=失败。不消耗 TestSprite credits。
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const [USER, PASS, PATH_FRAG, SELECTOR, MUST_HAVE, MUST_NOT = ''] = process.argv.slice(2)

if (!USER || !PASS || !PATH_FRAG || !SELECTOR || !MUST_HAVE) {
  console.error('用法: node role-dashboard.mjs <用户名> <密码> <路径片段> <选择器> <必须包含> [必须不包含]')
  process.exit(2)
}

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9400 + Math.floor(Math.random() * 100)   // 随机端口，避免并发撞车
const SETTLE_MS = 4000                                 // 等异步数据加载完，再看"稳定态"

const profile = mkdtempSync(join(tmpdir(), 'cdpr-'))
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

  // 关键：在点登录之前就装好 MutationObserver，记录目标元素文本的**每一次**变化。
  // 否则等轮询发现路由跳转时，首屏闪烁（~100ms）早已结束，采样必然漏掉。
  await evaluate(`(() => {
    window.__seq = []
    const sel = ${JSON.stringify(SELECTOR)}
    const record = () => {
      const el = document.querySelector(sel)
      const t = el ? (el.textContent || '').trim() : null
      if (t !== null && window.__seq[window.__seq.length - 1] !== t) window.__seq.push(t)
    }
    record()
    window.__obs = new MutationObserver(record)
    window.__obs.observe(document.documentElement, { childList: true, subtree: true, characterData: true })
    return true
  })()`)

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
    // 验证码已改为真实校验，必须读页面上显示的那个（.lr-captcha-btn 的文字）填回去。
    // 原先硬编码 '1234' 能过，是因为比对根本没做——那是被修的 bug，不是接口约定。
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
  if (!filled?.a || !filled?.clicked) return done(1, `登录表单元素没找全: ${JSON.stringify(filled)}`)

  let path = ''
  for (let i = 0; i < 40; i++) {
    await sleep(500)
    path = await evaluate('location.pathname')
    if (path && path.includes(PATH_FRAG)) break
  }
  // 登录后通常落到该角色的首页，子页面（如 /counselor/communication）要再跳一次。
  // 此时 token 已在 localStorage，直接整页导航即可，不必重新登录。
  // 代价：整页刷新会重置 window.__seq，所以子页面的"闪变"检测不再生效（本用例只查渲染）。
  if (!path || !path.includes(PATH_FRAG)) {
    if (path && !path.includes('/login')) {
      console.log(`      登录后落在 ${path}，再导航到 ${PATH_FRAG}`)
      await send('Page.navigate', { url: `${APP}${PATH_FRAG}` })
      for (let i = 0; i < 30; i++) {
        await sleep(500)
        path = await evaluate('location.pathname')
        if (path && path.includes(PATH_FRAG)) break
      }
    }
  }
  if (!path || !path.includes(PATH_FRAG)) {
    const alert = await evaluate(`(document.querySelector('.el-alert__title')||{}).textContent || ''`)
    return done(1, `未跳到 ${PATH_FRAG}, 当前路径=${path}, 页面提示="${alert}"`)
  }

  await sleep(SETTLE_MS)
  const seq = (await evaluate('window.__seq || []')) || []
  const settled = ((await evaluate(`(document.querySelector(${JSON.stringify(SELECTOR)})||{}).textContent || ''`)) || '').trim()
  const early = seq[0] || settled

  // 选择器若匹配多个元素，全部列出——单看第一个会漏掉"只有第一项对、后面全是初值"这种半坏状态
  const all = ((await evaluate(
    `Array.from(document.querySelectorAll(${JSON.stringify(SELECTOR)})).map(e => (e.textContent || '').trim())`
  )) || [])

  console.log(`      路径        = ${path}`)
  console.log(`      文本变化序列 = ${JSON.stringify(seq)}`)
  console.log(`      稳定态文本   = "${settled}"`)
  if (all.length > 1) console.log(`      全部 ${all.length} 个匹配 = ${JSON.stringify(all)}`)
  if (seq.length > 1) console.log(`      ⚠️ 首屏发生过闪变`)

  if (!settled) return done(1, `选择器 ${SELECTOR} 匹配不到元素，或它为空`)
  if (MUST_NOT && settled.includes(MUST_NOT)) return done(1, `稳定态文本 "${settled}" 含不该出现的「${MUST_NOT}」`)
  if (!settled.includes(MUST_HAVE)) return done(1, `稳定态文本 "${settled}" 未包含「${MUST_HAVE}」`)
  // 首屏出现过 MUST_NOT 必须判失败，不能只警告。
  // 否则"闪一下错误文案再被接口纠正"这类 bug 永远测不出来：
  // 稳定态是对的，只管稳定态就会 PASS —— 我最初就是这么漏掉它的。
  // 确实需要容忍瞬时占位符时（如 "--"、"0" 变真值），传 ALLOW_FLASH=1。
  if (MUST_NOT && early.includes(MUST_NOT) && process.env.ALLOW_FLASH !== '1') {
    return done(1, `首屏出现过「${MUST_NOT}」("${early}")，虽已纠正为 "${settled}"，但这是一次可见的闪变`)
  }
  if (MUST_NOT && early.includes(MUST_NOT)) {
    console.log(`      注意：首屏 "${early}" 含「${MUST_NOT}」但稳定态已纠正（ALLOW_FLASH=1 已放行）`)
  }
  return done(0, `稳定态文本 "${settled}" 符合预期`)
}

main().catch((e) => done(1, `脚本异常: ${e.message}`))
