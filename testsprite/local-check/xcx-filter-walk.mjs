// 小程序 H5 通道（127.0.0.1:5180）**列表筛选条逐值走查**。
//
// 这是第三层，前两层各差一截：
//   xcx-page-smoke.mjs  —— 只改 hash 进页面，看默认态（= 默认筛选值）
//   xcx-tab-walk.mjs    —— 逐 tab 切，但只管**切面板**的控件
// 剩下 20 来个页面里的 `filter-bar` 是**筛列表**的（学期/等级/状态/类型/班级），
// 冒烟和 tab 走查都只看得到默认那一个值。
//
// 核心不变量是**分档行数之和 = 总数行数**：
//   「待处理 + 已处理 + 已拒绝 + 已取消 = 全部」
// 这条不需要事先知道每档该有多少条 —— 它是页面自己该满足的算术。
// 而"筛选没生效/选项张冠李戴"恰恰会破坏它：所有档都渲染出全量，
// 和就变成总数的 N 倍。这也最容易被追问：「点学期筛选了吗？点了。数字变了吗？没变。」
//
// 为什么要数**渲染出的行**而不是读正文里的「共 N 条」：
//   实测多数页的「共 N 条」是**不带筛选的总数**（teacher/admin 的预警页、
//   class-requests 每档都显示"共 9 条"）。拿它当判据会把"筛选没生效"判成通过。
//   要看见筛选的效果，只能数列表真正渲染了几行。
//
// 用法:
//   node xcx-filter-walk.mjs <role> --discover   # 枚举每页每个筛选控件 + 逐值数行
//   node xcx-filter-walk.mjs <role>              # 按 CASES 判定
// 输出: xcx-filters-<role>.txt
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.XCX_BASE || 'http://127.0.0.1:5180'

const role = process.argv[2]
const discover = process.argv.includes('--discover')

function LANDED(r) {
  return `(localStorage.getItem('token') ? 1 : 0) === 1 && (location.hash || '').includes('/pages/${r}/')`
}

/**
 * 每页要：列表行选择器（数行靠它）、以及每个筛选控件的期望性质。
 *   partition: true  —— 除"全部/全部X"外的各档构成一个**划分**，行数之和须等于总数
 *   cap             —— 列表有"显示前 N 条"的截断，行数封顶
 *   kind: 'tab'     —— 这个 filter-bar 其实是**切 tab**（共 4 个学院 / 共 12 个专业），
 *                      各档不是划分，只验"切过去了且列表变了"
 *   kind: 'sort'    —— 这个 filter-bar 既不筛也不切，是**排序**（按修读人数/均分/挂科数）。
 *                      三档渲染同一批行，数行数恒等、分档之和也不成立；
 *                      它的判据只能是**顺序**：三个排序键不该产出同一个顺序。
 * 索引对应页面上第几个合格的 filter-bar（容器内 ≥2 个 chip）。
 */
const CASES = {
  student: {
    user: '2023020616', pass: '123456', landed: LANDED('student'),
    pages: [
      { path: '/pages/student/scores', rowSel: '.score-item', controls: [{ partition: true }] },
      { path: '/pages/student/warnings', rowSel: '.warn-card', controls: [{ partition: true }] },
      { path: '/pages/student/appeals', rowSel: '.appeal-card', controls: [{ partition: true }] },
      { path: '/pages/student/notification-center', rowSel: '.notify-card', controls: [{ partition: true }] }
    ]
  },

  teacher: {
    user: '10001', pass: '123456', landed: LANDED('teacher'),
    pages: [
      {
        path: '/pages/teacher/warnings', rowSel: '.wrow',
        controls: [{ partition: true }, { partition: true }]
      }
    ]
  },

  counselor: {
    user: '1000000001', pass: '123456', landed: LANDED('counselor'),
    pages: [
      { path: '/pages/counselor/warnings', rowSel: '.warn-card', controls: [{ partition: true }] },
      { path: '/pages/counselor/assistance', rowSel: '.plan-card', controls: [{ partition: true }] },
      // 这条不是筛选是**排序**（sortKey：按修读人数/按均分/按挂科数）——
      // 验「顺序真的变了」，数行数对它没有意义（三档都是同一批课）。
      { path: '/pages/counselor/courses', rowSel: '.course', controls: [{ kind: 'sort' }] },
      // 行是 .stu（学分不足学生）；`.cls` 是上面的**班级块**（classMonitors，6 个），
      // riskKey 筛不到它 —— 早先把 rowSel 写成 .cls，三档都数到 6，误报了三条 MISS。
      { path: '/pages/counselor/credit-monitor', rowSel: '.stu', controls: [{ partition: false }] },
      // 这两页的筛选条在 tab 面板里：默认 tab 上只有"班级筛选"那一条可见
      { path: '/pages/counselor/scores', rowSel: '.stu', controls: [{ partition: false }] },
      { path: '/pages/counselor/students', rowSel: '.stu-card', controls: [{ partition: false }] }
    ]
  },

  'college-admin': {
    user: 'collegeadmin1', pass: '123456', landed: LANDED('college-admin'),
    pages: [
      // 行是 .stu；.srow 是详情抽屉里的成绩行，别搞混
      { path: '/pages/college-admin/students', rowSel: '.stu', controls: [{ partition: false }] },
      { path: '/pages/college-admin/warnings', rowSel: '.warn', controls: [{ partition: false }] },
      { path: '/pages/college-admin/assistance', rowSel: '.row', controls: [{ partition: false }] }
    ]
  },

  admin: {
    user: 'admin', pass: '123456', landed: LANDED('admin'),
    pages: [
      { path: '/pages/admin/users', rowSel: '.usr', controls: [{ partition: false }, { partition: false }] },
      { path: '/pages/admin/warnings', rowSel: '.warn', controls: [{ partition: true }] },
      // 这三个 filter-bar 其实是**切 tab**：两个 tab 的行类名不同，按档给选择器
      { path: '/pages/admin/colleges', rowSel: { 学院: '.col', 专业: '.maj' }, controls: [{ kind: 'tab' }] },
      { path: '/pages/admin/courses', rowSel: { 课程: '.course', 班级: '.klass' }, controls: [{ kind: 'tab' }] },
      {
        path: '/pages/admin/class-requests', rowSel: '.app',
        controls: [{ partition: true }, { partition: true }]
      },
      {
        path: '/pages/admin/permissions',
        rowSel: { 角色: '.role', 权限点: '.perm', 账号权限查询: '.plain-row' },
        controls: [{ kind: 'tab' }]
      }
    ]
  }
}

const cfg = CASES[role]
if (!cfg) {
  console.error(`role 必须是 ${Object.keys(CASES).join(' | ')} 之一，收到: ${role ?? '(空)'}`)
  process.exit(2)
}

const ENUM_ROOTS = `(() => {
  const roots = Array.from(document.querySelectorAll('.filter-bar'))
  return JSON.stringify(roots
    .filter(r => r.querySelectorAll('.filter-bar__chip').length >= 2)
    .map((root, i) => ({
      i,
      chips: Array.from(root.querySelectorAll('.filter-bar__chip')).map(n => (n.innerText || '').trim())
    })))
})()`

const clickChip = (rootIdx, raw) => `(() => {
  const roots = Array.from(document.querySelectorAll('.filter-bar'))
    .filter(r => r.querySelectorAll('.filter-bar__chip').length >= 2)
  const root = roots[${rootIdx}]
  if (!root) return JSON.stringify({ ok: false, why: 'no-root' })
  const items = Array.from(root.querySelectorAll('.filter-bar__chip'))
  const el = items.find(n => (n.innerText || '').trim() === ${JSON.stringify(raw)})
    || items.find(n => (n.innerText || '').trim().split('\\n')[0].trim() === ${JSON.stringify(raw)})
  if (!el) return JSON.stringify({ ok: false, why: 'no-chip', items: items.map(n => (n.innerText||'').trim()) })
  el.click()
  return JSON.stringify({ ok: true })
})()`

const activeChip = rootIdx => `(() => {
  const roots = Array.from(document.querySelectorAll('.filter-bar'))
    .filter(r => r.querySelectorAll('.filter-bar__chip').length >= 2)
  const root = roots[${rootIdx}]
  if (!root) return '(无)'
  const el = Array.from(root.querySelectorAll('.filter-bar__chip')).find(n => n.classList.contains('filter-bar__chip--active'))
  return el ? (el.innerText || '').trim().split('\\n')[0].trim() : '(无 active)'
})()`

const rowCount = sel => `document.querySelectorAll(${JSON.stringify(sel)}).length`

/** 前 n 行的正文，逗号拼 —— 排序控件靠它比对"换个键顺序变了没有"。 */
const rowOrder = (sel, n = 6) => `(() => {
  const nodes = Array.from(document.querySelectorAll(${JSON.stringify(sel)})).slice(0, ${n})
  return JSON.stringify(nodes.map(x => (x.innerText || '').replace(/\\s+/g, ' ').trim()))
})()`

/**
 * 走控件 #k 之前，把**其余控件**复位到第一档。
 *
 * 页面上多个筛选是**叠加**的（状态 ∩ 等级）。不复位的话，量到的是"上一轮最后
 * 停在那一档 ∩ 本档"，而不是本档自己的数 —— 实测 teacher/warnings 的「等级」
 * 四档全 0，就是因为「状态」还停在"已处理"（0 条）。那是走查器的错，不是页面的错。
 * 复位之后每个控件都在"其余为全部"的基准下测，结果才对得上用户的直觉。
 */
const resetOthers = rootIdx => `(() => {
  const roots = Array.from(document.querySelectorAll('.filter-bar'))
    .filter(r => r.querySelectorAll('.filter-bar__chip').length >= 2)
  const done = []
  roots.forEach((root, j) => {
    if (j === ${rootIdx}) return
    const items = Array.from(root.querySelectorAll('.filter-bar__chip'))
    const active = items.find(n => n.classList.contains('filter-bar__chip--active'))
    if (items[0] && active !== items[0]) { items[0].click(); done.push(j) }
  })
  return JSON.stringify(done)
})()`

/** 行选择器可以按档给（切 tab 的控件里，两个 tab 的行类名不同）。 */
function selFor(page, label) {
  if (page.rowSel && typeof page.rowSel === 'object') {
    return page.rowSel[label] || Object.values(page.rowSel)[0]
  }
  return page.rowSel
}

/**
 * chip 的 innerText 形如「全部\n29」或「全部等级」（不带条数）。
 * 末行是纯数字就当条数，剩下的是名字。
 */
function parseChip(raw) {
  const parts = String(raw).split('\n').map(s => s.trim()).filter(Boolean)
  const last = parts[parts.length - 1]
  if (parts.length > 1 && /^\d+$/.test(last)) {
    return { label: parts.slice(0, -1).join(' '), count: Number(last), raw }
  }
  return { label: parts.join(' '), count: null, raw }
}

const PORT = 9700 + Math.floor(Math.random() * 200)
const profile = mkdtempSync(join(tmpdir(), 'cdpf-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank'
], { stdio: 'ignore' })

const sleep = ms => new Promise(r => setTimeout(r, ms))
const cleanup = () => {
  try { chrome.kill() } catch {}
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
}

const lines = []
const say = s => { lines.push(s); console.log(s) }

const main = async () => {
  let target = null
  for (let i = 0; i < 80; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      target = list.find(t => t.type === 'page')
      if (target) break
    } catch {}
    await sleep(250)
  }
  if (!target) { say('FAIL 调试端口未就绪'); cleanup(); process.exit(1) }

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })

  let id = 0
  const pending = new Map()
  let logs = []
  ws.onmessage = e => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Runtime.consoleAPICalled' && ['error', 'warning'].includes(m.params.type)) {
      const text = (m.params.args || []).map(a => a.value ?? a.description ?? '').join(' ')
      if (text) logs.push(`[${m.params.type}] ${text.slice(0, 200)}`)
    }
  }
  const send = (method, params = {}) =>
    new Promise(res => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async expr => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    return r.result?.result?.value
  }

  await send('Page.enable')
  await send('Runtime.enable')
  await send('Page.navigate', { url: APP })

  const waitFor = async (expr, timeoutMs = 45000) => {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      try { if (await evaluate(expr)) return true } catch {}
      await sleep(400)
    }
    return false
  }

  await waitFor(`document.querySelectorAll('uni-input').length >= 3`)
  await evaluate(`(() => {
    const byPh = (ph) => {
      const wrap = Array.from(document.querySelectorAll('uni-input'))
        .find(u => (u.querySelector('.uni-input-placeholder')?.textContent || '').trim() === ph)
      return wrap ? wrap.querySelector('input') : null
    }
    const setVal = (el, val) => {
      if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true }))
      el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    setVal(byPh('请输入学号或工号'), ${JSON.stringify(cfg.user)})
    setVal(byPh('请输入密码'), ${JSON.stringify(cfg.pass)})
    setVal(byPh('输入验证码'), (document.querySelector('.captcha-code')?.textContent || '').trim())
    document.querySelector('.btn')?.click()
    return true
  })()`)

  const landed = await waitFor(cfg.landed, 30000)
  say(`角色 ${role}  账号 ${cfg.user}`)
  say(`登录结果: landed=${landed}`)
  if (!landed) { say('FAIL 登录没落到该角色首页'); cleanup(); process.exit(1) }
  if (discover) say('（--discover 模式：枚举 + 逐值数行，不做判定）')

  let okC = 0, badC = 0, noPartition = 0, errC = 0, total = 0

  for (const page of cfg.pages) {
    await evaluate(`location.hash = '#/pages/student/dashboard'`)
    await sleep(1000)
    await evaluate(`location.hash = ${JSON.stringify('#' + page.path)}`)
    await sleep(5000)

    let roots = []
    try { roots = JSON.parse(await evaluate(ENUM_ROOTS) || '[]') } catch {}
    const rowSelText = page.rowSel && typeof page.rowSel === 'object'
      ? JSON.stringify(page.rowSel) : page.rowSel
    say(`\n########## ${page.path}  筛选条 ${roots.length} 个  rowSel=${rowSelText} ##########`)
    if (!roots.length) { say('  （默认态下没有筛选条）'); continue }

    for (const r of roots) {
      const spec = (page.controls || [])[r.i] || {}
      const chips = r.chips.map(parseChip)
      const isTab = spec.kind === 'tab'
      const isSort = spec.kind === 'sort'
      // 数行数对切tab/排序都不适用，只有"筛列表"的控件才谈得上行数与分档之和
      const countsRows = !isTab && !isSort
      say(`  --- 控件#${r.i} ${chips.length} 项 ` +
          `[${chips.map(c => c.label + (c.count !== null ? '(' + c.count + ')' : '')).join(' | ')}]` +
          `  ${isTab ? '切tab' : isSort ? '排序' : (spec.partition ? '划分' : '非划分')} ---`)

      // 先把其余控件复位，保证量的是"本档 ∩ 其余为全部"
      const reset = await evaluate(resetOthers(r.i))
      if (reset && reset !== '[]') await sleep(2500)

      const measured = []
      for (const c of chips) {
        logs = []
        const clicked = await evaluate(clickChip(r.i, c.raw))
        let info = {}
        try { info = JSON.parse(clicked || '{}') } catch {}
        await sleep(3500)
        const active = await evaluate(activeChip(r.i))
        const rows = await evaluate(rowCount(selFor(page, c.label)))
        let order = null
        if (isSort) { try { order = JSON.parse(await evaluate(rowOrder(selFor(page, c.label))) || '[]') } catch {} }
        const errors = logs.filter(l => l.startsWith('[error]'))
        measured.push({ ...c, ok: info.ok === true, active, rows, order, why: info.why, items: info.items })
        if (errors.length) errC += 1
        say(`    ${info.ok ? '  ' : '!!'}「${c.label}」` +
            (c.count !== null ? ` 声明${c.count}` : ' 无条数') +
            `  行数=${rows}  active=「${active}」` +
            (errors.length ? `  console.error ${errors.length} 条` : ''))
        if (order) say(`       序: ${order.map(s => s.split(' ')[0]).join(' < ')}`)
        if (!info.ok) say(`       点不到: ${info.why}${info.items ? ' 可选: ' + JSON.stringify(info.items) : ''}`)
      }

      if (discover) { total += chips.length; continue }

      // —— 判定 ——
      for (const m of measured) {
        total += 1
        const switched = m.ok && !!m.active && (m.active === m.label || m.active.startsWith(m.label))
        let pass = switched
        const notes = []
        if (!switched) notes.push(`点了没切过去(active=「${m.active}」)`)
        if (countsRows && m.count !== null && m.rows !== m.count && spec.cap === undefined) {
          // 声明的档位条数与实际渲染行数不等 —— 要么计数错，要么筛选错
          pass = false
          notes.push(`声明 ${m.count} ≠ 实际 ${m.rows}`)
        }
        if (!pass) badC += 1; else okC += 1
        if (notes.length) say(`    MISS「${m.label}」 ${notes.join('；')}`)
      }

      // 排序控件：三档渲染的是**同一批行**，唯一能证伪的是"换了键顺序变了没有"。
      // 若三个键产出完全相同的顺序，多半是排序没接上（点了但列表没重排）。
      if (isSort) {
        const orders = measured.map(m => JSON.stringify(m.order || []))
        const distinct = new Set(orders).size
        const good = distinct >= 2
        if (!good) badC += 1
        say(`    ${good ? '排序 OK' : '排序 MISS'}  ${measured.length} 个排序键产出 ${distinct} 种顺序` +
            (good ? '' : '   ← 各键顺序一致：排序很可能没生效（点了但列表没重排）'))
      }

      // 划分不变量：各档之和 == 总数档
      if (countsRows && spec.partition) {
        const totalChip = measured.find(m => /^(全部|不限|所有)/.test(m.label))
        if (!totalChip) { noPartition += 1; say('    [无"全部"档，跳过分档之和校验]') }
        else {
          const parts = measured.filter(m => m !== totalChip)
          const sum = parts.reduce((a, m) => a + m.rows, 0)
          const good = sum === totalChip.rows
          if (!good) badC += 1
          say(`    ${good ? '不变量 OK' : '不变量 MISS'}  各档行数 ${parts.map(m => m.label + '=' + m.rows).join(' + ')}` +
              ` = ${sum}  vs  ${totalChip.label}=${totalChip.rows}` +
              (good ? '' : '   ← 分档之和与总数对不上：筛选很可能没生效（各档都倒了全量）'))
        }
      }
    }
  }

  say('')
  if (discover) {
    say(`==== ${role}: 枚举 + 数行 dump 完毕，共 ${total} 个筛选值 ====`)
  } else {
    say(`==== ${role}: ${okC}/${total} 个筛选值通过，${badC} 项未通过，${errC} 项有 console.error ====`)
  }
  writeFileSync(`xcx-filters-${role}.txt`, lines.join('\n') + '\n', 'utf8')
  console.log(`已写出 xcx-filters-${role}.txt`)

  cleanup()
  process.exit(discover ? 0 : (badC ? 1 : 0))
}

main().catch(e => { say(`FAIL ${e.message}`); cleanup(); process.exit(1) })
