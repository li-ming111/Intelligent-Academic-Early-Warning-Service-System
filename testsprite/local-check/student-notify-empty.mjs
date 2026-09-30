// 学生「通知中心」到底是"没有通知"还是"拿到了却没渲染"—— 这条要能区分开。
//
// 起因：读代码发现 `views/student/NotificationCenter.vue` 里 11 处判的是
//   if (response.data?.code === 200) { this.xxx = response.data.data || [] }
// 而 `api/client.js` 的拦截器在普通端点上**已经把内层 data 解包返回了**，
// 所以 `response.data` 恒为 undefined → 分支恒假 → **赋值语句一次都不执行**。
// 这类 bug 页面不崩、不报错、冒烟只看渲染也照过，表现为"空态"。
//
// 所以本脚本必须**同时**断言两件事，缺一不可：
//   (a) 接口真的返回了数据（否则"空"是正常的，证明不了 bug）
//   (b) 页面上一个都没有
// 只断言 (b) 会被"帐号本来就没通知"满足，只断言 (a) 与页面无关。
//
// 用法: node student-notify-empty.mjs [学号] [密码]     退出码 0=页面对得上接口, 1=对不上
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || '2023020616'
const PASS = process.argv[3] || '123456'
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9600 + Math.floor(Math.random() * 90)
const profile = mkdtempSync(join(tmpdir(), 'cdpn-'))
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
  const api = []            // { method, url, status, body 摘要 }
  const jsErrors = []
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Network.responseReceived') {
      const r = m.params.response
      if (r.url.includes('/api/') && r.mimeType?.includes('json')) {
        api.push({ status: r.status, url: r.url.replace(/^https?:\/\/[^/]+/, '').split('?')[0], reqId: m.params.requestId })
      }
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
  // 读响应体：只留"条数"这类结构信息，不打印整包
  const bodies = new Map()
  const readBody = async (reqId) => {
    if (bodies.has(reqId)) return bodies.get(reqId)
    const r = await send('Network.getResponseBody', { requestId: reqId })
    const raw = r.result?.body || ''
    let summary = ''
    try {
      const j = JSON.parse(raw)
      const d = j?.data
      if (Array.isArray(d)) summary = `data 是数组，${d.length} 条`
      else if (d && typeof d === 'object') {
        // 这几个键是判据本身，必须打出来，不能只打印"有这些键"
        const seg = d.data !== undefined ? `data.data=${Array.isArray(d.data) ? d.data.length + ' 条' : typeof d.data}` : ''
        const tot = d.total !== undefined ? `total=${d.total}` : ''
        const lst = d.list !== undefined ? `list=${Array.isArray(d.list) ? d.list.length + ' 条' : typeof d.list}` : ''
        const cnt = d.unreadCount !== undefined ? `unreadCount=${d.unreadCount}` : ''
        summary = `data 对象{${Object.keys(d).slice(0, 8).join(',')}} ${[seg, tot, lst, cnt].filter(Boolean).join(' ')}`
      } else summary = `data=${JSON.stringify(d)?.slice(0, 60)}`
    } catch { summary = `(非 JSON, ${raw.length} 字符)` }
    bodies.set(reqId, summary)
    return summary
  }

  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable')
  closeBrowser = async () => { try { await send('Browser.close') } catch {} }

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
  if (!filled?.a || !filled?.b || !filled?.c || !filled?.clicked) throw new Error(`登录表单没找全: ${JSON.stringify(filled)}`)
  let path = ''
  for (let i = 0; i < 30; i++) { await sleep(500); path = await evaluate('location.pathname'); if (path && path !== '/login') break }
  if (!path || path === '/login') throw new Error(`登录失败（${USER}）`)
  console.log(`已登录 ${USER} → ${path}\n`)

  api.length = 0
  await send('Page.navigate', { url: `${APP}/student/notification-center` })
  await sleep(5000)

  // 读页面当前状态。判"空"不能靠 .el-empty —— Element 的表格空态是
  // `.el-table__empty-text`（"暂无数据"）+ 分页栏写"共 0 条"，`.el-empty` 一个都没有，
  // 用 .el-empty 计数会把"确实是空的"读成 0，得出相反结论（第一版就栽在这）。
  const readPage = () => evaluate(`(() => {
    const vis = (e) => e && e.getBoundingClientRect().height > 0
    const rows = [...document.querySelectorAll('.el-table__row')].filter(vis)
    const tabs = [...document.querySelectorAll('.el-tabs__item')].map((t) => ({
      text: t.textContent.trim(), active: t.className.includes('is-active')
    }))
    const body = document.body.innerText.replace(/\\s+/g, ' ')
    const unreadLabel = (body.match(/未读[:：]\\s*(\\d+)/) || [])[1]
    const totalLabel = (body.match(/共\\s*(\\d+)\\s*条/) || [])[1]
    return {
      rowCount: rows.length,
      firstRow: rows[0]?.innerText?.replace(/\\n/g, ' | ').slice(0, 140) || '',
      emptyText: document.querySelector('.el-table__empty-text')?.textContent?.trim() || '',
      tabs,
      unreadLabel, totalLabel,
      badges: [...document.querySelectorAll('.el-badge__content')].filter(vis).map((b) => b.textContent.trim()),
    }
  })()`)
  const page = await readPage()
  // 默认可能停在「全部」；「未读」是接口明确说有 2 条的那个 tab，必须点过去再读一次
  const clickedUnread = await evaluate(`(() => {
    const t = [...document.querySelectorAll('.el-tabs__item')].find((e) => e.textContent.trim() === '未读')
    if (!t) return '没有「未读」这个 tab：' + [...document.querySelectorAll('.el-tabs__item')].map((e) => e.textContent.trim()).join('/')
    t.click(); return 'ok'
  })()`)
  if (clickedUnread === 'ok') await sleep(2500)
  const unreadTab = clickedUnread === 'ok' ? await readPage() : null

  const notifyCalls = api.filter((x) => /notification|preference|unread/i.test(x.url))
  console.log('===== 本次页面发出的通知相关请求 =====')
  for (const c of notifyCalls) {
    const s = await readBody(c.reqId)
    console.log(`  [${c.status}] ${c.url}`)
    console.log(`        响应 ${s}`)
  }
  const show = (label, p) => {
    console.log(`  ${label.padEnd(10)}行数=${p.rowCount} 空态文本=${JSON.stringify(p.emptyText)} 未读标签=${p.unreadLabel} 共N条=${p.totalLabel} 角标=${JSON.stringify(p.badges)}`)
    console.log(`  ${''.padEnd(10)}Tab=${p.tabs.map((t) => (t.active ? '[' + t.text + ']' : t.text)).join(' / ')}`)
    if (p.firstRow) console.log(`  ${''.padEnd(10)}首行=${p.firstRow}`)
  }
  console.log('\n===== 页面渲染 =====')
  show('默认tab', page)
  if (unreadTab) show('点「未读」后', unreadTab)
  else console.log(`  点「未读」失败: ${clickedUnread}`)
  console.log(`  未捕获异常  ${jsErrors.length ? [...new Set(jsErrors)].join(' | ') : '(无)'}`)

  // (a) 接口有没有给数据。**未读列表返回的条数**才是判据本身，用正则去猜太脆，
  // 直接看那条请求的响应摘要里报了几条。
  const unreadListCall = notifyCalls.find((c) => /notification-center\/\d+\/unread$/.test(c.url))
  const unreadSummary = unreadListCall ? (bodies.get(unreadListCall.reqId) || '') : ''
  const unreadItems = Number((unreadSummary.match(/数组，(\d+) 条/) || [])[1] ?? NaN)
  const okStatus = notifyCalls.every((c) => c.status === 200)

  console.log('\n===== 判定 =====')
  console.log(`  接口状态        ${okStatus ? '全部 200' : '有非 200：' + notifyCalls.filter((c) => c.status !== 200).map((c) => c.status + ' ' + c.url).join(', ')}`)
  console.log(`  未读接口返回    ${unreadItems} 条   (${unreadSummary})`)
  console.log(`  未读 tab 渲染   ${unreadTab ? unreadTab.rowCount + ' 行' : '(没点到)'}`)
  console.log(`  页面「未读」标  ${unreadTab?.unreadLabel ?? page.unreadLabel}   (接口说 ${unreadItems} 条)`)
  let verdict = 0
  if (!Number.isNaN(unreadItems) && unreadItems > 0) {
    const rendered = unreadTab ? unreadTab.rowCount : page.rowCount
    if (rendered === 0) {
      verdict = 1
      console.log('  → 未读接口返回了数据，页面却是空的：**渲染被死分支挡住**，不是"本来就没通知"')
    } else {
      console.log('  → 页面把未读渲染出来了（此页当前正常）')
    }
  } else {
    console.log('  → 未读接口本身就没数据，不能据此判定渲染有问题（此轮证据不足）')
  }
  return verdict
}

let code = 1
let closeBrowser = async () => {}
try { code = (await main()) ? 1 : 0 } catch (e) { console.log(`\n脚本异常: ${e.message}`) }
// 直接 kill 浏览器会让 vite dev server 吃到未捕获的 socket ECONNRESET 而整个进程退出
// （2026-09-30 实测：跑一次脚本死一次，下一轮报"登录表单没找全"，看着像页面坏了）。
await closeBrowser()
await sleep(800)
try { chrome.kill() } catch {}
try { rmSync(profile, { recursive: true, force: true }) } catch {}
console.log(code === 0 ? '\nPASS(0)  页面对得上接口' : '\nFAIL(1)  页面与接口对不上（见上）')
process.exit(code)
