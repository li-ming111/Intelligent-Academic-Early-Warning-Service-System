// ⚠️ 已被 xcx-page-smoke.mjs 取代（2026-09-29）。保留仅为对照历史结论，别再加用例。
// 学院管理员端 9 页（小程序 H5 通道，127.0.0.1:5180）逐页冒烟。
//
// 回答的问题：**这 9 个页面在真实登录态下是不是都渲染出了真实数据**，
// 而不只是"能编译过"。编译通过只证明语法对，证明不了页面拿到的是真数据 ——
// 一个把所有请求都 catch 成 [] 的页面同样能编译通过，但点开是空的。
//
// 与 P8 那支 `college-admin-probe.mjs` 的分工：
//   那支验的是**接口层**（18 个只读路径 code=200、第一参 users.id 透传对不对）；
//   这支验的是**页面层**（数据有没有落到 DOM 上）。
// 接口通、页面空，是这类项目最常见的失败形态，两者都要跑。
//
// 每页两个判据：
//   expect —— 到达判据，用该页**独有**的正文（不能只用导航栏标题，
//             否则一个"只有导航栏、正文全空"的页面也会被判为到达）；
//   data   —— 数据判据，用一个**只有真数据才可能出现**的字面量
//             （班名/人名/条数）。取不到就 MISS，专门抓"接口通了但页面是空态"。
//
// 用法: node college-admin-pages-xcx.mjs [用户名] [密码]
//   默认用软件学院那个账号（有学生有成绩的那个）；电子工程学院是空架子，
//   拿它跑会把"本来就没数据"误报成失败。
// 输出: college-admin-pages-xcx.txt（UTF-8，避免控制台中文乱码）
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || 'collegeadmin1'
const PASS = process.argv[3] || '123456'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.XCX_BASE || 'http://127.0.0.1:5180'
const PORT = 9800 + Math.floor(Math.random() * 100)

/**
 * 9 页。data 里的字面量来自 2026-09-29 用真实账号打出来的库内实情：
 * 软件学院（collegeadmin1 / users.id=57）有 6 个班但只有「计科2306班」有人，
 * 24 名学生、238 条成绩、5 位教师（含于凤海）、1 位辅导员（司明）、4 条预警。
 */
const PAGES = [
  { path: '/pages/college-admin/dashboard',  expect: '本院概览',  data: '软件学院' },
  { path: '/pages/college-admin/students',   expect: '名学生',    data: '计科2306班' },
  { path: '/pages/college-admin/teachers',   expect: '位教师',    data: '于凤海' },
  { path: '/pages/college-admin/counselors', expect: '位辅导员',  data: '司明' },
  { path: '/pages/college-admin/classes',    expect: '个班',      data: '计科2306班' },
  { path: '/pages/college-admin/warnings',   expect: '待处理',    data: '' },
  { path: '/pages/college-admin/scores',     expect: '成绩条数',  data: '238' },
  { path: '/pages/college-admin/assistance', expect: '帮扶情况',  data: '' },
  { path: '/pages/college-admin/profile',    expect: '管理档案',  data: '张建国' }
]

/**
 * 学院管理员端**不该出现**的东西。
 *
 * 这里只放"出现了就一定是错的"字面量 —— 别把解释性文案（比如页面自己写的
 * "不是辅导员总数"）也列进来，那会自己撞自己，是假警报。
 *
 * ⚠️ 这条清单本身也变过：2026-09-29 网关修好之前，role=5 被限死在
 * `/api/college-admin/**` + `/api/admin/**`，`/api/auth/change-password` 必然 403，
 * 当时这里有一条「确认新密码」禁项。网关按"仅限本人"放行之后那条禁项就反了 ——
 * 改密码表单**应该**在，「改不了密码」的说明文案才是不该出现的。
 * 禁项跟着结论走，不是跟着当初的写法走。
 */
const FORBIDDEN = [
  { path: '/pages/college-admin/profile', text: '本系统对学院管理员不可用' }
]

const profile = mkdtempSync(join(tmpdir(), 'cdpca-'))
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
  /** 页面自己 console.error/warn 的内容。分页清空，避免上一页的报错算到下一页头上。 */
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

  const landed = await waitFor(
    `(location.hash || '').includes('/pages/college-admin/') || document.body.innerText.includes('学院管理')`,
    30000
  )
  const role = await evaluate(`localStorage.getItem('role')`)
  const name = await evaluate(`localStorage.getItem('userName')`)
  say(`登录结果: landed=${landed} role=${role} userName=${name}`)
  if (!landed) { say('FAIL 登录没落到学院管理员首页 —— 后面的页面结果不可信'); cleanup(); process.exit(1) }

  let ok = 0
  let miss = 0
  let errPages = 0

  for (const p of PAGES) {
    logs = []
    await evaluate(`location.hash = ${JSON.stringify('#' + p.path)}`)
    const arrived = await waitFor(
      `document.body.innerText.includes(${JSON.stringify(p.expect)})`,
      20000
    )
    // 到达后再停一下，让并发的多个请求都打完（页面是并行发请求的）
    await sleep(2500)

    const body = await evaluate(`document.body.innerText.replace(/\\n{2,}/g, '\\n').trim().slice(0, 700)`)
    const errors = logs.filter(l => l.startsWith('[error]'))
    const warns = logs.filter(l => l.startsWith('[warning]'))

    // 数据判据：空串表示这一页没有可靠的字面量，跳过
    let hasData = null
    if (p.data) {
      hasData = await evaluate(`document.body.innerText.includes(${JSON.stringify(p.data)})`)
    }

    // 禁项检查
    const hit = []
    for (const f of FORBIDDEN.filter(f => f.path === p.path)) {
      const has = await evaluate(`document.body.innerText.includes(${JSON.stringify(f.text)})`)
      if (has) hit.push(f.text)
    }

    const pass = arrived && hasData !== false && !hit.length
    const status = pass ? 'OK  ' : 'MISS'
    if (pass) ok += 1; else miss += 1
    if (errors.length) errPages += 1

    say('')
    say(`${status} ${p.path}  到达判据「${p.expect}」${p.data ? ` 数据判据「${p.data}」=${hasData}` : ' （无数据判据）'}`)
    if (hit.length) say(`     禁项命中: ${hit.join(' / ')}`)
    say('     --- 正文 ---')
    for (const line of String(body || '').split('\n').slice(0, 22)) say(`     | ${line}`)
    if (errors.length) {
      say('     --- console.error ---')
      for (const e of errors.slice(0, 6)) say(`     ! ${e}`)
    }
    if (warns.length) {
      say(`     --- console.warn (${warns.length} 条，前 2) ---`)
      for (const w of warns.slice(0, 2)) say(`     ~ ${w}`)
    }
  }

  say('')
  say(`==== 小计: ${ok}/${PAGES.length} 页通过，${miss} 页未通过，${errPages} 页有 console.error ====`)

  writeFileSync('college-admin-pages-xcx.txt', lines.join('\n') + '\n', 'utf8')
  console.log('已写出 college-admin-pages-xcx.txt')

  cleanup()
  process.exit(miss ? 1 : 0)
}

main().catch(e => { say(`FAIL ${e.message}`); cleanup(); process.exit(1) })
