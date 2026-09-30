// ⚠️ 已被 xcx-page-smoke.mjs 取代（2026-09-29）。保留仅为对照历史结论，别再加用例。
// 管理员端 9 页（小程序 H5 通道，127.0.0.1:5180）逐页冒烟。
//
// 与 college-admin-pages-xcx.mjs 同构，只换角色与清单。
// 为什么 P7 也要跑这一趟：学院管理员端跑出来 2 个编译期查不出的真 bug
// （缺 API 方法名、网关 403 被 catch 成"页面全 0"）—— 那两个页面**编译都是过的**。
// 编译通过只说明语法对，说明不了页面上有真数据。
//
// 用法: node admin-pages-xcx.mjs [用户名] [密码]   默认 admin / 123456
// 输出: admin-pages-xcx.txt
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || 'admin'
const PASS = process.argv[3] || '123456'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.XCX_BASE || 'http://127.0.0.1:5180'
const PORT = 9900 + Math.floor(Math.random() * 100)

/**
 * 9 页。
 * expect —— 到达判据：该页独有的**区域标题**，不能只用导航栏标题
 *           （否则"只有导航栏、正文全空"的页面也判到达）。
 * data   —— 数据判据：只有真数据才会出现的字面量，取自 2026-09-29 实跑出来的库内实情
 *           （35 个账号 / 4 个学院 / 10 门课程 / 4 条规则 / 4 条预警 / 9 条班级申请）。
 */
const PAGES = [
  { path: '/pages/admin/dashboard',      expect: '待办事项',     data: '全平台数据' },
  { path: '/pages/admin/users',          expect: '个账号',       data: 'collegeadmin1' },
  { path: '/pages/admin/permissions',    expect: '账号权限查询', data: 'ROLE_STUDENT' },
  { path: '/pages/admin/colleges',       expect: '共 4 个学院',  data: '艺术设计学院' },
  { path: '/pages/admin/courses',        expect: '共 10 门课程', data: '数据结构' },
  { path: '/pages/admin/rules',          expect: '共 4 条规则',  data: 'fail_count > 5' },
  { path: '/pages/admin/warnings',       expect: '待处理',       data: '中级预警（RULE002）' },
  { path: '/pages/admin/class-requests', expect: '9 条申请',     data: '软件工程2101班' },
  { path: '/pages/admin/profile',        expect: '平台概览',     data: 'admin@academic.edu' }
]

const profile = mkdtempSync(join(tmpdir(), 'cdpadm-'))
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
    setVal(byPh('请输入学号或工号'), ${JSON.stringify(USER)})
    setVal(byPh('请输入密码'), ${JSON.stringify(PASS)})
    setVal(byPh('输入验证码'), (document.querySelector('.captcha-code')?.textContent || '').trim())
    document.querySelector('.btn')?.click()
    return true
  })()`)

  const landed = await waitFor(
    `(location.hash || '').includes('/pages/admin/') || document.body.innerText.includes('管理控制台')`,
    30000
  )
  const role = await evaluate(`localStorage.getItem('role')`)
  const name = await evaluate(`localStorage.getItem('userName')`)
  say(`登录结果: landed=${landed} role=${role} userName=${name}`)
  if (!landed) { say('FAIL 登录没落到管理员首页 —— 后面的页面结果不可信'); cleanup(); process.exit(1) }

  let ok = 0
  let miss = 0
  let errPages = 0

  for (const p of PAGES) {
    logs = []
    await evaluate(`location.hash = ${JSON.stringify('#' + p.path)}`)
    const arrived = p.expect
      ? await waitFor(`document.body.innerText.includes(${JSON.stringify(p.expect)})`, 20000)
      : true
    await sleep(2500)

    const body = await evaluate(`document.body.innerText.replace(/\\n{2,}/g, '\\n').trim().slice(0, 900)`)
    const errors = logs.filter(l => l.startsWith('[error]'))
    const warns = logs.filter(l => l.startsWith('[warning]'))

    let hasData = null
    if (p.data) {
      hasData = await evaluate(`document.body.innerText.includes(${JSON.stringify(p.data)})`)
    }

    const pass = arrived && hasData !== false
    const status = pass ? 'OK  ' : 'MISS'
    if (pass) ok += 1; else miss += 1
    if (errors.length) errPages += 1

    say('')
    say(`${status} ${p.path}  到达判据「${p.expect}」${p.data ? ` 数据判据「${p.data}」=${hasData}` : ''}`)
    say('     --- 正文 ---')
    for (const line of String(body || '').split('\n').slice(0, 28)) say(`     | ${line}`)
    if (errors.length) {
      say('     --- console.error ---')
      for (const e of errors.slice(0, 8)) say(`     ! ${e}`)
    }
    if (warns.length) {
      say(`     --- console.warn (${warns.length} 条，前 2) ---`)
      for (const w of warns.slice(0, 2)) say(`     ~ ${w}`)
    }
  }

  say('')
  say(`==== 小计: ${ok}/${PAGES.length} 页通过，${miss} 页未通过，${errPages} 页有 console.error ====`)

  writeFileSync('admin-pages-xcx.txt', lines.join('\n') + '\n', 'utf8')
  console.log('已写出 admin-pages-xcx.txt')

  cleanup()
  process.exit(0)
}

main().catch(e => { say(`FAIL ${e.message}`); cleanup(); process.exit(1) })
