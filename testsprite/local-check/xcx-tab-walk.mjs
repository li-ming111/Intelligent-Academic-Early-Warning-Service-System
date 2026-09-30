// 小程序 H5 通道（127.0.0.1:5180）**页内 tab 逐切走查**。
//
// 为什么要有这一支：xcx-page-smoke.mjs 只 `location.hash` 跳到页面 URL 就 dump 正文，
// 而带 tab 的页默认落在**第一个 tab** —— 于是它报的「全绿」实际只覆盖了每个 tab 页的
// 1/N。辅导员成绩页 3 个 tab、学院管理员成绩页 4 个 tab、学生详情 4 个 tab，
// 后面那些从没被打开过，里面的字段名写错、接口 404、空态卡死都测不出来。
// 这是那支冒烟的**诚实边界**，这一支专门把它补上。
//
// 判据与冒烟同构：点得到（tab 真的切过去了）+ 数据判据（只有真数据才出现的字面量）
// + 该 tab 自己的 console.error。**点了但 active 态没动 = 死 tab，判失败** ——
// 只验「文字出现」会把「点了没反应」漏掉，因为按钮旁边的文案本来就在。
//
// 三档判据强度（比冒烟更细，因为 tab 里的事比整页更具体）：
//   data    —— 真数据字面量。tab 面板用 v-if 渲染，同一时刻只有一个是活的，
//              所以面板里的串天然只属于它自己，不必担心和别的 tab 撞。
//   absent  —— **筛选类 tab 专用**：选了「教学建议」就不该再出现别的分类的学生名。
//              「筛选没生效」正是那种「看着有数据、其实全量倒出来了」的静默缺陷。
//   via     —— 用**应用自己的路径**到达这个 tab（而不是点 tab 条）。
//              teacher/analysis 的「学生」tab 只有原生 picker 表单，headless 里
//              驱动不了浏览器原生下拉；但页面上有一条真实入口 ——
//              「课程」tab 的低分名单点一行会带着学生跳过来并自动出结果。
//              走这条路既绕开了 picker，测的又是用户真实会走的动作。
//
// 另有**对照用例**（`control: true`）—— 判据反过来，且不计入通过率：
//   它走的是一条"点了但故意什么都不做"的路径，**期望结果就是判为「未切换」**。
//   为什么要留这么一格：`switched` 是这一支里唯一能抓住「点击没反应」的判据，
//   而"这个判据到底会不会响"是靠它自己证明不了的 —— 万一它哪天被改成恒真
//   （比如比较时写成了 `active !== ''`），report 会一路全绿而没人看得出来。
//   对照用例把这个判据**夹在中间**：它必须响；它要是不响，这里立刻报 CTRL!。
//   不计入通过率，是因为"故意失败"混进 `0 个未通过` 会让那句汇总变成谎话。
//
// 用法:
//   node xcx-tab-walk.mjs <role> --discover   # 枚举每个 tab 控件 + 逐 tab 点开 dump 正文
//   node xcx-tab-walk.mjs <role>              # 按 CASES 里已固化的 label + data 判定
// 输出: xcx-tabs-<role>.txt
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.XCX_BASE || 'http://127.0.0.1:5180'

const role = process.argv[2]
const discover = process.argv.includes('--discover')

/**
 * 导航型 tab 控件的选择器 —— 全工程**五种**，逐一从代码里查出来的，不是笔误。
 * （表单里的 `chip--on` / `opt--on` 单选组不算：它们选的是**值**，不是切内容面板。
 *   全工程 grep `:class="{ '...--on'"` 的穷尽结果里，只有下面五种在切面板。）
 */
const SEL = {
  tabs: { root: '.tabs', item: '.tabs__item', active: 'tabs__item--on' },
  // college-admin/scores 用的是另一套类名（.tab / .tab--on），同样在 .tabs 容器里
  tab: { root: '.tabs', item: '.tab', active: 'tab--on' },
  filter: { root: '.filter-bar', item: '.filter-bar__chip', active: 'filter-bar__chip--active' },
  // student/class-ranking 的「本班 / 本专业」
  listhead: { root: '.list-head__tabs', item: '.list-head__tab', active: 'list-head__tab--on' },
  // auth/register 的「学生 / 教师 / 辅导员」—— 高亮态类名是朴素的一个 active
  roletabs: { root: '.role-tabs', item: '.role-tab', active: 'active' }
}

function LANDED(r) {
  return `(localStorage.getItem('token') ? 1 : 0) === 1 && (location.hash || '').includes('/pages/${r}/')`
}

/**
 * label 与 data 全部来自 `--discover` 的实机 dump，不是照库表想象的
 * （页面渲染的是接口字段，不等于库里的列；猜出来的串会得到假 MISS）。
 *
 * 判定用**固化清单**而非现场枚举 —— 枚举只用在 discover。
 * 若把判定也交给枚举，某个 tab 哪天消失了会静默通过；固化清单则会让它失败。
 */
const CASES = {
  counselor: {
    user: '1000000001',
    pass: '123456',
    landed: LANDED('counselor'),
    pages: [
      {
        path: '/pages/counselor/scores',
        sel: 'tabs',
        tabs: [
          { label: '班级成绩', data: '计算机2101班' },
          { label: '低分关注', data: '低分 9 门' },
          { label: '分数分布', data: '数据结构' },
          {
            // 对照用例：本页只有上面三个 tab，这一格**故意不切**（via 返回成功但不点任何东西），
            // 用来证明 `switched` 判据会响。详见文件头「对照用例」。
            // 它不出现在页面上，所以 label 故意起个带「对照」的名字，避免被当成产品缺陷查。
            label: '（对照）点了不切',
            control: true,
            via: `(() => { window.__ctrlTabMark = Date.now(); return JSON.stringify({ ok: true, how: 'noop-点了不切' }) })()`
          }
        ]
      },
      {
        path: '/pages/counselor/class-management',
        sel: 'tabs',
        tabs: [
          { label: '我的班级', data: '24 名学生' },
          { label: '申请管理', data: '共 3 条' },
          { label: '班级预警', data: '平均绩点 2.83' }
        ]
      },
      {
        path: '/pages/counselor/students',
        sel: 'tabs',
        // 四个 tab 在「学生详情」抽屉里 —— 不先点开一名学生，DOM 里根本没有 tab 条
        prep: 'open-student-detail',
        // 只判抽屉面板的正文：页面背后那份学生列表本来就有一堆字，
        // 拿整页正文判会把「面板空了」漏过去
        scope: '.pane',
        tabs: [
          { label: '概览', data: '17 / 28' },
          { label: '成绩', data: '人工智能导论' },
          { label: '帮扶', data: '针对司明同学的学业帮扶计划' },
          { label: '沟通', data: '期中学习状态沟通' }
        ]
      }
    ]
  },

  teacher: {
    user: '10001',
    pass: '123456',
    landed: LANDED('teacher'),
    pages: [
      {
        path: '/pages/teacher/analysis',
        sel: 'filter',
        tabs: [
          { label: '课程', data: '24/24 人已出成绩' },
          { label: '班级', data: '78.72' },
          {
            label: '学生',
            // 这一格没有可点 tab 的直达路径：表单俩 picker + 一个按钮，
            // picker 在 H5 是原生 select，headless 驱动不了。
            // 走「课程」tab 低分名单那条真实入口，它会带着学生跳过来并自动出结果。
            from: '课程',
            waitFor: '.arow',
            via: `(() => {
              const el = document.querySelector('.arow')
              if (!el) return JSON.stringify({ ok: false, why: 'no-arow' })
              el.click()
              return JSON.stringify({ ok: true, how: 'arow(低分名单)→自动分析' })
            })()`,
            data: '与均分差'
          }
        ]
      },
      {
        path: '/pages/teacher/class-management',
        sel: 'filter',
        tabs: [
          { label: '我的班级', data: '24 名学生' },
          { label: '申请记录', data: '申请管理软件工程2101班' },
          { label: '申请班级', data: '0 名学生 · 暂无负责教师' },
          { label: '改分记录', data: '试卷复核发现平时分漏计，据实更正' }
        ]
      },
      {
        path: '/pages/teacher/feedback-management',
        sel: 'filter',
        tabs: [
          { label: '全部', data: '数据结构的课后作业太难了' },
          // 「全部」与「未回复」现在渲染出同一份内容 —— 库里 2 条反馈都还没回复。
          // 这不是 bug，但也不能假装这两格验了不同的东西；照实记在这里。
          { label: '未回复', data: '数据结构的课后作业太难了' },
          // 筛选类 tab 的真正判据是「别的分类没被倒出来」
          { label: '教学建议', data: '高等数学课程进度有点快', absent: ['韩坤'] },
          { label: '学习困难', data: '数据结构的课后作业太难了', absent: ['司明'] }
        ]
      }
    ]
  },

  'college-admin': {
    user: 'collegeadmin1',
    pass: '123456',
    landed: LANDED('college-admin'),
    pages: [
      {
        path: '/pages/college-admin/scores',
        sel: 'tab',
        tabs: [
          { label: '概览', data: '95%' },
          { label: '分布与课程', data: '合计 238 条' },
          { label: '班级对比', data: '238 条成绩' },
          { label: '成绩查询', data: '命中 238 条' }
        ]
      }
    ]
  },

  student: {
    user: '2023020616',
    pass: '123456',
    landed: LANDED('student'),
    pages: [
      {
        path: '/pages/student/class-ranking',
        sel: 'listhead',
        // 榜单卡。全页 `card--flush` 只此一处 —— 限定到它，正文才不会
        // 200 行里前 22 行全是 hero 卡（那样判据等于没建）
        scope: '.card--flush',
        tabs: [
          { label: '本班', data: '共 24 人' },
          // 库里 major_id=2 只有 1 个班，专业榜与班级榜必然重合，
          // 页面用这句解释「两个 tab 一样」是实情而不是写错了 —— 判据就取它
          { label: '本专业', data: '故专业榜与班级榜一致' }
        ]
      }
    ]
  },

  /**
   * 注册页（免登录）。冒烟那支**完全没覆盖**登录/注册两页 —— 它一进去就登录，
   * 而这两页恰恰是演示时第一个被点开的。这里至少把注册页的角色 tab 走到。
   */
  guest: {
    guest: true,
    landed: `(location.hash || '').includes('/pages/auth/register')`,
    start: '#/pages/auth/register',
    pages: [
      {
        path: '/pages/auth/register',
        sel: 'roletabs',
        tabs: [
          { label: '学生', data: '所属班级', absent: ['职称'] },
          { label: '教师', data: '职称', absent: ['所属专业'] },
          { label: '辅导员', data: '工号', absent: ['所属专业', '职称'] }
        ]
      }
    ]
  }
}

const cfg = CASES[role]
if (!cfg) {
  console.error(`role 必须是 ${Object.keys(CASES).join(' | ')} 之一，收到: ${role ?? '(空)'}`)
  process.exit(2)
}

/** 枚举 tab 控件里的项。取**第一个**有 ≥2 项的根 —— tab 条永远在页面顶部， */
/** 内容区里的筛选条（同样用 .filter-bar）项数另计，靠"第一个"避开。 */
const enumScript = s => `(() => {
  const roots = Array.from(document.querySelectorAll(${JSON.stringify(s.root)}))
  for (const root of roots) {
    const items = Array.from(root.querySelectorAll(${JSON.stringify(s.item)}))
    if (items.length >= 2) return JSON.stringify(items.map(n => (n.innerText || '').trim()))
  }
  return JSON.stringify([])
})()`

const clickScript = (label, s) => `(() => {
  const roots = Array.from(document.querySelectorAll(${JSON.stringify(s.root)}))
  if (!roots.length) return JSON.stringify({ ok: false, why: 'no-control' })
  for (const root of roots) {
    if (root.querySelectorAll(${JSON.stringify(s.item)}).length < 2) continue
    const items = Array.from(root.querySelectorAll(${JSON.stringify(s.item)}))
    const el = items.find(n => (n.innerText || '').trim() === ${JSON.stringify(label)})
      // filter-bar 的 chip 带条数时 innerText 是「未回复 3」，用 startsWith 兜
      || items.find(n => (n.innerText || '').trim().startsWith(${JSON.stringify(label)}))
    if (!el) continue
    const before = items.find(n => n.classList.contains(${JSON.stringify(s.active)}))
    el.click()
    return JSON.stringify({ ok: true, beforeText: before ? (before.innerText || '').trim() : '(无)' })
  }
  const first = roots[0]
  return JSON.stringify({
    ok: false, why: 'no-matching-item',
    items: Array.from(first.querySelectorAll(${JSON.stringify(s.item)})).map(n => (n.innerText || '').trim())
  })
})()`

/** 点完之后 active 态落在谁头上 —— 「tab 真的切过去了」的直接证据 */
const activeScript = s => `(() => {
  const roots = Array.from(document.querySelectorAll(${JSON.stringify(s.root)}))
  for (const root of roots) {
    const items = Array.from(root.querySelectorAll(${JSON.stringify(s.item)}))
    if (items.length < 2) continue
    const el = items.find(n => n.classList.contains(${JSON.stringify(s.active)}))
    return el ? (el.innerText || '').trim() : '(无 active)'
  }
  return '(无控件)'
})()`

const PORT = 9700 + Math.floor(Math.random() * 200)
const profile = mkdtempSync(join(tmpdir(), 'cdpt-'))
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

  // —— 登录（与冒烟同款：失败时 HTTP 200 且 code 200，只能靠 data.status + token 判） ——
  if (cfg.guest) {
    // 免登录页（注册）：直接按 hash 进，不填表单
    await send('Runtime.enable')
    await sleep(3000)
    await evaluate(`location.hash = ${JSON.stringify(cfg.start)}`)
    await sleep(2000)
  } else {
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
  }

  const landed = await waitFor(cfg.landed, 30000)
  say(`角色 ${role}` + (cfg.guest ? '（免登录）' : `  账号 ${cfg.user}`))
  say(`登录结果: landed=${landed}`)
  if (!landed) {
    say(cfg.guest
      ? 'FAIL 注册页没进去 —— 可能被未登录守卫拦回登录页了（这本身是个发现）'
      : 'FAIL 登录没落到该角色首页')
    cleanup(); process.exit(1)
  }
  if (discover) say('（--discover 模式：枚举 tab + 逐个点开 dump 正文，不做判定）')

  let ok = 0
  let bad = 0
  let noData = 0
  let errTabs = 0
  let total = 0
  // 对照用例单独计数，绝不并进上面的 ok/bad —— 详见文件头「对照用例」
  let ctrlTotal = 0
  let ctrlBad = 0

  for (const p of cfg.pages) {
    const s = SEL[p.sel]

    // 先把抽屉关掉：单页 SPA 里上一页的打开状态可能被带过来，会污染后面的 dump。
    // 免登录页没有"别处"可去 —— 拿 dashboard 当跳板会被守卫拦回登录页，那就回不来了。
    if (!cfg.guest) {
      await evaluate(`location.hash = '#/pages/student/dashboard'`)
      await sleep(1200)
    }
    await evaluate(`location.hash = ${JSON.stringify('#' + p.path)}`)
    await waitFor(`document.querySelector(${JSON.stringify(s.root)}) !== null`, 20000)
    await sleep(3000)

    let header = `\n########## ${p.path}  sel=${p.sel}`
    if (p.prep === 'open-student-detail') {
      const opened = await evaluate(`(() => { const el = document.querySelector('.stu-card'); if (!el) return false; el.click(); return true })()`)
      await sleep(3500)
      header += `  prep=open-student-detail opened=${opened}`
    } else {
      header += `  tabs-found=${await evaluate(`document.querySelectorAll(${JSON.stringify(s.item)}).length`)}`
    }
    say(header + ' ##########')

    // label 来源：discover 现场枚举（并打印出来供固化），判定用 CASES 里的固化清单
    let labels
    if (discover) {
      const raw = await evaluate(enumScript(s))
      try { labels = JSON.parse(raw || '[]') } catch { labels = [] }
      say(`  枚举到的 tab: ${JSON.stringify(labels)}`)
      if (!labels.length) { say('  !! 没枚举到 tab —— 这一页的选择器或 prep 有问题'); continue }
    } else {
      labels = Array.isArray(p.tabs) ? p.tabs.map(t => t.label) : []
      if (!labels.length) { say('  !! CASES 里这一页还是 tabs:"auto" —— 先跑 --discover 再固化'); continue }
    }

    // 对照用例不进分母 —— 它们问的是"判据还灵不灵"，不是"页面好不好"
    const controlLabels = labels.filter(l => {
      const d = Array.isArray(p.tabs) ? p.tabs.find(t => t.label === l) : null
      return !!(d && d.control)
    })
    total += labels.length - controlLabels.length
    // 从默认 tab 起步，保证后续每次点击都是从"已有一个 tab 选中"的状态切过去
    await evaluate(clickScript(labels[0], s))
    await sleep(2500)

    for (const label of labels) {
      const declared = Array.isArray(p.tabs) ? p.tabs.find(t => t.label === label) : null
      const want = declared ? declared.data : ''
      const absent = (declared && declared.absent) || []
      logs = []

      // via 是先决条件：先切到它出发的那个 tab，等入口元素出现，再走应用自己的路径
      if (declared && declared.from) {
        await evaluate(clickScript(declared.from, s))
        await sleep(3000)
      }
      if (declared && declared.waitFor) {
        await waitFor(`document.querySelector(${JSON.stringify(declared.waitFor)}) !== null`, 20000)
      }

      const isControl = !!(declared && declared.control)
      const raw = declared && declared.via
        ? await evaluate(declared.via)
        : await evaluate(clickScript(label, s))
      let info = {}
      try { info = JSON.parse(raw || '{}') } catch {}

      // 对照用例等不来任何数据（它本来就不该切），别白等那 6 秒
      if (!discover && want) await waitFor(`document.body.innerText.includes(${JSON.stringify(want)})`, 20000)
      else if (isControl) await sleep(800)
      else await sleep(6000)
      await sleep(isControl ? 300 : 2000)

      const active = await evaluate(activeScript(s))
      // 判定用**全文**，不截断：早期版本在这里 slice 了 900 字符，
      // 而带 hero 卡的页面光 hero 就占满了 ——「本专业」tab 因此被判成"没数据"，
      // 其实内容只是排在 hero 下面被切掉了。判据建在被截断的正文上等于自欺。
      const body = await evaluate(
        p.scope
          ? `(() => { const el = document.querySelector(${JSON.stringify(p.scope)}); return el ? el.innerText.replace(/\\n{2,}/g,'\\n').trim() : '(scope 未命中 ' + ${JSON.stringify(p.scope)} + ')' })()`
          : `document.body.innerText.replace(/\\n{2,}/g, '\\n').trim()`
      )
      const text = String(body || '')
      const errors = logs.filter(l => l.startsWith('[error]'))

      // 死 tab 判定：点了（或走了 via）但 active 态没落在本 label 上 = 没切过去
      const switched = !!active && (active === label || active.startsWith(label))
      const hasData = want ? text.includes(want) : null
      const leaked = absent.filter(t => text.includes(t))
      // 不该出现的过滤目标还在，说明筛选没生效 —— 但注意"没出现"不等于"筛对了"，
      // 只能说这一格没有倒出别类的数据
      const absentOK = leaked.length === 0

      // 打印窗口：命中判据时给判据附近的 12 行（比"正文前 22 行"有用得多 ——
      // 前 22 行通常只是 hero），没命中就给开头
      const allLines = text.split('\n')
      let from = 0
      const anchor = want && hasData ? want : (leaked[0] || '')
      if (anchor) {
        const i = allLines.findIndex(l => l.includes(anchor))
        if (i >= 0) from = Math.max(0, i - 5)
      }
      const excerpt = allLines.slice(from, from + 22)

      const pass = info.ok === true && switched && hasData !== false && absentOK
      // 对照用例判据反过来：它**必须**切不过去。切过去了有两种可能，
      // 都得人来查 —— 判据坏了（恒真），或页面真的长出了一个同名控件。
      const controlBad = isControl && switched
      if (isControl) {
        ctrlTotal += 1
        if (controlBad) ctrlBad += 1
      } else if (pass) ok += 1
      else bad += 1
      if (!want && !isControl) noData += 1
      // errTabs 数的是「有 tab 报错」，对照用例不是 tab —— 它就算报了错也照原样打印出来，
      // 但不混进那个数里（否则汇总里的分母对不上）
      if (errors.length && !isControl) errTabs += 1

      const verdict = discover ? 'DUMP'
        : isControl ? (controlBad ? 'CTRL!' : 'CTRL')
          : (pass ? 'OK  ' : 'MISS')
      say('')
      say(`${verdict} ${p.path}  tab「${label}」  clicked=${info.ok === true} active=「${active}」 switched=${switched}` +
          `  absent=${absent.length ? (absentOK ? 'OK' : '泄漏 ' + JSON.stringify(leaked)) : '—'}` +
          (want ? `  数据「${want}」=${hasData}` : '  [无数据判据]'))
      if (isControl) say(controlBad
        ? '     ↑ 对照用例判据失效：它**本不该**切过去，却切过去了 —— 先查 switched 这条判据本身'
        : '     ↑ 对照用例：期望的"未切换"已判出，说明 switched 这条判据是活的（不计入通过率）')
      if (!info.ok) say(`     没点到: ${info.why || '?'}${info.items ? '  可选: ' + JSON.stringify(info.items) : ''}`)
      if (info.how) say(`     经由: ${info.how}`)
      say(`     --- 正文（共 ${allLines.length} 行，从第 ${from + 1} 行起）---`)
      for (const line of excerpt) say(`     | ${line}`)
      if (errors.length) {
        say('     --- console.error ---')
        for (const e of errors.slice(0, 6)) say(`     ! ${e}`)
      }
    }
  }

  say('')
  if (discover) {
    say(`==== ${role}: dump 完毕，共 ${total} 个 tab ====`)
  } else {
    say(`==== ${role}: ${ok}/${total} 个 tab 通过，${bad} 个未通过，${errTabs} 个有 console.error ====`)
    if (noData) say(`注意：其中 ${noData} 个 tab **没有数据判据**，通过只代表"切过去了且没崩"，不代表"有真数据"。`)
    if (ctrlTotal) {
      say(`另跑过 ${ctrlTotal} 个**对照用例**（故意切不过去的那一格）：` +
        (ctrlBad
          ? `${ctrlBad} 个没判出来 —— 判定逻辑坏了，上面那句「通过」不可信。`
          : '全部如预期判为未切换，「点击没反应 = 失败」这条判据是活的。'))
      say('对照不进通过率：它问的是判据灵不灵，不是页面好不好。')
    }
  }

  writeFileSync(`xcx-tabs-${role}.txt`, lines.join('\n') + '\n', 'utf8')
  console.log(`已写出 xcx-tabs-${role}.txt`)

  cleanup()
  // 对照用例判错 = 判据本身坏了，这一跑的数字不能用，**必须**非零退出
  // （否则 verify-all 会把一份"自己都不可信"的报告记成 OK）
  process.exit(discover ? 0 : ((bad || ctrlBad) ? 1 : 0))
}

main().catch(e => { say(`FAIL ${e.message}`); cleanup(); process.exit(1) })
