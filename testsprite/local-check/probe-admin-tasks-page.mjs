// 管理员「消息通知」页 →「任务管理」tab 的端到端点击级探针（W1.11，2026-10-02）。
//
// 为什么要有它：这四个端点此前是**整组桩** —— `getTasksList` 写死空 ArrayList，
// 创建/改状态/删除只 `log.info` 一行就回 "…successfully"。而这个 tab 的前端
// **早就写好了**（创建表单 / 四个统计卡 / 三个筛选按钮 / 行内改状态 / 单条+批量删除 /
// 分页），对着一个永远空的列表渲染 ⇒ 页面看着"能用"，点什么都"成功"，什么都不发生。
// 后端 HTTP 探针（probe-admin-tasks.py）只证明接口对了；**这一层证明前端真的接上了**。
//
// 手法上刻意避开的坑（都是本项目踩过的）：
//   · el-tabs 的 tab 头是 `div.el-tabs__item`，**不是 <button>** —— 按 button 找会
//     NOT_FOUND 然后静默地继续在原来的 tab 上点，点出一堆假结论；
//   · 页面所有 tab-pane 都在 DOM 里 ⇒ 找表格/按钮必须**先锁定那个 pane**，
//     否则会点到隔壁「消息通知」tab 的删除按钮上去（上一轮真删掉过数据）；
//   · 任务表在 `filteredTasks.length === 0` 时**整个 el-table 都不渲染**（显示"暂无数据"），
//     所以"找不到表格"在空列表时是正常的，不能当失败；
//   · ElMessage 3 秒自动消失 ⇒ 边跑边轮询，不能睡完再读一次；
//   · 判"接口成功"看**里层 code**，不是 HTTP 状态（本项目的老规矩）。
//
// 用法: node probe-admin-tasks-page.mjs
// 退出码 0 = 全过；1 = 有失败项。**无论成败都会清掉自己建的任务**（按标题前缀）。
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || 'admin'
const PASS = process.argv[3] || '123456'
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9600 + Math.floor(Math.random() * 90)
const TITLE = 'probe-端到端任务A'          // 前缀 probe- 是清理的抓手
const DESC = '由探针创建，跑完会自己删掉'
const PREFIX = 'probe-'
const profile = mkdtempSync(join(tmpdir(), 'cdptask-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const fails = []
const notes = []
const check = (name, ok, detail = '') => {
  if (!ok) fails.push(name)
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? '  <- ' + detail : ''}`)
}

let evaluate = async () => 'not-ready'
let closeBrowser = async () => {}

// ===== 页面里共用的小工具（注入到页面上下文，避免每处重写）=====
const HELPERS = `
window.__p = {
  // ⚠️ 任务 pane 的定位：用 innerText（Chrome 下隐藏元素 innerText 为空串），
  //    这样只在**当前可见**的那个 pane 上动手，绝不会误伤隔壁 tab。
  pane: () => [...document.querySelectorAll('.el-tab-pane')]
          .find(p => (p.innerText || '').includes('创建新任务')) || null,
  // 表头是「任务标题」的表格（只在有数据时存在）
  table: () => {
    const p = window.__p.pane(); if (!p) return null
    return [...p.querySelectorAll('.el-table')].find(t =>
      [...t.querySelectorAll('.el-table__header-wrapper th .cell')]
        .some(c => c.innerText.trim() === '任务标题')) || null
  },
  rows: () => { const t = window.__p.table(); if (!t) return []
    return [...t.querySelectorAll('.el-table__body-wrapper tbody tr')]
      .filter(tr => tr.offsetParent !== null) },
  rowText: (tr) => (tr.innerText || '').replace(/\\s+/g, ' ').trim(),
  count: () => {
    const p = window.__p.pane(); if (!p) return null
    const m = (p.innerText || '').replace(/\\s+/g, ' ').match(/共\\s*(\\d+)\\s*个任务/)
    return m ? Number(m[1]) : null
  },
  statCards: () => {
    const p = window.__p.pane(); if (!p) return null
    const out = {}
    for (const s of [...p.querySelectorAll('.stat-item, .stat-card')]) {
      const t = (s.innerText || '').replace(/\\s+/g, ' ').trim()
      const m = t.match(/^(\\S+)\\s*(\\d+)$/); if (m) out[m[1]] = Number(m[2])
    }
    return out
  },
  setVal: (el, v) => {
    // ⚠️ 描述那格是 <textarea>，拿 HTMLInputElement 的 value setter 去写它不报错、
    //    但 Vue 的 v-model 收不到 —— 值看着填进去了，提交时是空的。
    const proto = el.tagName === 'TEXTAREA'
      ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, v)
    el.dispatchEvent(new Event('input', { bubbles: true }))
    el.dispatchEvent(new Event('change', { bubbles: true }))
  },
  toasts: () => [...document.querySelectorAll('.el-message')]
    .filter(e => e.getBoundingClientRect().height > 0)
    .map(e => (e.innerText || '').replace(/\\n/g, ' ').trim()),
  // 走网关读接口的**第一手**结果（不依赖 CDP 事件管道）
  api: async (path, method = 'GET', body = null) => {
    const tok = localStorage.getItem('token') || ''
    const opt = { method, headers: { Authorization: 'Bearer ' + tok } }
    if (body) { opt.headers['Content-Type'] = 'application/json'; opt.body = JSON.stringify(body) }
    const r = await fetch('/api' + path, opt)
    const t = await r.text()
    try { const j = JSON.parse(t); return { http: r.status, code: j.code, data: j.data, msg: j.message } }
    catch { return { http: r.status, head: t.slice(0, 160) } }
  },
}
'ok'
`

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
  const jsErrors = []
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params.exceptionDetails
      jsErrors.push((d?.exception?.description || d?.text || '').split('\n')[0].slice(0, 160))
    }
  }
  const send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) return { __err: r.result.exceptionDetails.exception?.description }
    return r.result?.result?.value
  }
  await send('Page.enable'); await send('Runtime.enable')
  closeBrowser = async () => { try { await send('Browser.close') } catch {} }

  // ---------- 登录 ----------
  await send('Page.navigate', { url: `${APP}/login` })
  for (let i = 0; i < 60; i++) { await sleep(500); if (await evaluate(`!!document.querySelector('.lr-submit')`)) break }
  const filled = await evaluate(`(() => {
    const setVal = window.__setValTmp = (sel, val) => {
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
  if (!filled?.clicked) throw new Error(`登录表单没找全: ${JSON.stringify(filled)}`)
  let path = ''
  for (let i = 0; i < 40; i++) { await sleep(500); path = await evaluate('location.pathname'); if (path && path !== '/login') break }
  check('登录成功（离开 /login）', !!path && path !== '/login', path)

  // ---------- 进页面 ----------
  await send('Page.navigate', { url: `${APP}/admin/messages` })
  for (let i = 0; i < 60; i++) { await sleep(500); if (await evaluate(`document.querySelectorAll('.el-tabs__item').length > 0`)) break }
  await evaluate(HELPERS)

  // ---------- 切到「任务管理」tab ----------
  // ⚠️ tab 头是 div.el-tabs__item，不是 <button>
  const tabClick = await evaluate(`(() => {
    const t = [...document.querySelectorAll('.el-tabs__item')]
      .find(x => x.textContent.trim() === '任务管理')
    if (!t) return 'NOT_FOUND'
    t.click(); return 'CLICKED'
  })()`)
  await sleep(1200)
  await evaluate(HELPERS)
  const onTaskTab = await evaluate(`(() => {
    const t = [...document.querySelectorAll('.el-tabs__item')]
      .find(x => x.textContent.trim() === '任务管理')
    const active = !!t && t.className.includes('is-active')
    const hasForm = !!document.querySelector('input[placeholder="输入任务标题"]')
    const pane = !!window.__p.pane()
    return { active, hasForm, pane, clicked: ${JSON.stringify(tabClick)} }
  })()`)
  check('切到「任务管理」tab（tab 变 active 且创建表单在）',
    onTaskTab?.active && onTaskTab?.hasForm && onTaskTab?.pane, JSON.stringify(onTaskTab))

  // ---------- 基线 ----------
  // 清掉上次可能的残留（同名前缀），保证基线干净
  const cleanup = async () => {
    const r = await evaluate(`(async () => {
      const res = await window.__p.api('/admin/tasks/list?page=1&size=200')
      const items = (res && res.data && res.data.items) || []
      const mine = items.filter(i => String(i.title || '').startsWith(${JSON.stringify(PREFIX)}))
      for (const m of mine) await window.__p.api('/admin/tasks/' + m.id, 'DELETE')
      return { before: items.length, removed: mine.map(m => m.id) }
    })()`)
    return r
  }
  const pre = await cleanup()
  if (pre?.removed?.length) notes.push(`开工前清掉 ${pre.removed.length} 条残留: ${pre.removed.join(',')}`)
  await evaluate(`window.__p.api('/admin/tasks/list?page=1&size=200')`)
  const base = await evaluate(`window.__p.api('/admin/tasks/list?page=1&size=200')`)
  const baseTotal = base?.data?.total
  check('列表接口里层 code==200（不再是恒空桩）', base?.code === 200, JSON.stringify(base).slice(0, 200))

  // 刷新让页面拿到干净基线
  await send('Page.navigate', { url: `${APP}/admin/messages` })
  for (let i = 0; i < 40; i++) { await sleep(500); if (await evaluate(`!!window.__p`)) break }
  await evaluate(HELPERS)
  await evaluate(`(() => { const t = [...document.querySelectorAll('.el-tabs__item')].find(x => x.textContent.trim() === '任务管理'); if (t) t.click() })()`)
  await sleep(1500)
  await evaluate(HELPERS)
  const pageCount0 = await evaluate(`window.__p.count()`)
  check('页面「共 N 个任务」与接口 total 一致（基线）',
    pageCount0 === baseTotal, `页面=${pageCount0} 接口=${baseTotal}`)

  // ---------- 空标题：页面自己该挡住 ----------
  await evaluate(`(() => {
    const b = [...window.__p.pane().querySelectorAll('button')]
      .find(x => x.textContent.includes('创建任务'))
    if (b) b.click()
  })()`)
  await sleep(600)
  const emptyToast = await evaluate(`window.__p.toasts()`)
  const afterEmpty = await evaluate(`window.__p.api('/admin/tasks/list?page=1&size=200')`)
  check('空标题点「创建任务」→ 弹错误提示（不是静默什么都不做）',
    Array.isArray(emptyToast) && emptyToast.some(t => /标题/.test(t)), JSON.stringify(emptyToast))
  check('  且没有偷偷建出一条', afterEmpty?.data?.total === baseTotal,
    `total=${afterEmpty?.data?.total} base=${baseTotal}`)

  // ---------- 正常创建 ----------
  const created = await evaluate(`(() => {
    const p = window.__p.pane()
    const t = p.querySelector('input[placeholder="输入任务标题"]')
    const d = p.querySelector('textarea[placeholder="输入任务描述"]')
    if (!t) return { err: '找不到标题输入框' }
    window.__p.setVal(t, ${JSON.stringify(TITLE)})
    if (d) window.__p.setVal(d, ${JSON.stringify(DESC)})
    const b = [...p.querySelectorAll('button')].find(x => x.textContent.includes('创建任务'))
    if (!b) return { err: '找不到创建按钮' }
    b.click(); return { ok: true }
  })()`)
  check('填好标题后点「创建任务」', created?.ok === true, JSON.stringify(created))

  const createToasts = []
  for (let i = 0; i < 40; i++) {
    await sleep(300)
    const ts = await evaluate(`window.__p.toasts()`)
    if (Array.isArray(ts)) for (const x of ts) if (x && !createToasts.includes(x)) createToasts.push(x)
    if (createToasts.length) break
  }
  check('创建成功弹了提示', createToasts.some(t => /创建/.test(t)), JSON.stringify(createToasts))
  await sleep(1200)
  await evaluate(HELPERS)

  // ---------- 行真的出现在页面上 ----------
  const rowInfo = await evaluate(`(() => {
    const rs = window.__p.rows()
    const tr = rs.find(x => window.__p.rowText(x).includes(${JSON.stringify(TITLE)}))
    return {
      rowCount: rs.length,
      found: !!tr,
      text: tr ? window.__p.rowText(tr) : null,
      count: window.__p.count(),
      empty: (window.__p.pane().innerText || '').includes('暂无数据'),
      stats: window.__p.statCards(),
    }
  })()`)
  check('★ 表格里出现了刚建的那一行（不是"提示说成功、列表还是空"）',
    rowInfo?.found, String(JSON.stringify(rowInfo)).slice(0, 300))
  check('  行上渲染出标题与「待处理」', !!rowInfo?.text && rowInfo.text.includes(TITLE) && rowInfo.text.includes('待处理'),
    rowInfo?.text)
  check('  「共 N 个任务」比基线 +1', rowInfo?.count === baseTotal + 1,
    `count=${rowInfo?.count} base=${baseTotal}`)
  check('  空态「暂无数据」已消失', rowInfo?.empty === false, rowInfo?.empty)

  // ---------- 服务端对账：这一行不是本地乐观插入的 ----------
  const afterCreate = await evaluate(`window.__p.api('/admin/tasks/list?page=1&size=200')`)
  const createdItem = (afterCreate?.data?.items || []).find(i => i.title === TITLE)
  check('★ 接口里真有这条（页面那一行来自服务端，不是本地乐观插入）',
    afterCreate?.data?.total === baseTotal + 1 && !!createdItem,
    `total=${afterCreate?.data?.total} item=${String(JSON.stringify(createdItem)).slice(0, 200)}`)
  check('  落库的 priority 是 medium（表单默认）且 status=pending',
    createdItem?.priority === 'medium' && createdItem?.status === 'pending',
    String(JSON.stringify(createdItem)).slice(0, 160))
  // 描述那格是 <textarea> —— 用错 prototype 的 value setter 会让 v-model 收不到，
  // 表现是"填了但提交上去是空的"。所以这条要真断言，不能只看标题对了就算过。
  check('★ 描述（textarea）也真提交上去了，不是"填了但 v-model 没收到"',
    createdItem?.description === DESC, JSON.stringify(createdItem?.description))

  // ---------- 刷新后仍在（持久化证据）----------
  await send('Page.navigate', { url: `${APP}/admin/messages` })
  for (let i = 0; i < 40; i++) { await sleep(500); if (await evaluate(`!!window.__p`)) break }
  await evaluate(HELPERS)
  await evaluate(`(() => { const t = [...document.querySelectorAll('.el-tabs__item')].find(x => x.textContent.trim() === '任务管理'); if (t) t.click() })()`)
  await sleep(1800)
  await evaluate(HELPERS)
  const afterReload = await evaluate(`(() => {
    const rs = window.__p.rows()
    return { found: rs.some(x => window.__p.rowText(x).includes(${JSON.stringify(TITLE)})), count: window.__p.count() }
  })()`)
  check('★ 刷新页面后那行还在（真落库了，不是前端内存里的假象）',
    afterReload?.found === true, JSON.stringify(afterReload))

  // ---------- 行内改状态 ----------
  // Element Plus 的下拉挂在 body 上，所以 select 在 pane 里找、选项在全局找
  const pick = await evaluate(`(() => {
    const tr = window.__p.rows().find(x => window.__p.rowText(x).includes(${JSON.stringify(TITLE)}))
    if (!tr) return 'NO_ROW'
    const sel = tr.querySelector('.el-select')
    if (!sel) return 'NO_SELECT'
    // Element Plus 2.4+ 的可点区是 .el-select__wrapper（外层 .el-select 只是个壳）；
    // 而且它是靠 mousedown/mouseup 序列切换的，只 .click() 在部分版本上不展开。
    const w = sel.querySelector('.el-select__wrapper') || sel
    w.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    w.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
    w.click()
    return 'OPENED'
  })()`)
  await sleep(1000)
  const optClick = await evaluate(`(() => {
    const all = [...document.querySelectorAll('.el-select-dropdown__item')]
    const vis = all.filter(e => e.offsetParent !== null)
    const o = (vis.length ? vis : all).find(e => e.textContent.trim() === '进行中')
    if (!o) return { r: 'NO_OPTION', n: all.length, texts: all.map(e => e.textContent.trim()).slice(0, 12),
                     poppers: [...document.querySelectorAll('.el-popper')].map(e => (e.innerText || '').replace(/\\s+/g, ' ').slice(0, 30)) }
    o.click(); return { r: 'PICKED' }
  })()`)
  if (optClick && optClick.r !== 'PICKED') notes.push('下拉选项诊断: ' + JSON.stringify(optClick))
  const updToasts = []
  for (let i = 0; i < 40; i++) {
    await sleep(300)
    const ts = await evaluate(`window.__p.toasts()`)
    if (Array.isArray(ts)) for (const x of ts) if (x && !updToasts.includes(x)) updToasts.push(x)
    if (updToasts.length) break
  }
  check('行内下拉选「进行中」', pick === 'OPENED' && optClick?.r === 'PICKED', `${pick}/${JSON.stringify(optClick)}`)
  check('改状态弹了提示', updToasts.some(t => /状态/.test(t)), JSON.stringify(updToasts))

  const stInDb = await evaluate(`(async () => {
    const r = await window.__p.api('/admin/tasks/list?page=1&size=200')
    const it = (r.data.items || []).find(i => i.title === ${JSON.stringify(TITLE)})
    return it ? it.status : null
  })()`)
  check('★ 库里那条状态真的变成 in_progress（不是只改了本地 row.status）',
    stInDb === 'in_progress', stInDb)

  // ---------- 筛选按钮 ----------
  const filterCheck = await evaluate(`(async () => {
    const p = window.__p.pane()
    const click = (label) => { const b = [...p.querySelectorAll('button')].find(x => x.textContent.trim() === label); if (b) b.click() }
    click('已完成'); await new Promise(r => setTimeout(r, 400))
    const afterCompleted = window.__p.rows().filter(x => window.__p.rowText(x).includes(${JSON.stringify(TITLE)})).length
    click('全部'); await new Promise(r => setTimeout(r, 400))
    const afterAll = window.__p.rows().filter(x => window.__p.rowText(x).includes(${JSON.stringify(TITLE)})).length
    click('进行中'); await new Promise(r => setTimeout(r, 400))
    const afterInProgress = window.__p.rows().filter(x => window.__p.rowText(x).includes(${JSON.stringify(TITLE)})).length
    click('全部')
    return { afterCompleted, afterAll, afterInProgress }
  })()`)
  check('筛选按钮真的在过滤（进行中能看到、已完成看不到）',
    filterCheck?.afterInProgress === 1 && filterCheck?.afterCompleted === 0 && filterCheck?.afterAll === 1,
    JSON.stringify(filterCheck))

  // ---------- 删除 ----------
  await sleep(600)
  const delClick = await evaluate(`(() => {
    const tr = window.__p.rows().find(x => window.__p.rowText(x).includes(${JSON.stringify(TITLE)}))
    if (!tr) return 'NO_ROW'
    const b = [...tr.querySelectorAll('button')].find(x => x.textContent.includes('删除'))
    if (!b) return 'NO_BTN'
    b.click(); return 'CLICKED'
  })()`)
  await sleep(900)
  const dlg = await evaluate(`(() => {
    const d = [...document.querySelectorAll('.el-dialog')].filter(x => x.offsetParent !== null)
      .find(x => (x.innerText || '').includes('确认删除'))
    if (!d) return { found: false }
    const hasTitle = (d.innerText || '').includes(${JSON.stringify(TITLE)})
    const b = [...d.querySelectorAll('button')].find(x => x.textContent.includes('确认删除'))
    if (b) b.click()
    return { found: true, hasTitle, clicked: !!b }
  })()`)
  check('删除弹窗弹出且标题正确、确认按钮点了',
    delClick === 'CLICKED' && dlg?.found && dlg?.hasTitle && dlg?.clicked,
    `${delClick} / ${JSON.stringify(dlg)}`)

  const delToasts = []
  for (let i = 0; i < 40; i++) {
    await sleep(300)
    const ts = await evaluate(`window.__p.toasts()`)
    if (Array.isArray(ts)) for (const x of ts) if (x && !delToasts.includes(x)) delToasts.push(x)
    if (delToasts.length) break
  }
  check('删除弹了成功提示', delToasts.some(t => /删除/.test(t)), JSON.stringify(delToasts))
  await sleep(1200)
  await evaluate(HELPERS)

  const afterDel = await evaluate(`(() => {
    const rs = window.__p.rows()
    return {
      stillThere: rs.some(x => window.__p.rowText(x).includes(${JSON.stringify(TITLE)})),
      count: window.__p.count(),
      rowCount: rs.length,
    }
  })()`)
  check('★ 删除后那一行从页面上消失了', afterDel?.stillThere === false, JSON.stringify(afterDel))
  check('  「共 N 个任务」回到基线', afterDel?.count === baseTotal,
    `count=${afterDel?.count} base=${baseTotal}`)

  const finalApi = await evaluate(`window.__p.api('/admin/tasks/list?page=1&size=200')`)
  check('★ 接口 total 也回到基线（库里真删了，不是只从页面上摘掉）',
    finalApi?.data?.total === baseTotal, `total=${finalApi?.data?.total} base=${baseTotal}`)
  check('  库里没有 probe- 前缀的残留',
    !(finalApi?.data?.items || []).some(i => String(i.title || '').startsWith(PREFIX)))

  check('全程无未捕获 JS 异常', jsErrors.length === 0, [...new Set(jsErrors)].join(' | '))
  return { baseTotal }
}

let result = { baseTotal: null }
try { result = await main() } catch (e) { console.log(`\n脚本异常: ${e.message}`); fails.push('脚本异常: ' + e.message) }

// ===== 兜底清理：无论成败，把自己建的任务按标题前缀删掉 =====
try {
  const cleaned = await evaluate(`(async () => {
    if (!window.__p || !window.__p.api) return 'no-helpers'
    const r = await window.__p.api('/admin/tasks/list?page=1&size=200')
    const mine = ((r && r.data && r.data.items) || []).filter(i => String(i.title || '').startsWith(${JSON.stringify(PREFIX)}))
    for (const m of mine) await window.__p.api('/admin/tasks/' + m.id, 'DELETE')
    const after = await window.__p.api('/admin/tasks/list?page=1&size=200')
    return { removed: mine.map(m => m.id), left: after && after.data ? after.data.total : null }
  })()`)
  if (cleaned && cleaned.removed && cleaned.removed.length) {
    notes.push(`兜底清理删掉了 ${cleaned.removed.length} 条: ${cleaned.removed.join(',')}`)
  }
  if (cleaned && cleaned.left !== null && result.baseTotal !== null && cleaned.left !== result.baseTotal) {
    fails.push(`兜底清理后 total=${cleaned.left}，与基线 ${result.baseTotal} 不符`)
  }
} catch (e) { notes.push('兜底清理没能执行: ' + e.message) }

await closeBrowser()
await sleep(800)
try { chrome.kill() } catch {}
try { rmSync(profile, { recursive: true, force: true }) } catch {}

console.log(`\n===== 结论 =====`)
if (!fails.length) console.log('PASS(0)  「任务管理」tab 端到端可用')
else { console.log(`FAIL(${fails.length})`); for (const f of fails) console.log(`  - ${f}`) }
for (const n of notes) console.log(`  注意：${n}`)
process.exit(fails.length ? 1 : 0)
