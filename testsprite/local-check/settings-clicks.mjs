// 设置页"点击级"验证：真的点保存按钮，断言两件事
//   (a) 确实发出了请求（方法 + 路径片段对得上）
//   (b) 弹出的 ElMessage 文案是成功文案
//
// 为什么两条都要：
//   只断言 (b)，`saveAccount()` 那种"只弹 ElMessage.success 不发请求"的空壳照样能过；
//   只断言 (a)，"请求发了但被 catch 吞成失败提示"照样能过（改密码原本就是这个病）。
//   这批 bug 全在 @click 路径上，冒烟脚本（只跑页面加载）永远发现不了。
//
// 用法: node settings-clicks.mjs <student|teacher|counselor> [用户名] [密码]
// 退出码 0=全部通过, 1=有失败
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

// 每一步：切到哪个 tab -> 点哪个按钮 -> 期望弹什么 -> 期望发出什么请求
// pw 存在时先按占位符填三个密码框
const ROLES = {
  counselor: {
    user: '1000000001', path: '/counselor/settings',
    steps: [
      { tab: '账户管理', button: '保存信息', expect: '个人信息已保存', method: 'PUT', url: '/api/users/update/' },
      { tab: '通知偏好', button: '保存设置', expect: '通知偏好已保存', method: 'PUT', url: '/settings' },
      { tab: '工作设置', button: '保存设置', expect: '工作设置已保存', method: 'PUT', url: '/settings' },
      // 密码改成同一个值（123456 -> 123456），可逆，不动账号名册
      { tab: '账户管理', button: '确认修改', expect: '密码修改成功', method: 'POST', url: '/api/auth/change-password', pw: { old: '123456', newPw: '123456' } },
    ],
  },
  teacher: {
    user: 'teacher001', path: '/teacher/settings',
    steps: [
      { tab: '账户管理', button: '保存信息', expect: '个人信息已保存', method: 'PUT', url: '/api/users/update/' },
      { tab: '通知偏好', button: '保存设置', expect: '通知偏好已保存', method: 'PUT', url: '/settings' },
      { tab: '工作设置', button: '保存设置', expect: '工作设置已保存', method: 'PUT', url: '/settings' },
      { tab: '账户管理', button: '确认修改', expect: '密码修改成功', method: 'POST', url: '/api/auth/change-password', pw: { old: '123456', newPw: '123456' } },
    ],
  },
  student: {
    user: '2023020616', path: '/student/settings',
    steps: [
      { tab: '账户管理', button: '保存修改', expect: '账户信息已保存', method: 'PUT', url: '/api/users/update/' },
      { tab: '隐私设置', button: '保存设置', expect: '隐私设置已保存', method: 'POST', url: '/privacy-level' },
      // 学生页前端卡"新密码至少8位"，而账号密码是 6 位的 123456。
      // 所以**改不回去**：把密码改成 8 位临时值后，界面上无法再用 6 位新密码提交。
      // 这一步只验"UI 改密码成功"，随后用 restore 走接口把它还原，
      // 免得跑一次脚本就把名册里的密码改掉。
      // （另一条结论：后端 `/auth/change-password` 根本不校验长度，8 位限制纯在前端。）
      { tab: '账户管理', button: '确认修改', expect: '密码已修改', method: 'POST', url: '/api/auth/change-password', pw: { old: '123456', newPw: 'ZzTemp2026' } },
      { restore: { userId: 27, from: 'ZzTemp2026', to: '123456', loginName: '2023020616' } },
    ],
  },
}

const ROLE = process.argv[2]
if (!ROLES[ROLE]) {
  console.error(`用法: node settings-clicks.mjs <${Object.keys(ROLES).join('|')}> [用户名] [密码]`)
  process.exit(2)
}
const CFG = ROLES[ROLE]
const USER = process.argv[3] || CFG.user
const PASS = process.argv[4] || '123456'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9700 + Math.floor(Math.random() * 100)
const profile = mkdtempSync(join(tmpdir(), 'cdps-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const done = (code, msg) => {
  console.log(`\n${code === 0 ? 'PASS' : 'FAIL'}  ${msg}`)
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
  let reqs = []          // 当前步骤内发出的 /api/ 请求 {method,url}
  let resps = []         // 当前步骤内返回的 /api/ 响应 {status,url}
  let jsErrors = []
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Network.requestWillBeSent') {
      const r = m.params.request
      if (r.url.includes('/api/')) reqs.push({ method: r.method, url: r.url.replace(/^https?:\/\/[^/]+/, '').split('?')[0] })
    } else if (m.method === 'Network.responseReceived') {
      const r = m.params.response
      if (r.url.includes('/api/')) resps.push({ status: r.status, url: r.url.replace(/^https?:\/\/[^/]+/, '').split('?')[0] })
    } else if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params.exceptionDetails
      jsErrors.push((d?.exception?.description || d?.text || '').split('\n')[0].slice(0, 160))
    }
  }
  const send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) return { __err: r.result.exceptionDetails.exception?.description }
    return r.result?.result?.value
  }
  const waitIdle = async (quietMs = 600, maxMs = 8000) => {
    let last = reqs.length + resps.length, quiet = 0
    for (let t = 0; t < maxMs; t += 200) {
      await sleep(200)
      const now = reqs.length + resps.length
      if (now === last) { quiet += 200; if (quiet >= quietMs) return } else { quiet = 0; last = now }
    }
  }

  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable')

  // ---- 登录 ----
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
  if (!filled?.a || !filled?.b || !filled?.c || !filled?.clicked) {
    return done(1, `登录表单元素没找全: ${JSON.stringify(filled)}`)
  }
  let path = ''
  for (let i = 0; i < 30; i++) {
    await sleep(500)
    path = await evaluate('location.pathname')
    if (path && path !== '/login') break
  }
  if (!path || path === '/login') return done(1, `登录失败（账号 ${USER}）`)
  console.log(`已登录 ${USER} → ${path}`)
  console.log(`打开 ${CFG.path}\n`)

  // ---- 进设置页，等首次加载的请求跑完 ----
  reqs = []; resps = []; jsErrors = []
  await send('Page.navigate', { url: APP + CFG.path })
  await sleep(2000)
  await waitIdle()
  const loadReqs = [...reqs], loadResps = [...resps]
  const loadBad = loadResps.filter((r) => r.status >= 400)
  console.log(`加载阶段请求 ${loadReqs.length} 个：`)
  for (const r of loadReqs) {
    const st = loadResps.filter((x) => x.url === r.url).map((x) => x.status)
    console.log(`    ${r.method.padEnd(5)} ${r.url.padEnd(34)} ${st.join(',') || '(无响应)'}`)
  }
  if (loadBad.length) console.log(`    ⚠ 有 ${loadBad.length} 个 4xx/5xx —— 表单会是空的，后续保存大概率失败`)
  console.log()

  // ---- 逐步点击 ----
  let bad = 0
  for (const [i, st] of CFG.steps.entries()) {
    reqs = []; resps = []; jsErrors = []
    // 还原步骤：不点界面，直接走接口把密码改回名册原值。
    // 光看"改回来了"没用——必须再用原密码真的登录一次，否则无法排除
    // "接口返回 200 但其实没写库"（本项目出现过 register 失败仍返回 code:200 的先例）。
    if (st.restore) {
      const r = await evaluate(`(async () => {
        const tok = localStorage.getItem('token')
        const h = { 'Content-Type': 'application/json', Authorization: 'Bearer ' + tok }
        const c = await fetch('/api/auth/change-password', { method: 'POST', headers: h,
          body: JSON.stringify({ userId: ${st.restore.userId}, oldPassword: ${JSON.stringify(st.restore.from)}, newPassword: ${JSON.stringify(st.restore.to)} }) })
        const cj = await c.json()
        const l = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: ${JSON.stringify(st.restore.loginName)}, password: ${JSON.stringify(st.restore.to)} }) })
        const lj = await l.json()
        return { code: cj.code, msg: cj.message, loginOk: !!(lj.data && lj.data.token) }
      })()`)
      const ok = r && r.code === 200 && r.loginOk
      if (!ok) bad++
      console.log(`${ok ? '✓' : '✗'} 步骤${i + 1} [还原] 把密码改回名册原值`)
      console.log(`      接口  ${r ? `code=${r.code} ${r.msg}` : '(调用失败)'}`)
      console.log(`      复核  用原密码重新登录：${r?.loginOk ? '成功' : '失败'}${ok ? '' : '   ← 两项必须都成立'}`)
      continue
    }
    // 填密码框（占位符定位，顺序即当前/新/确认）
    if (st.pw) {
      const ok = await evaluate(`(() => {
        const setVal = (el, val) => {
          Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, val)
          el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true }))
        }
        const inputs = [...document.querySelectorAll('input[type="password"]')]
        if (inputs.length < 3) return '密码框只有 ' + inputs.length + ' 个'
        setVal(inputs[0], ${JSON.stringify(st.pw.old)})
        setVal(inputs[1], ${JSON.stringify(st.pw.newPw)})
        setVal(inputs[2], ${JSON.stringify(st.pw.newPw)})
        return 'ok'
      })()`)
      if (ok !== 'ok') { console.log(`✗ 步骤${i + 1} 填密码失败: ${ok}`); bad++; continue }
    }
    const clicked = await evaluate(`(() => {
      const vis = (el) => !!(el && el.offsetParent !== null)
      // 先真的切 tab：按钮在未激活的 el-tab-pane 里虽然存在于 DOM，
      // 但直接 click 隐藏元素不算"用户点得到"，必须先把面板切出来。
      const tabs = [...document.querySelectorAll('.el-tabs__item')]
      const tab = tabs.find((t) => t.textContent.trim() === ${JSON.stringify(st.tab)})
      if (!tab) return '找不到 tab: ' + ${JSON.stringify(st.tab)}
      tab.click()
      // el-message 会叠加，先清掉旧的，免得读到上一步的残留
      document.querySelectorAll('.el-message').forEach((e) => e.remove())
      return 'tab-ok'
    })()`)
    if (clicked !== 'tab-ok') { console.log(`✗ 步骤${i + 1} ${clicked}`); bad++; continue }
    await sleep(400)
    const res = await evaluate(`(() => {
      const vis = (el) => !!(el && el.offsetParent !== null)
      const btns = [...document.querySelectorAll('.el-tab-pane button')].filter(vis)
      const btn = btns.find((b) => b.textContent.trim() === ${JSON.stringify(st.button)})
      if (!btn) return { err: '当前面板可见按钮: ' + btns.map((b) => b.textContent.trim()).join('/') }
      btn.click()
      return { ok: true }
    })()`)
    if (res?.err) { console.log(`✗ 步骤${i + 1} ${res.err}`); bad++; continue }

    // 等消息出现（ElMessage 默认 3s 自动消失，必须马上抓）
    let msg = null
    for (let t = 0; t < 2500; t += 100) {
      await sleep(100)
      msg = await evaluate(`(() => {
        const el = document.querySelector('.el-message')
        if (!el) return null
        const txt = el.querySelector('.el-message__content')?.textContent?.trim() || el.textContent.trim()
        return { txt, type: el.className.includes('el-message--error') ? 'error' : (el.className.includes('el-message--success') ? 'success' : 'other') }
      })()`)
      if (msg) break
    }
    await waitIdle(500, 4000)

    const hitReq = reqs.find((r) => r.method === st.method && r.url.includes(st.url))
    const hitResp = resps.filter((r) => r.url.includes(st.url))
    const okMsg = msg && msg.txt.includes(st.expect) && msg.type !== 'error'
    const okReq = !!hitReq
    const ok = okMsg && okReq
    if (!ok) bad++
    console.log(`${ok ? '✓' : '✗'} 步骤${i + 1} [${st.tab}] 点「${st.button}」`)
    console.log(`      提示  ${msg ? `${msg.type}: ${msg.txt}` : '(没有任何 ElMessage)'}${okMsg ? '' : `   ← 期望含「${st.expect}」且非 error`}`)
    console.log(`      请求  ${reqs.length ? reqs.map((r) => `${r.method} ${r.url}→${resps.filter((x) => x.url === r.url).map((x) => x.status).join('/') || '?'}`).join('  ') : '(没有发出任何 /api/ 请求)'}${okReq ? '' : `   ← 期望 ${st.method} *${st.url}*`}`)
    for (const e of new Set(jsErrors)) console.log(`      未捕获异常  ${e}`)
  }

  console.log(`\n通过 ${CFG.steps.length - bad}/${CFG.steps.length}`)
  if (bad) return done(1, `${bad} 个步骤失败`)
  return done(0, `全部 ${CFG.steps.length} 个步骤的提示与请求都符合预期`)
}

main().catch((e) => done(1, `脚本异常: ${e.message}`))
