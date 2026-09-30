// 小程序 H5 通道（127.0.0.1:5180）**五端逐页**冒烟 —— 一个脚本覆盖全部角色。
//
// 回答的问题：**这些页面在真实登录态下是不是都渲染出了真实数据**，
// 而不只是"能编译过"。编译通过只证明语法对，证明不了页面拿到的是真数据 ——
// 一个把所有请求都 catch 成 [] 的页面同样能编译通过，但点开是空的。
//
// 为什么收敛成一个脚本（2026-09-29 合并）：
//   之前有 3 份近乎复制粘贴的逐页脚本，但它们**判据强度不一样** ——
//   admin / college-admin 两支有「数据判据」（正文里必须出现只有真数据才可能的字面量）
//   + 「禁项」（出现了就一定错的东西）；teacher 那支只有「到达判据」，而且它的
//   expect 串多是「平均分」「教师资料」这类**静态 UI 标签** —— 页面全空也会出现。
//   结果 teacher 报了 10/10 全过，实际证明力远低于同行的另外两支，
//   而报告里两个数字长得一样。判据强度不统一的"全绿"是会骗人的，故合并为一支。
//
// 两级判据（每一页都要过两级才算通过）：
//   expect —— 到达判据。用该页标题之外、**独有**的正文。
//   data   —— 数据判据。用只有真数据才可能出现的字面量（人名/班名/条数）。
//             取不到就 MISS。这是抓"接口通了但页面是空态"的那一级。
// FORBIDDEN —— 出现了就一定错的字面量。只放真正的缺陷信号，
//             别把页面自己的解释性文案列进来（会自己撞自己，是假警报）。
//
// 用法:
//   node xcx-page-smoke.mjs <role>              # role ∈ student|teacher|counselor|admin|college-admin
//   node xcx-page-smoke.mjs <role> --discover   # 只逐页 dump 正文，不判定
//                                               # （用来给还没定过判据的页面**从实机取**字面量，
//                                               #   别凭想象猜 expect 串 —— 猜错会得到假 MISS）
//   node xcx-page-smoke.mjs college-admin collegeadmin2 123456   # 覆盖账号/口令
// 输出: xcx-pages-<role>.txt（UTF-8，避免控制台中文乱码）
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.XCX_BASE || 'http://127.0.0.1:5180'

const role = process.argv[2]
const discover = process.argv.includes('--discover')
// 允许位置参数覆盖账号（用户名是第 3 个非选项参数，口令是第 4 个）
const positional = process.argv.slice(2).filter(a => !a.startsWith('--'))

/**
 * 每个角色的账号、落地判据、逐页判据。
 *
 * 账号一列**必须按活库实情**：活库的辅导员只有 `1000000001`，
 * 没有 counselor001；学生没有 student001。照种子 SQL 抄会登不进去。
 *
 * data 里的字面量全部来自实机 dump（`--discover` 跑出来的正文），
 * 不是照库表想象的 —— 页面渲染的是接口给的字段，不等于库里的列。
 */
/**
 * 登录落地判据：**必须同时**看到 token 与目标角色的 hash。
 *
 * 为什么不能再用各角色自己拼的串：学生那支原先写的是
 *   `hash.includes('/pages/student/') || body.includes('学业预警')`
 * 而后半句在**登录页自己身上就成立**（品牌标题是"智能学业预警服务系统"）。
 * 于是 waitFor 立刻返回、根本没等跳转，紧接着就把 hash 改到 dashboard ——
 * 与登录页那个 1200ms 的 setTimeout + reLaunch 撞在一起，
 * dashboard 的请求被中途重挂，统计卡全渲染成 `--`，
 * 冒烟于是把「首页空着」误报成一页未通过。判据能被被测对象自己满足，
 * 就不是判据；两个条件取与，且都不出现在登录页上。
 */
function LANDED(role) {
  return `(localStorage.getItem('token') ? 1 : 0) === 1`
    + ` && (location.hash || '').includes('/pages/${role}/')`
}

const ROLES = {
  student: {
    user: '2023020616',   // 司明
    pass: '123456',
    landed: LANDED('student'),
    pages: [
      { path: '/pages/student/dashboard', expect: '学分进度', data: '已修 17 / 120 学分' },
      { path: '/pages/student/scores', expect: '课程数', data: '大学英语' },
      { path: '/pages/student/warnings', expect: '预警总数', data: '司明同学已累计3门课程不及格' },
      { path: '/pages/student/profile', expect: '学籍信息', data: '2023020616' },
      { path: '/pages/student/assistance', expect: '计划总数', data: '每周两次辅导答疑' },
      { path: '/pages/student/appeals', expect: '申诉总数', data: '申诉原因' },
      { path: '/pages/student/benchmark-analysis', expect: '学业位次', data: '/ 24' },
      { path: '/pages/student/class-ranking', expect: '班级成绩榜', data: '21/24' },
      { path: '/pages/student/notification-center', expect: '条未读通知', data: '帮扶计划已制定' },
      { path: '/pages/student/assistance-feedback', expect: '可以评价的帮扶计划', data: '进度 50%' },
      // 设置页全是开关，没有"只有真数据才出现"的字面量 —— 留空并让报告显式计入
      // 「没有数据判据」那一栏，别假装它被验过了。
      { path: '/pages/student/settings', expect: '消息接收', data: '' }
    ],
    forbidden: []
  },

  teacher: {
    user: '10001',
    pass: '123456',
    landed: LANDED('teacher'),
    pages: [
      { path: '/pages/teacher/dashboard', expect: '待办事项', data: '带 2 个班 · 24 名学生' },
      { path: '/pages/teacher/scores', expect: '标准差', data: '76.90' },
      { path: '/pages/teacher/students', expect: '有挂科', data: '2023020524' },
      { path: '/pages/teacher/credit-prediction', expect: '全班学分进度', data: '毕业要求' },
      { path: '/pages/teacher/courses', expect: '修读人数', data: '高等数学基础课程' },
      { path: '/pages/teacher/warnings', expect: '只读视图', data: '李爽同学已累计3门课程不及格' },
      { path: '/pages/teacher/analysis', expect: '标准差', data: '24/24 人已出成绩' },
      { path: '/pages/teacher/feedback-management', expect: '未回复', data: '数据结构的课后作业太难了' },
      { path: '/pages/teacher/class-management', expect: '我的班级', data: '空班' },
      { path: '/pages/teacher/profile', expect: '教师资料', data: '13397583736@qq.com' }
    ],
    // 教师端的预警处理接口不落库，页面故意不做处理入口。
    forbidden: [
      { path: '/pages/teacher/warnings', text: '标记处理' },
      { path: '/pages/teacher/warnings', text: '确认处理' }
    ]
  },

  counselor: {
    user: '1000000001',   // 司明（辅导员身份，与学生司明同名不同人）
    pass: '123456',
    landed: LANDED('counselor'),
    pages: [
      { path: '/pages/counselor/dashboard', expect: '辅导员工作台', data: '学生工作办公室' },
      { path: '/pages/counselor/warnings', expect: '涉及学生', data: '李爽' },
      { path: '/pages/counselor/students', expect: '学生总数', data: '挂 3 门' },
      { path: '/pages/counselor/assistance', expect: '计划总数', data: '针对李爽同学的学业帮扶计划' },
      { path: '/pages/counselor/analytics', expect: '学分不足率', data: '达标班级' },
      { path: '/pages/counselor/scores', expect: '班级均分', data: '软工2306班' },
      { path: '/pages/counselor/credit-monitor', expect: '达标率的分母', data: '计科2306班' },
      { path: '/pages/counselor/courses', expect: '成绩记录', data: '计算机基础' },
      { path: '/pages/counselor/class-management', expect: '管理班级', data: '软件工程2101班' },
      { path: '/pages/counselor/profile', expect: '辅导员资料', data: '学生工作办公室' }
    ],
    forbidden: [
      // 2026-09-29 修掉的一个真实 off-by-one：副标题曾写 `classOptions.length`
      // 而 hero 卡写 `classOptions.length - 1`，同一屏同时显示「2 个班」和「班级 1」。
      // 演示数据里该辅导员名下只有 计科2306班 有学生，所以「2 个班」必然是错的。
      { path: '/pages/counselor/students', text: '· 2 个班' }
    ]
  },

  admin: {
    user: 'admin',
    pass: '123456',
    landed: LANDED('admin'),
    pages: [
      { path: '/pages/admin/dashboard', expect: '待办事项', data: '全平台数据' },
      { path: '/pages/admin/users', expect: '个账号', data: 'collegeadmin1' },
      { path: '/pages/admin/permissions', expect: '账号权限查询', data: 'ROLE_STUDENT' },
      { path: '/pages/admin/colleges', expect: '共 4 个学院', data: '艺术设计学院' },
      { path: '/pages/admin/courses', expect: '共 10 门课程', data: '数据结构' },
      { path: '/pages/admin/rules', expect: '共 4 条规则', data: 'fail_count > 5' },
      { path: '/pages/admin/warnings', expect: '待处理', data: '中级预警（RULE002）' },
      { path: '/pages/admin/class-requests', expect: '9 条申请', data: '软件工程2101班' },
      { path: '/pages/admin/profile', expect: '平台概览', data: 'admin@academic.edu' }
    ],
    forbidden: []
  },

  'college-admin': {
    user: 'collegeadmin1',   // 张建国，软件学院
    pass: '123456',
    landed: LANDED('college-admin'),
    pages: [
      { path: '/pages/college-admin/dashboard', expect: '本院概览', data: '软件学院' },
      { path: '/pages/college-admin/students', expect: '名学生', data: '计科2306班' },
      { path: '/pages/college-admin/teachers', expect: '位教师', data: '于凤海' },
      { path: '/pages/college-admin/counselors', expect: '位辅导员', data: '司明' },
      { path: '/pages/college-admin/classes', expect: '个班', data: '计科2306班' },
      { path: '/pages/college-admin/warnings', expect: '待处理', data: '' },
      { path: '/pages/college-admin/scores', expect: '成绩条数', data: '238' },
      { path: '/pages/college-admin/assistance', expect: '帮扶情况', data: '' },
      { path: '/pages/college-admin/profile', expect: '管理档案', data: '张建国' }
    ],
    // 曾有一条「本系统对学院管理员不可用」的禁项：网关把 role=5 限死在
    // /api/college-admin + /api/admin 时，改密码必然 403，页面只好那么写。
    // 网关按"仅限本人 id"放行之后结论反了 —— 现在**不该**再出现那句话。
    forbidden: [
      { path: '/pages/college-admin/profile', text: '本系统对学院管理员不可用' }
    ]
  }
}

const cfg = ROLES[role]
if (!cfg) {
  console.error(`role 必须是 ${Object.keys(ROLES).join(' | ')} 之一，收到: ${role ?? '(空)'}`)
  process.exit(2)
}
const USER = positional[1] || cfg.user
const PASS = positional[2] || cfg.pass
const PAGES = cfg.pages
const FORBIDDEN = cfg.forbidden
const OUT = `xcx-pages-${role}.txt`

const PORT = 9700 + Math.floor(Math.random() * 200)
const profile = mkdtempSync(join(tmpdir(), 'cdpx-'))
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
  /** 页面自己 console.error/warn 出来的内容。分页清空，避免上一页的报错算到下一页头上。 */
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

  // —— 登录 ——
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
    setVal(byPh('请输入学号或工号'), ${JSON.stringify(USER)})
    setVal(byPh('请输入密码'), ${JSON.stringify(PASS)})
    setVal(byPh('输入验证码'), (document.querySelector('.captcha-code')?.textContent || '').trim())
    document.querySelector('.btn')?.click()
    return true
  })()`)

  const landed = await waitFor(cfg.landed, 30000)
  const gotRole = await evaluate(`localStorage.getItem('role')`)
  const name = await evaluate(`localStorage.getItem('userName')`)
  say(`角色 ${role}  账号 ${USER}`)
  say(`登录结果: landed=${landed} role=${gotRole} userName=${name}`)
  if (!landed) { say('FAIL 登录没落到该角色首页 —— 后面的页面结果不可信'); cleanup(); process.exit(1) }
  if (discover) say('（--discover 模式：只 dump 正文，不做判定）')

  let ok = 0
  let miss = 0
  let errPages = 0

  for (const p of PAGES) {
    logs = []
    await evaluate(`location.hash = ${JSON.stringify('#' + p.path)}`)
    // 到达判据为空时（还没定过判据的页），只等一段固定时间。
    if (p.expect) {
      await waitFor(`document.body.innerText.includes(${JSON.stringify(p.expect)})`, 20000)
    } else {
      await sleep(4000)
    }
    // 到达后再停一下，让并发的多个请求都打完（页面是并行发请求的）
    await sleep(2500)

    const body = await evaluate(`document.body.innerText.replace(/\\n{2,}/g, '\\n').trim().slice(0, 900)`)
    const text = String(body || '')
    const errors = logs.filter(l => l.startsWith('[error]'))
    const warns = logs.filter(l => l.startsWith('[warning]'))

    const arrived = p.expect ? text.includes(p.expect) : true
    // 数据判据：空串表示这一页还没定过可靠的字面量，跳过（在报告里显式标出，别当通过）
    let hasData = null
    if (p.data) hasData = text.includes(p.data)

    const hit = []
    for (const f of FORBIDDEN.filter(f => f.path === p.path)) {
      if (text.includes(f.text)) hit.push(f.text)
    }

    const pass = arrived && hasData !== false && !hit.length
    if (pass) ok += 1; else miss += 1
    if (errors.length) errPages += 1

    const flags = []
    if (!p.expect) flags.push('无到达判据')
    if (!p.data) flags.push('无数据判据')
    const verdict = discover ? 'DUMP' : (pass ? 'OK  ' : 'MISS')

    say('')
    say(`${verdict} ${p.path}` +
        (p.expect ? `  到达「${p.expect}」=${arrived}` : '') +
        (p.data ? `  数据「${p.data}」=${hasData}` : '') +
        (flags.length ? `  [${flags.join('/')}]` : ''))
    if (hit.length) say(`     禁项命中: ${hit.join(' / ')}`)
    say('     --- 正文 ---')
    for (const line of text.split('\n').slice(0, 26)) say(`     | ${line}`)
    if (errors.length) {
      say('     --- console.error ---')
      for (const e of errors.slice(0, 6)) say(`     ! ${e}`)
    }
    if (warns.length) {
      say(`     --- console.warn (${warns.length} 条，前 2) ---`)
      for (const w of warns.slice(0, 2)) say(`     ~ ${w}`)
    }
  }

  const noData = PAGES.filter(p => !p.data).length
  say('')
  if (discover) {
    say(`==== ${role}: dump 完毕，共 ${PAGES.length} 页 ====`)
  } else {
    say(`==== ${role}: ${ok}/${PAGES.length} 页通过，${miss} 页未通过，${errPages} 页有 console.error ====`)
    if (noData) say(`注意：其中 ${noData} 页**没有数据判据**，通过只代表"没崩"，不代表"有真数据"。`)
  }

  writeFileSync(OUT, lines.join('\n') + '\n', 'utf8')
  console.log(`已写出 ${OUT}`)

  cleanup()
  process.exit(miss ? 1 : 0)
}

main().catch(e => { say(`FAIL ${e.message}`); cleanup(); process.exit(1) })
