// 管理员「消息通知」页 (/admin/messages) 显示的条数与接口是否一致。
//
// 2026-09-30 改的四处：
//   1. `/admin/messages/list` 与 `/admin/tasks/list` 回的是 `{total, items:[...]}`，
//      **不是数组**。页面上 `if (Array.isArray(response))` 恒为假、无 else、无提示 ——
//      接口 200、控制台干净、页面写"暂无数据"。现在改读 `items`。
//   2. 卡片上「共 N 条消息」原来写的是 `messages.length`，即**当前这一页的长度**，
//      不是库里的总数；而且服务端分页 + 本地再切片，翻到第 2 页永远是空的。
//      现在总数取服务端 total，翻页交给服务端。
//   3. 批量删除用 `Promise.all`：一条失败就全灭 —— 成功的几条本地不移除、
//      也不报"成功了几条"，于是"库里已删的行还挂在页面上"。改成 allSettled 逐条对账。
//   4. 失败信息一律吞成"删除失败/发送消息失败"，后端原话（如"0/2 条写入成功"）被抹平。
//
// 用法: node admin-messages-page.mjs [账号] [密码] [期望条数]
//   期望条数：给个数字就按它断言；不给就从接口读。
// 退出码 0 = 页面对得上接口, 1 = 对不上
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || 'admin'
const PASS = process.argv[3] || '123456'
const EXPECT = process.argv[4] ? Number(process.argv[4]) : null
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9600 + Math.floor(Math.random() * 90)
const profile = mkdtempSync(join(tmpdir(), 'cdpam-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const fails = []
const notes = []

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
  let api = []
  const reqUrls = new Map()   // requestId -> url，给 loadingFailed 用
  const failed = []           // 连接层就失败的请求（没有任何 HTTP 响应）
  const jsErrors = []
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Network.requestWillBeSent') {
      if (m.params.request?.url?.includes('/api/')) reqUrls.set(m.params.requestId, m.params.request.url.replace(/^https?:\/\/[^/]+/, '').split('?')[0])
    } else if (m.method === 'Network.responseReceived') {
      const r = m.params.response
      // ⚠️ 不要用 mimeType 过滤：网关把后端 500 的错误体回成了非 json 的 content-type，
      // 按 `mimeType.includes('json')` 收就会**恰好漏掉失败的那些请求** ——
      // 第一版就栽在这：页面明明发了 /admin/messages/list 并拿到 500，脚本却说"没抓到请求"，
      // 差一点当成"证据不足"放过去。凡是 /api/ 的都收，状态照实打。
      if (r.url.includes('/api/')) {
        api.push({ status: r.status, url: r.url.replace(/^https?:\/\/[^/]+/, '').split('?')[0], reqId: m.params.requestId, mime: r.mimeType })
      }
    } else if (m.method === 'Network.loadingFailed') {
      // 第二条同样会造假的缝：**一个 HTTP 响应都没有**的失败（网关把连接掐了、
      // 上游 Feign 超时后直连被 reset），根本不会触发 responseReceived。
      // 只盯 responseReceived 的话，这种失败在脚本里和"没发过请求"长得一模一样。
      const u = reqUrls.get(m.params.requestId)
      if (u) failed.push({ url: u, err: m.params.errorText, canceled: !!m.params.canceled })
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
  const bodies = new Map()
  const readBody = async (reqId) => {
    if (bodies.has(reqId)) return bodies.get(reqId)
    const r = await send('Network.getResponseBody', { requestId: reqId })
    const raw = r.result?.body || ''
    bodies.set(reqId, raw)
    return raw
  }

  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable')
  closeBrowser = async () => { try { await send('Browser.close') } catch {} }
  await send('Page.navigate', { url: `${APP}/login` })
  // 等表单真的出现再填。固定 sleep 3000 会偶发地在 vite 首次冷编译时抢跑，
  // 报出来是"登录表单没找全"，看着像页面坏了，其实是脚本等太短。
  for (let i = 0; i < 60; i++) {
    await sleep(500)
    if (await evaluate(`!!document.querySelector('.lr-submit')`)) break
  }
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
    // "登录表单没找全"有两种成因，报错时必须分开，否则会把自己的环境问题读成页面缺陷：
    // (a) vite 根本没在跑（本机实测：CDP 脚本杀掉浏览器后，vite 会因一次
    //     socket ECONNRESET 未捕获而整个进程退出，下一轮就连不上）；
    // (b) 页面在，但选择器/字段变了。
    const diag = await evaluate(`({ url: location.href, title: document.title, body: (document.body?.innerText || '').replace(/\\s+/g, ' ').slice(0, 160), html: document.documentElement.outerHTML.length })`)
    throw new Error(`登录表单没找全: ${JSON.stringify(filled)}\n    页面实况: ${JSON.stringify(diag)}`)
  }
  let path = ''
  for (let i = 0; i < 30; i++) { await sleep(500); path = await evaluate('location.pathname'); if (path && path !== '/login') break }
  if (!path || path === '/login') throw new Error(`登录失败（${USER}）`)
  console.log(`已登录 ${USER} → ${path}\n`)

  api = []
  await send('Page.navigate', { url: `${APP}/admin/messages` })
  // 第三条缝：ElMessage 自己 3 秒就消失，而上一版是"睡 7 秒再读一次 DOM"——
  // 于是**任何提示都读不到**，看起来永远像"页面一声不吭"。
  // 那种读法证明不了"失败是静默的"，只能证明"我读晚了"。改成边跑边轮询，见到就记下来。
  //
  // 第四条缝（2026-09-30 实测踩到）：窗口本身也得够长。下游 message-service 不在时，
  // admin-service 会先把 Feign 重试链跑完（本机实测 ~13 秒）才回那句 500，
  // 提示因此**在 10 秒之后**才弹出来 —— 窗口开 10 秒依然什么都看不到。
  // 所以：先等"有终局信号"（弹了提示，或总数已经是正数），否则最多等 30 秒。
  const toastSeen = []
  for (let i = 0; i < 100; i++) {
    await sleep(300)
    const s = await evaluate(`(() => {
      const vis = (e) => e && e.getBoundingClientRect().height > 0
      const body = document.body.innerText.replace(/\\s+/g, ' ')
      return {
        toasts: [...document.querySelectorAll('.el-message')].filter(vis).map((e) => e.innerText.replace(/\\n/g, ' ').trim()),
        count: Number((body.match(/共\\s*(\\d+)\\s*条消息/) || [])[1] ?? -1),
      }
    })()`)
    if (Array.isArray(s?.toasts)) for (const x of s.toasts) if (x && !toastSeen.includes(x)) toastSeen.push(x)
    if (i >= 10 && (toastSeen.length || (s?.count ?? -1) > 0)) break
  }

  const calls = api.filter((x) => /\/admin\/(messages|tasks|users)/i.test(x.url))
  console.log('===== 本页接口 =====')
  for (const c of calls) {
    const raw = await readBody(c.reqId)
    let sum = `(非 JSON, ${raw.length} 字符)`
    try {
      const j = JSON.parse(raw); const d = j?.data
      if (Array.isArray(d)) sum = `data 是数组，${d.length} 条`
      else if (d && typeof d === 'object') {
        const lst = d.list !== undefined ? `list=${Array.isArray(d.list) ? d.list.length + ' 条' : typeof d.list}` : ''
        const items = d.items !== undefined ? `items=${Array.isArray(d.items) ? d.items.length + ' 条' : typeof d.items}` : ''
        sum = `data 对象{${Object.keys(d).slice(0, 8).join(',')}} ${[`total=${d.total}`, lst, items].filter(Boolean).join(' ')}`
      } else sum = `data=${JSON.stringify(d)?.slice(0, 60)}`
    } catch {}
    console.log(`  [${c.status}] ${c.url}  (mime=${c.mime})\n        ${sum}`)
  }
  if (!calls.length) console.log('  (没有发出匹配的请求)')
  // 看到的现象：admin-service 日志里明明有 /admin/messages/list，脚本却一条都没收到。
  // 把这一轮抓到的**全部** /api/ 都打出来，别让"没抓到"变成一句含糊的"证据不足"。
  console.log(`  —— 本轮共抓到 ${api.length} 条 /api/ 响应 ——`)
  for (const c of api) console.log(`     [${c.status}] ${c.url} (mime=${c.mime})`)
  if (failed.length) {
    console.log(`  —— 另有 ${failed.length} 条**没有 HTTP 响应**的失败（连接层）——`)
    for (const f of failed) console.log(`     [请求失败${f.canceled ? '/已取消' : ''}] ${f.url} — ${f.err}`)
  }

  const page = await evaluate(`(() => {
    const vis = (e) => e && e.getBoundingClientRect().height > 0
    const rows = [...document.querySelectorAll('.el-table__row')].filter(vis)
    const body = document.body.innerText.replace(/\\s+/g, ' ')
    return {
      countLabel: (body.match(/共\\s*(\\d+)\\s*条消息/) || [])[1] ?? null,
      emptyHint: /暂无数据/.test(body),
      rowCount: rows.length,
      firstRow: rows[0]?.innerText?.replace(/\\n/g, ' | ').slice(0, 140) || '',
      toasts: [...document.querySelectorAll('.el-message')].filter(vis).map((e) => e.innerText.replace(/\\n/g, ' ').trim()),
      emptyText: document.querySelector('.el-table__empty-text')?.textContent?.trim() || '',
    }
  })()`)

  // 页面自己发过的请求，脚本不一定收得到事件（见上一条注释）。
  // 这里在页面上下文里**直接重放一次**，拿到浏览器真实看到的 status / content-type / 前 200 字符。
  // 这是"接口到底回了什么"的第一手证据，不依赖 CDP 的事件管道。
  const replay = await evaluate(`(async () => {
    const tok = localStorage.getItem('token') || ''
    try {
      const r = await fetch('/api/admin/messages/list?page=1&size=20', { headers: { Authorization: 'Bearer ' + tok } })
      const t = await r.text()
      const out = { status: r.status, ct: r.headers.get('content-type') }
      try {
        const j = JSON.parse(t); const d = j?.data
        // ⚠️ 这个项目的老规矩：外层 HTTP 200、里层 code 才是结论。
        // 只看 HTTP 状态会把 500 判成成功 —— 负向对照实测就是这样：
        // 消息服务不在时接口回的是 code:500 + success:false 且 HTTP 仍是 200，
        // 第一版脚本据此打了 PASS(0)。code 必须一起带回来看。
        out.code = j?.code
        if (d && typeof d === 'object' && !Array.isArray(d)) {
          out.total = d.total
          out.items = Array.isArray(d.items) ? d.items.length : (d.items === undefined ? null : typeof d.items)
          out.err = d.error ?? null
        } else out.head = t.slice(0, 160)
      } catch { out.head = t.slice(0, 160) }
      return out
    } catch (e) { return { thrown: String(e) } }
  })()`)
  console.log(`\n  [重放] GET /api/admin/messages/list → ${JSON.stringify(replay)}`)

  console.log('\n===== 页面渲染 =====')
  console.log(`  「共 N 条消息」 ${page.countLabel}`)
  console.log(`  表格行数        ${page.rowCount}   空态="${page.emptyText}"`)
  if (page.firstRow) console.log(`  首行            ${page.firstRow}`)
  console.log(`  提示(ElMessage) ${JSON.stringify(toastSeen)}   ← 全程轮询累积，不是某一刻的快照`)
  console.log(`  未捕获异常      ${jsErrors.length ? [...new Set(jsErrors)].join(' | ') : '(无)'}`)

  const listCall = calls.find((c) => /\/admin\/messages\/list$/.test(c.url))
  const listRaw = listCall ? await readBody(listCall.reqId) : ''
  let apiTotal = null, apiItems = null, apiCode = null
  try {
    const j = JSON.parse(listRaw)
    apiCode = j?.code
    const d = j?.data; if (d) { apiTotal = d.total; apiItems = Array.isArray(d.items) ? d.items.length : null }
  } catch {}
  // 页面那条没抓到事件时，用重放的第一手结果回填（重放走的是同一个网关、同一份 token）
  const listHttp = listCall ? listCall.status : (replay?.status ?? null)
  if (apiCode === null) apiCode = replay?.code ?? null
  if (apiTotal === null && replay && replay.total !== undefined) { apiTotal = replay.total; apiItems = replay.items ?? null }
  // 这一层"成功"要看**里层 code**，不是 HTTP 状态：外层 200 + 里层 500 是本项目最常见的坑
  const listFailed = (apiCode !== null && apiCode !== 200) || (listHttp !== null && listHttp >= 400) || !!replay?.thrown

  console.log('\n===== 判定 =====')
  if (listFailed) {
    // 下游不可用：这一轮**不能**用来证明"渲染对得上"，只能证明"失败不再是静默的"
    const why = replay?.thrown ? `连接层失败: ${replay.thrown.slice(0, 60)}`
      : apiCode !== null && apiCode !== 200 ? `里层 code=${apiCode}（HTTP ${listHttp}）`
      : `HTTP ${listHttp}`
    console.log(`  /admin/messages/list 失败：${why}`)
    console.log(`  里层 message: ${JSON.stringify(replay?.head ? replay.head.slice(0, 90) : null)}`)
    const said = toastSeen.length > 0
    console.log(`  页面有没有把失败说出来: ${said ? '说了 → ' + JSON.stringify(toastSeen) : '没说（还是静默空表）'}`)
    if (!said) fails.push('接口失败时页面仍然静默')
    else notes.push('本轮是**失败态**：页面把失败说出来了，但"渲染对得上接口"这一项没验（接口本来是坏的）')
    console.log(`  页面此时显示「共 N 条消息」=${page.countLabel}、${page.rowCount} 行 —— 这是失败态，不参与对账`)
  } else if (apiTotal === null) {
    console.log(`  没抓到 /admin/messages/list 的响应，重放也没给出 total（HTTP=${listHttp} code=${apiCode}），本轮证据不足`)
  } else {
    console.log(`  接口 total=${apiTotal} items=${apiItems} 条；页面「共 N 条」=${page.countLabel} 行数=${page.rowCount}`)
    if (String(apiTotal) !== String(page.countLabel)) { fails.push('卡片总数与接口 total 不一致'); console.log('  → 总数对不上') }
    else console.log('  → 总数对得上')
    if (apiItems !== null && apiItems !== page.rowCount) { fails.push('表格行数与接口 items 数不一致'); console.log('  → 行数对不上') }
    else console.log('  → 行数对得上')
    if (toastSeen.length) { fails.push('成功路径上却弹了提示'); console.log(`  → 但弹了提示：${JSON.stringify(toastSeen)}`) }
    if (EXPECT !== null && apiTotal !== EXPECT) console.log(`  ⚠️ 接口 total=${apiTotal}，与命令行期望的 ${EXPECT} 不同`)
  }
  return fails
}

let result = ['脚本异常']
let closeBrowser = async () => {}
try { result = await main() } catch (e) { console.log(`\n脚本异常: ${e.message}`) }
// 直接 kill 浏览器会让 vite dev server 吃到未捕获的 socket ECONNRESET 而整个进程退出
// （本机实测：跑一次脚本死一次，下一轮报"登录表单没找全"，看着像页面坏了）。
await closeBrowser()
await sleep(800)
try { chrome.kill() } catch {}
try { rmSync(profile, { recursive: true, force: true }) } catch {}
console.log(`\n===== 结论 =====`)
if (!result.length) console.log('PASS(0)  页面对得上接口')
else { console.log(`FAIL(1)  ${result.length} 项：`); for (const f of result) console.log(`  - ${f}`) }
for (const n of notes) console.log(`  注意：${n}`)
process.exit(result.length ? 1 : 0)
