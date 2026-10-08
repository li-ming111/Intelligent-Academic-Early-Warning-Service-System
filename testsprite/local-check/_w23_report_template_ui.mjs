// W23：「报表生成」页能不能真的选到**自定义模板**，并且走对了端点。
//
// 为什么要有这条：这一轮的病是**承诺与实现不符** —— 页面上写着「自定义模板可以在生成报表时
// 使用」，但下拉里只列系统模板，而真正能吃自定义模板的 /admin/reports/generate-with-template
// **零调用方**。单元测试与 curl 只能证明后端那条路是通的，**证明不了下拉里真能选到它**
// —— 这正是本仓 el-checkbox/el-switch 那一族踩过的坑（接口、库、控制台全正常，只有 DOM 能抓到）。
//
// 断言（每条都能被打红）：
//   1. 下拉里出现**分组标题**「系统模板」与「自定义模板」；
//   2. 「自定义模板」组下出现**探针模板的名字**（预先由外部脚本建好）；
//   3. 选中它并点「生成报表」后，**发出的请求路径**是 /api/admin/reports/generate-with-template
//      （而不是 /api/admin/reports/generate —— 那会拿 template_id 去撞白名单 1-4）；
//   4. 提示语是 success 且不为空（证明在老后端上也不会炸：老后端不返回 rows/matched，
//      前端要能退回中性文案）。
//
// 用法: node _w23_report_template_ui.mjs <探针模板名> [管理员账号] [密码]
// 退出码 0=全绿, 1=有失败。模板由调用方预先创建、调用方负责删除（本脚本不建不删）。
import { spawn } from 'node:child_process'
import { mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const TPL_NAME = process.argv[2] || 'ZZW23探针模板'
const USER = process.argv[3] || 'admin'
const PASS = process.argv[4] || '123456'
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9700 + Math.floor(Math.random() * 200)
const profile = mkdtempSync(join(tmpdir(), 'cdp23-'))
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
  const reqs = [], jsErrors = []
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Fetch.requestPaused') {
      // 改写目标 + 补身份头。原请求头里的 Content-Type 要留着，否则后端解析 body 会 415。
      const hdrs = Object.entries(m.params.request.headers || {})
        .filter(([n]) => !['host', 'x-user-id', 'x-role', 'content-length'].includes(n.toLowerCase()))
        .map(([name, value]) => ({ name, value }))
      hdrs.push({ name: 'X-User-Id', value: '1' }, { name: 'X-Role', value: '4' })
      send('Fetch.continueRequest', {
        requestId: m.params.requestId,
        url: `http://127.0.0.1:${SPARE}/admin/reports/generate-with-template`,
        headers: hdrs,
      })
      return
    }
    if (m.method === 'Network.requestWillBeSent') {
      const r = m.params.request
      if (r.url.includes('/api/')) reqs.push({ method: r.method, url: r.url.replace(/^https?:\/\/[^/]+/, '').split('?')[0] })
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
  const grabMessage = async (maxMs = 3000) => {
    for (let t = 0; t < maxMs; t += 100) {
      await sleep(100)
      const msg = await evaluate(`(() => {
        const el = document.querySelector('.el-message')
        if (!el) return null
        const txt = el.querySelector('.el-message__content')?.textContent?.trim() || el.textContent.trim()
        return { txt, type: el.className.includes('el-message--error') ? 'error' : (el.className.includes('el-message--success') ? 'success' : 'other') }
      })()`)
      if (msg) return msg
    }
    return null
  }

  await send('Network.enable')
  await send('Runtime.enable')
  await send('Page.enable')

  // ---- 可选：把「用自定义模板生成报表」这条请求在**浏览器层**改写到备用实例（新代码）上 ----
  // 为什么需要：活库 8070 跑的是 W23 **之前**的 jar，响应里没有 rows/matched，
  // 于是浏览器只会走"老后端兜底文案"那一支 —— 那条路本来就该绿（向后兼容），
  // 但**新文案到底渲不渲染得出来**，在老后端上永远看不到。用 CDP Fetch 改写 URL，
  // 并补上身份头（直连不经网关，没人替你注入 X-User-Id/X-Role）。
  const SPARE = process.env.W23_SPARE_PORT
  if (SPARE) {
    await send('Fetch.enable', {
      patterns: [{ urlPattern: '*reports/generate-with-template*', requestStage: 'Request' }],
    })
  }

  // ---- 登录 ----
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
  if (!filled?.a || !filled?.b || !filled?.c || !filled?.clicked) throw new Error(`登录表单元素没找全: ${JSON.stringify(filled)}`)
  let path = ''
  for (let i = 0; i < 30; i++) { await sleep(500); path = await evaluate('location.pathname'); if (path && path !== '/login') break }
  if (!path || path === '/login') throw new Error(`登录失败（账号 ${USER}）`)
  console.log(`登录成功: ${path}`)

  let bad = 0
  const check = (ok, label, extra = '') => {
    console.log(`${ok ? '  [PASS]' : '  [FAIL]'} ${label}${extra ? '  ' + extra : ''}`)
    if (!ok) bad++
  }

  // ---- 进「数据导出」页 ----
  await send('Page.navigate', { url: `${APP}/admin/data-export` })
  await sleep(4000)
  // 等模板列表加载完（自定义模板来自 /admin/reports/templates/list）
  for (let i = 0; i < 30; i++) {
    await sleep(300)
    const n = await evaluate(`document.querySelectorAll('.tab-content, .el-tabs__item').length`)
    if (n > 0) break
  }

  // ---- 切到「报表生成」标签 ----
  const tabClicked = await evaluate(`(() => {
    const t = [...document.querySelectorAll('.el-tabs__item')].find(e => (e.textContent || '').includes('报表生成'))
    if (!t) return false
    t.click(); return true
  })()`)
  check(tabClicked, '页面上存在「报表生成」标签并能点击')
  await sleep(1200)

  // ---- 先填时间范围：不填的话点「生成报表」会被前端校验挡在"请选择时间范围"那一步，
  //      请求根本发不出去，后面的路由断言就没有输入可言（第一版探针就栽在这里）。
  //      两个都是单选日期，默认展开在当前月，点当月第一个可用日即可（start=end 也合法）。
  const pickDates = await evaluate(`(async () => {
    const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
    const pane = [...document.querySelectorAll('.el-tab-pane')].find(p => (p.textContent || '').includes('报表模板'))
    if (!pane) return 'no pane'
    const eds = [...pane.querySelectorAll('.el-date-editor')]
    if (eds.length < 2) return 'date editors found: ' + eds.length
    // 两个日期各有一个 popper；关着的那个也留在 DOM 里，所以必须挑**可见**的那一个，
    // 否则点到的是上一个（已隐藏）面板里的格子，点了等于没点。
    // 合成点击打不开 el-date-picker 的 popper（两个都停在 display:none），
    // 但**点面板里的日期格子照样能设上值** —— Vue 的事件处理器不看可见性。
    // 于是不跟"开没开"较劲，直接按**顺序**取第 i 个面板（popper 按模板创建顺序 teleport 到 body，
    // 第 i 个日期框对应第 i 个面板）。顺序若错，下面「时间范围实际值」那两条断言会立刻红
    // —— 判据自带自检，不靠"我觉得顺序是对的"。
    const diag = []
    for (let i = 0; i < eds.length; i++) {
      const inp = eds[i].querySelector('input')
      if (!inp) return 'no input at ' + i
      inp.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
      inp.click()
      await sleep(900)
      const panels = [...document.querySelectorAll('.el-picker-panel')]
      const panel = panels[i]
      const cell = panel && panel.querySelector('td.available:not(.prev-month):not(.next-month)')
      diag.push({ i, panels: panels.length, gotCell: !!cell })
      if (!cell) continue
      cell.click()
      await sleep(700)
    }
    return JSON.stringify(diag)
  })()`)
  console.log(`  填时间范围: ${pickDates}`)
  const dateVals = await evaluate(`(() => {
    const pane = [...document.querySelectorAll('.el-tab-pane')].find(p => (p.textContent || '').includes('报表模板'))
    return [...pane.querySelectorAll('.el-date-editor input')].map(i => i.value)
  })()`)
  console.log(`  时间范围实际值: ${JSON.stringify(dateVals)}`)
  // 判据只认**值真的落到两个输入框里**。别去认 pickDates 的返回串 ——
  // 第一版这里写的是 `pickDates === 'ok'`，而 pickDates 早就改成回 JSON 诊断了，
  // 于是"两个日期都填上了"这句在**值明明是对的**时候报红（判据比事实严，假阴性）。
  check(dateVals?.length === 2 && dateVals.every((v) => !!v), '两个日期都填上了', JSON.stringify(dateVals))

  // ---- 打开「报表模板」下拉 ----
  const opened = await evaluate(`(() => {
    const pane = [...document.querySelectorAll('.el-tab-pane')].find(p => (p.textContent || '').includes('报表模板'))
    if (!pane) return { ok: false, why: '找不到含「报表模板」的 tab-pane' }
    const sel = pane.querySelector('.el-select')
    if (!sel) return { ok: false, why: '找不到 .el-select' }
    const input = sel.querySelector('.el-select__wrapper, .el-input__wrapper') || sel
    input.click()
    return { ok: true }
  })()`)
  check(opened?.ok, '能打开「报表模板」下拉', opened?.ok ? '' : JSON.stringify(opened))
  await sleep(1000)

  // ---- 断言 1/2：分组标题与探针模板选项 ----
  const groups = await evaluate(`[...document.querySelectorAll('.el-select-group__title')].map(e => (e.textContent || '').trim())`)
  const options = await evaluate(`[...document.querySelectorAll('.el-select-dropdown__item')].map(e => (e.textContent || '').trim())`)
  console.log(`  下拉分组: ${JSON.stringify(groups)}`)
  console.log(`  下拉选项: ${JSON.stringify(options)}`)
  check(Array.isArray(groups) && groups.includes('系统模板'), '下拉里有「系统模板」分组')
  check(Array.isArray(groups) && groups.includes('自定义模板'), '下拉里有「自定义模板」分组（本轮新增）')
  const probeOption = (options || []).find((o) => o.includes(TPL_NAME))
  check(!!probeOption, `「自定义模板」组下能看到探针模板「${TPL_NAME}」`)

  if (!probeOption) {
    console.log('\n下拉里没有探针模板 —— 后面的路由断言跳过（否则会选错模板，结论无意义）')
  } else {
    // ---- 选中它 ----
    const picked = await evaluate(`(() => {
      const o = [...document.querySelectorAll('.el-select-dropdown__item')].find(e => (e.textContent || '').includes(${JSON.stringify(TPL_NAME)}))
      if (!o) return false
      o.click(); return true
    })()`)
    check(picked, '能选中探针模板')
    await sleep(600)

    // ---- 断言 3：点「生成报表」，看请求路径 ----
    const nBefore = reqs.length
    const clicked = await evaluate(`(() => {
      const pane = [...document.querySelectorAll('.el-tab-pane')].find(p => (p.textContent || '').includes('报表模板'))
      const btn = [...pane.querySelectorAll('button')].find(b => (b.textContent || '').trim() === '生成报表')
      if (!btn) return false
      btn.click(); return true
    })()`)
    check(clicked, '能点到「生成报表」按钮')

    const toast = await grabMessage(6000)
    const sent = reqs.slice(nBefore)
    console.log(`  发出的请求: ${JSON.stringify(sent)}`)
    console.log(`  ElMessage: ${JSON.stringify(toast)}`)
    check(sent.some((r) => r.url === '/api/admin/reports/generate-with-template'),
      '请求打到 /api/admin/reports/generate-with-template（而不是 /admin/reports/generate）')
    check(!sent.some((r) => r.url === '/api/admin/reports/generate'),
      '没有误打到系统模板端点 /admin/reports/generate')
    check(!!toast && toast.type === 'success', '提示语是成功的', toast ? JSON.stringify(toast) : '（没收到 ElMessage）')
    if (process.env.W23_SPARE_PORT) {
      // 走到这里说明请求确实被改写到了新代码实例上 —— 那响应里就有 rows，
      // 页面**必须**把真实行数说出来，而不是那句中性的「报表已生成」。
      check(!!toast && /行数据/.test(toast.txt || ''),
        '新代码：提示里带上了真实行数（不是兜底文案）', toast ? JSON.stringify(toast.txt) : '')
    }
  }

  if (jsErrors.length) console.log(`  JS 异常: ${JSON.stringify(jsErrors)}`)
  check(jsErrors.length === 0, '页面无 JS 异常')

  console.log(bad === 0 ? '\n全绿' : `\n有 ${bad} 条失败`)
  ws.close()
  return bad
}

let code = 1
try {
  code = await main()
} catch (e) {
  console.log(`探针自身出错: ${e.message}`)
  code = 1
} finally {
  try { chrome.kill() } catch {}
}
process.exit(code === 0 ? 0 : 1)
