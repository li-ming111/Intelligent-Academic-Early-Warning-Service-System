// 【改之前的历史证据】学生端旧「消息中心」页 `views/Messages.vue` 的"标记已读"和"发送消息"
// 到底有没有真的发生。该页已于 2026-10-01 第二十四轮**删除**（与孤儿页 NotificationCenter.vue
// 收敛成一个），所以现在跑它读不到 `.msg-card`、判定没有意义 —— 保留它是因为
// **它是"改之前三重全断"那条结论的证据**：改之前实测
//   点开消息 → 0 个已读请求、未读数 2→2；发送消息 → GET /api/teachers/list 403、
//   POST /students/messages 403。要看现在的行为请跑 student-prefs-checkbox.mjs /
//   student-notify-empty.mjs（两者的导航地址都已指向 /student/messages）。
//
// 原文：
//
// 起因（2026-10-01 第二十四轮）：读代码发现该页有三处对不上后端：
//   ① `viewDetail()` 判的是 `m.status === 0`，而后端 `/students/messages/{uid}`
//      走 notificationToMap，返回的键是 **isRead**、根本没有 status
//      ⇒ 判据恒假，`markRead()` 一次都不会被调用（页面看、控制台也看，都看不出来）。
//   ② 就算调到了，`markRead()` 打的是 `POST /students/messages/{id}/mark-read`，
//      这个 handler 在 student-service 里**不存在**（实测 403）。
//   ③ 「发送消息」打 `POST /students/messages` —— 同样不存在，且**全仓没有第二个实现**
//      （feedback 表没有 student_id 列、notifications 只有 counselor-service 一个写入方），
//      所以这条链无论怎么写都没有收件人能看见。
//
// 判据必须是**行为**而不是渲染：页面看起来"列表正常"完全不能说明已读生效。
//   本脚本按 (a) 接口确实给了 2 条未读 → (b) 点开一条后不发任何请求、未读数不变
//   → (c) 「发送消息」提交后拿到失败提示 ⇒ 三条合起来才证明"点了没反应/点了报错"。
//
// 用法: node student-messages-probe.mjs [学号] [密码]   退出码 0=与预期一致, 1=不一致
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || '2023020616'
const PASS = process.argv[3] || '123456'
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9700 + Math.floor(Math.random() * 90)
const profile = mkdtempSync(join(tmpdir(), 'cdpm-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

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
  const reqs = []            // { method, url, status, reqId }
  const jsErrors = []
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Network.requestWillBeSent') {
      const r = m.params.request
      if (r.url.includes('/api/')) reqs.push({ method: r.method, url: r.url.replace(/^https?:\/\/[^/]+/, '').split('?')[0], reqId: m.params.requestId, status: null })
    } else if (m.method === 'Network.responseReceived') {
      const hit = reqs.find((x) => x.reqId === m.params.requestId)
      if (hit) hit.status = m.params.response.status
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
  const closeBrowser = async () => { try { await send('Browser.close') } catch {} }

  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable')

  await send('Page.navigate', { url: `${APP}/login` })
  await sleep(3000)
  const filled = await evaluate(`(async () => {
    const setVal = (sel, val) => {
      const el = document.querySelector(sel); if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
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
    const a = setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(USER)})
    const b = setVal('input[placeholder="请输入密码"]', ${JSON.stringify(PASS)})
    const c = setVal('input[placeholder="验证码"]', shown)
    const btn = document.querySelector('.lr-submit'); if (btn) btn.click()
    return { a, b, c, clicked: !!btn }
  })()`)
  if (!filled?.a || !filled?.b || !filled?.c || !filled?.clicked) throw new Error(`登录表单没找全: ${JSON.stringify(filled)}`)
  let path = ''
  for (let i = 0; i < 30; i++) { await sleep(500); path = await evaluate('location.pathname'); if (path && path !== '/login') break }
  if (!path || path === '/login') throw new Error(`登录失败（${USER}）`)
  console.log(`已登录 ${USER} → ${path}\n`)

  // 页面右下/顶部可能有 AI 助手浮层，读 toast 前先只取 .el-message
  const readToasts = () => evaluate(`[...document.querySelectorAll('.el-message__content')].map(e => e.textContent.trim())`)
  const readUnread = () => evaluate(`(() => {
    const body = document.body.innerText.replace(/\\s+/g, ' ')
    const m = body.match(/未读[:：]?\\s*(\\d+)\\s*条/) || body.match(/(\\d+)\\s*条未读/)
    return m ? Number(m[1]) : null
  })()`)

  reqs.length = 0
  await send('Page.navigate', { url: `${APP}/student/messages` })
  await sleep(5000)
  const before = { unread: await readUnread() }
  const listCall = reqs.find((r) => /\/api\/students\/messages\/\d+$/.test(r.url))
  const cards = await evaluate(`document.querySelectorAll('.msg-card').length`)
  console.log('===== 打开 /student/messages =====')
  console.log(`  列表请求        ${listCall ? `[${listCall.status}] ${listCall.url}` : '(没发)'}`)
  console.log(`  渲染消息卡片    ${cards} 张`)
  console.log(`  页面未读标签    ${before.unread}`)

  // (b) 点开第一条 → 看有没有发起标记已读的请求
  reqs.length = 0
  const opened = await evaluate(`(() => {
    const c = document.querySelector('.msg-card'); if (!c) return false
    c.click(); return true
  })()`)
  await sleep(2500)
  const readReqs = reqs.filter((r) => /mark-read|mark-batch-read/.test(r.url))
  const afterClick = await readUnread()
  console.log('\n===== 点开第一条消息（应当触发"标记已读"）=====')
  console.log(`  点到卡片        ${opened}`)
  console.log(`  发出的已读请求  ${readReqs.length ? readReqs.map((r) => `${r.method} [${r.status}] ${r.url}`).join(' | ') : '(一个都没有)'}`)
  console.log(`  未读标签        ${afterClick}   (点之前 ${before.unread})`)

  // (c) 发送消息
  reqs.length = 0
  await evaluate(`document.querySelectorAll('.el-dialog').forEach(d => d.style.display='none')`)  // 关掉详情弹窗
  const sendOpened = await evaluate(`(() => {
    const b = [...document.querySelectorAll('.mgh-btn')].find(e => e.textContent.includes('发送消息'))
    if (!b) return false
    b.click(); return true
  })()`)
  await sleep(2000)
  // 选教师：el-select 是自绘的（下拉 teleport 到 body），必须 mousedown→mouseup→click 三步，
  // 只发一个 click 时 Element Plus 不会把选中值写回 v-model（第一版就栽在这：提示"请选择教师"）。
  const picked = await evaluate(`(async () => {
    const sel = document.querySelector('.el-dialog .el-select'); if (!sel) return 'no-select'
    const inp = sel.querySelector('input')
    inp.dispatchEvent(new MouseEvent('mousedown', {bubbles:true}))
    inp.click()
    await new Promise(r => setTimeout(r, 1500))
    const opts = [...document.querySelectorAll('.el-select-dropdown__item')].filter(o => o.getBoundingClientRect().height > 0)
    if (!opts.length) return 'no-option'
    const opt = opts[0]
    for (const t of ['mousedown','mouseup','click']) opt.dispatchEvent(new MouseEvent(t, {bubbles:true}))
    await new Promise(r => setTimeout(r, 600))
    return 'ok:' + opts.length + '项/选中=' + (sel.innerText || '').trim().slice(0,20)
  })()`)
  await sleep(800)
  await evaluate(`(() => {
    const ta = document.querySelector('.el-dialog textarea')
    if (ta) { Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set.call(ta, 'probe message')
      ta.dispatchEvent(new Event('input', {bubbles:true})) }
  })()`)
  await sleep(300)
  const submitted = await evaluate(`(() => {
    const b = [...document.querySelectorAll('.el-dialog__footer button')].find(e => e.textContent.includes('发送'))
    if (!b) return false
    b.click(); return true
  })()`)
  await sleep(3000)
  const toasts = await readToasts()
  console.log('\n===== 发送消息 =====')
  console.log(`  打开弹窗/选教师/提交   ${sendOpened} / ${picked} / ${submitted}`)
  for (const r of reqs) console.log(`  ${r.method} [${r.status}] ${r.url}`)
  console.log(`  提示文案        ${JSON.stringify(toasts)}`)
  // 下拉为什么是空的：直接用页面自己的 token 打一次网关，看老师列表这个接口学生能不能拿
  const teachersCall = await evaluate(`(async () => {
    const t = localStorage.getItem('token')
    const r = await fetch('/api/teachers/list', { headers: { Authorization: 'Bearer ' + t } })
    let body = ''; try { body = (await r.text()).slice(0, 120) } catch {}
    return 'HTTP ' + r.status + ' /api/teachers/list -> ' + body
  })()`)
  console.log(`  教师列表接口    ${teachersCall}`)
  console.log(`  未捕获异常      ${jsErrors.length ? [...new Set(jsErrors)].join(' | ') : '(无)'}`)

  console.log('\n===== 汇总（这是"改之前"的现状证据）=====')
  console.log(`  点开消息后是否发起已读请求: ${readReqs.length > 0 ? '是' : '否'}`)
  console.log(`  未读数是否变化:             ${before.unread} → ${afterClick}`)
  console.log(`  发送消息提示:               ${toasts.find(t => /失败|成功/.test(t)) || '(无)'}`)
  return 0
}

let code = 1
let closeBrowser = async () => {}
try { code = (await main()) ? 1 : 0 } catch (e) { console.log(`\n脚本异常: ${e.message}`) }
await closeBrowser()
await sleep(800)
try { chrome.kill() } catch {}
try { rmSync(profile, { recursive: true, force: true }) } catch {}
process.exit(code)
