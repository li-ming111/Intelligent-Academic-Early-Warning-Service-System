// 学生端「成绩申诉」(/student/appeals) 与「帮扶反馈」(/student/assistance-feedback) 两页
// 到底是"没有数据"还是"拿到了却没渲染"—— 必须同时断言两件事：
//   (a) 接口真的返回了数据（否则"空"是正常的，证明不了渲染 bug）
//   (b) 页面上显示的与接口一致
//
// 为什么这两页要验（2026-09-30 改的就是这几处）：
//   * 学生端的申诉页**真正可达的是 `views/Appeals.vue`**（路由 /student/appeals，
//     学生导航栏里有）。`views/student/AppealManagement.vue` 虽然被 router **import 了，
//     但没有任何一条路由用它** —— 那是个不可达的组件，同批修了、但要如实说清。
//   * `AppealManagement.vue` 的历史遗留：历史接口的列表键是 `list`、成功率接口的键是 `rate`、
//     状态是**数字**（0/1/2/3）而页面按 `'pending'` 这种字符串比 —— 三处都对不上。
//   * `AssistanceFeedback.vue` 统计卡原来读的是 `statistics.total`/`statistics.avgRating`，
//     后端根本没有这两个键（真实键：totalCount/averageScore/avgEffectiveness），
//     所以"已评价计划"恒 0、"平均评分"恒 0.0。现在还有一条：**零条评价时平均分显示 "—"**，
//     因为"0.0 分"读起来像"大家都打了 0 分"，而事实是"没有数据"。
//
// 用法: node student-pages-appeal-feedback.mjs [学号] [密码]
// 退出码 0 = 页面对得上接口, 1 = 对不上
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || '2023020616'
const PASS = process.argv[3] || '123456'
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9600 + Math.floor(Math.random() * 90)
const profile = mkdtempSync(join(tmpdir(), 'cdpab-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const fails = []

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
  const bodies = new Map()
  const readBody = async (reqId) => {
    if (bodies.has(reqId)) return bodies.get(reqId)
    const r = await send('Network.getResponseBody', { requestId: reqId })
    const raw = r.result?.body || ''
    bodies.set(reqId, raw)
    return raw
  }
  /** 只打印结构信息，不把整包响应刷屏 */
  const summarize = (raw) => {
    try {
      const j = JSON.parse(raw)
      const d = j?.data
      if (Array.isArray(d)) return `data 是数组，${d.length} 条`
      if (d && typeof d === 'object') {
        const keys = Object.keys(d)
        const pick = ['total', 'totalCount', 'averageScore', 'avgEffectiveness', 'rate', 'success', 'pending', 'approved', 'rejected', 'pages']
          .filter((k) => d[k] !== undefined).map((k) => `${k}=${JSON.stringify(d[k])}`)
        const lst = d.list !== undefined ? `list=${Array.isArray(d.list) ? d.list.length + ' 条' : typeof d.list}` : ''
        const err = d.error !== undefined ? `error=${JSON.stringify(d.error)}` : ''
        return `data 对象{${keys.slice(0, 8).join(',')}} ${[...pick, lst, err].filter(Boolean).join(' ')}`
      }
      return `data=${JSON.stringify(d)?.slice(0, 60)}`
    } catch { return `(非 JSON, ${raw.length} 字符)` }
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

  const dump = async (label, url, filter) => {
    api = []
    await send('Page.navigate', { url: `${APP}${url}` })
    await sleep(6000)
    const calls = api.filter((x) => filter.test(x.url))
    console.log(`===== ${label} (${url}) 的接口 =====`)
    for (const c of calls) console.log(`  [${c.status}] ${c.url}\n        ${summarize(await readBody(c.reqId))}`)
    if (!calls.length) console.log('  (这一页没有发出匹配的请求)')
    return calls
  }

  // ---------- 1) /student/appeals（可达的那一页）----------
  const appealCalls = await dump('成绩申诉', '/student/appeals', /\/appeals\//i)
  const listCall = appealCalls.find((c) => /\/appeals\/\d+\/list$/.test(c.url))
  const listRaw = listCall ? await readBody(listCall.reqId) : ''
  let listItems = NaN
  try { const d = JSON.parse(listRaw)?.data; if (Array.isArray(d)) listItems = d.length } catch {}

  const appealPage = await evaluate(`(() => {
    const vis = (e) => e && e.getBoundingClientRect().height > 0
    const rows = [...document.querySelectorAll('.el-table__row')].filter(vis)
    return {
      stats: [...document.querySelectorAll('.stat-value')].filter(vis).map((e) => e.textContent.trim()),
      labels: [...document.querySelectorAll('.stat-label')].filter(vis).map((e) => e.textContent.trim()),
      rowCount: rows.length,
      firstRow: rows[0]?.innerText?.replace(/\\n/g, ' | ').slice(0, 160) || '',
      emptyText: document.querySelector('.el-table__empty-text')?.textContent?.trim() || '',
      hasWithdraw: [...document.querySelectorAll('.action-btns button')].filter(vis).map((b) => b.textContent.trim()),
    }
  })()`)
  console.log('\n===== 成绩申诉页渲染 =====')
  console.log(`  统计卡  ${appealPage.labels.map((l, i) => `${l}=${appealPage.stats[i]}`).join('  ')}`)
  console.log(`  表格    ${appealPage.rowCount} 行  空态="${appealPage.emptyText}"`)
  if (appealPage.firstRow) console.log(`  首行    ${appealPage.firstRow}`)
  console.log(`  行内按钮 ${JSON.stringify(appealPage.hasWithdraw)}`)

  console.log('\n===== 判定①：成绩申诉 =====')
  if (Number.isNaN(listItems)) {
    console.log('  /appeals/{uid}/list 没抓到或不是数组，本轮证据不足')
  } else {
    console.log(`  接口返回 ${listItems} 条，页面渲染 ${appealPage.rowCount} 行`)
    if (listItems !== appealPage.rowCount) { fails.push('申诉列表条数与接口不一致'); console.log('  → 对不上') }
    else console.log('  → 对得上')
    // 状态列必须是中文（数字 0 被当成"待处理"显示说明映射对了；显示 "0" 说明还是数字键）
    if (listItems > 0) {
      const st = appealPage.firstRow
      const okStatus = /待处理|已通过|已驳回|已撤销/.test(st)
      console.log(`  首行状态是中文字面量: ${okStatus ? '是' : '否 → 状态映射没生效'}`)
      if (!okStatus) fails.push('申诉状态没有映射成中文')
      // 待处理的行必须有「撤销」按钮（row.status === 0 的分支）
      const pendingInApi = /"status"\s*:\s*0/.test(listRaw)
      if (pendingInApi && !appealPage.hasWithdraw.includes('撤销')) {
        fails.push('接口说该申诉是待处理，页面上却没有撤销按钮'); console.log('  → 待处理却没有撤销按钮')
      } else if (pendingInApi) console.log('  → 待处理行有撤销按钮')
    }
  }

  // ---------- 2) /student/assistance-feedback ----------
  const fbCalls = await dump('帮扶反馈', '/student/assistance-feedback', /\/evaluations\/|assistance/i)
  const statCall = fbCalls.find((c) => /\/evaluations\/\d+\/statistics$/.test(c.url))
  const statRaw = statCall ? await readBody(statCall.reqId) : ''
  let statObj = null
  try { statObj = JSON.parse(statRaw)?.data || null } catch {}

  const fbPage = await evaluate(`(() => {
    const vis = (e) => e && e.getBoundingClientRect().height > 0
    const nums = [...document.querySelectorAll('.stat-number')].filter(vis).map((e) => e.textContent.trim())
    const labels = [...document.querySelectorAll('.stat-label')].filter(vis).map((e) => e.textContent.trim())
    const rows = [...document.querySelectorAll('.el-table__row')].filter(vis)
    return {
      nums, labels, rowCount: rows.length,
      firstRow: rows[0]?.innerText?.replace(/\\n/g, ' | ').slice(0, 160) || '',
      body: document.body.innerText.replace(/\\s+/g, ' ').slice(0, 400),
    }
  })()`)
  console.log('\n===== 帮扶反馈页渲染 =====')
  console.log(`  统计卡  ${fbPage.labels.map((l, i) => `${l}=${fbPage.nums[i]}`).join('  ')}`)
  console.log(`  表格    ${fbPage.rowCount} 行`)
  if (fbPage.firstRow) console.log(`  首行    ${fbPage.firstRow}`)

  console.log('\n===== 判定②：帮扶反馈 =====')
  if (!statObj) {
    console.log('  没抓到 statistics 响应，本轮证据不足')
  } else {
    const idx = fbPage.labels.indexOf('已评价计划')
    const shown = idx >= 0 ? fbPage.nums[idx] : '(没找到卡片)'
    console.log(`  接口 totalCount=${statObj.totalCount}，页面「已评价计划」=${shown}`)
    if (String(statObj.totalCount) !== String(shown)) { fails.push('已评价计划数与接口不一致'); console.log('  → 对不上') }
    else console.log('  → 对得上')
    const avgIdx = fbPage.labels.indexOf('平均评分')
    const avgShown = avgIdx >= 0 ? fbPage.nums[avgIdx] : '(没找到卡片)'
    console.log(`  接口 averageScore=${statObj.averageScore}（totalCount=${statObj.totalCount}），页面「平均评分」=${avgShown}`)
    if (statObj.totalCount === 0 && avgShown !== '—') {
      fails.push('零条评价时平均分没有显示为 —（会被读成"大家都打了 0 分"）')
      console.log('  → 零条评价却显示了数字，这是"0.0 分"式的误导')
    } else if (statObj.totalCount === 0) {
      console.log('  → 零条评价显示 "—"（诚实空态）')
    }
  }

  console.log(`\n  未捕获异常  ${jsErrors.length ? [...new Set(jsErrors)].join(' | ') : '(无)'}`)
  const bad = [...appealCalls, ...fbCalls].filter((c) => c.status !== 200)
  console.log(`  非 200 接口  ${bad.length ? bad.map((c) => c.status + ' ' + c.url).join(', ') : '(无)'}`)
  if (bad.length) fails.push('有接口返回非 200')

  return fails
}

let result = ['脚本异常']
let closeBrowser = async () => {}
try { result = await main() } catch (e) { console.log(`\n脚本异常: ${e.message}`) }
// 直接 kill 浏览器会让 vite dev server 吃到未捕获的 socket ECONNRESET 而整个进程退出
// （2026-09-30 实测：跑一次脚本死一次，下一轮报"登录表单没找全"，看着像页面坏了）。
await closeBrowser()
await sleep(800)
try { chrome.kill() } catch {}
try { rmSync(profile, { recursive: true, force: true }) } catch {}
console.log(`\n===== 结论 =====`)
if (!result.length) console.log('PASS(0)  两页都对得上接口')
else { console.log(`FAIL(1)  ${result.length} 项对不上：`); for (const f of result) console.log(`  - ${f}`) }
process.exit(result.length ? 1 : 0)
