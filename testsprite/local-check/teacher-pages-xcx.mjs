// ⚠️ 已被 xcx-page-smoke.mjs 取代（2026-09-29）。保留仅为对照历史结论，别再加用例。
// 教师端 10 页（小程序 H5 通道）逐页冒烟。
//
// 回答的问题：**这 10 个页面在真实登录态下是不是都渲染出了真实数据**，
// 而不只是"能编译过"。编译通过只证明语法对，证明不了页面拿到的是真数据 ——
// 一个把所有请求都 catch 成 [] 的页面同样能编译通过，但点开是空的。
//
// 做法：用教师账号登录 → 逐页 hash 跳转 → 等页面出现该页**独有**的文案 →
// 记录正文片段 + 期间页面自己 console.error 出来的失败信息。
//
// 判读：
//   - 每页 "OK" 且正文含预期文案 → 该页有真数据
//   - "MISS" 表示等了 20 秒也没出现预期文案，多半是请求失败或数据为空（看 ERR 行）
//   - "ERR" 是页面自己打的失败日志（各页在 catch 里会 console.error('[teacher xxx] …失败:')），
//     这是发现"静默空列表"最直接的信号
//
// 用法: node teacher-pages-xcx.mjs [用户名] [密码]
// 输出: teacher-pages-xcx.txt（UTF-8，避免控制台中文乱码）
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || '10001'
const PASS = process.argv[3] || '123456'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.XCX_BASE || 'http://127.0.0.1:5180'
const PORT = 9700 + Math.floor(Math.random() * 100)

/**
 * 每页的到达判据（expect）选用该页**标题之外**的独有正文，
 * 否则一个"只有导航栏、正文全空"的页面也会被判为到达。
 */
const PAGES = [
  { path: '/pages/teacher/dashboard', expect: '我的班级' },
  { path: '/pages/teacher/scores', expect: '成绩流水' },
  { path: '/pages/teacher/students', expect: '有挂科' },
  { path: '/pages/teacher/credit-prediction', expect: '学分进度' },
  { path: '/pages/teacher/courses', expect: '学分' },
  { path: '/pages/teacher/warnings', expect: '只读视图' },
  { path: '/pages/teacher/analysis', expect: '平均分' },
  { path: '/pages/teacher/feedback-management', expect: '未回复' },
  { path: '/pages/teacher/class-management', expect: '我的班级' },
  { path: '/pages/teacher/profile', expect: '教师资料' }
]

/** 教师端**不应存在**的东西：处理入口（后端那个 process 接口不落库，故意不做）。 */
const FORBIDDEN = [
  { path: '/pages/teacher/warnings', text: '标记处理' },
  { path: '/pages/teacher/warnings', text: '确认处理' }
]

const profile = mkdtempSync(join(tmpdir(), 'cdptp-'))
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
  // ⚠️ 必须连验证码一起等：W1.12 起验证码是服务端异步签发的，「表单元素到位」不再等于「码已到手」
  //    （实测读到空串 ⇒ 服务端 401 拒绝 ⇒ 探针报假「登录未落地」，见 _w4_login_diag.mjs）。
  await waitFor(`document.querySelectorAll('uni-input').length >= 3 && (document.querySelector('.captcha-code')?.textContent || '').trim().length > 0`)
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

  const landed = await waitFor(`(location.hash || '').includes('/pages/teacher/') || document.body.innerText.includes('工作台')`, 30000)
  const role = await evaluate(`localStorage.getItem('role')`)
  const name = await evaluate(`localStorage.getItem('userName')`)
  say(`登录结果: landed=${landed} role=${role} userName=${name}`)

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

    // 禁项检查
    const hit = []
    for (const f of FORBIDDEN.filter(f => f.path === p.path)) {
      const has = await evaluate(`document.body.innerText.includes(${JSON.stringify(f.text)})`)
      if (has) hit.push(f.text)
    }

    const status = arrived && !hit.length ? 'OK  ' : 'MISS'
    if (arrived) ok += 1; else miss += 1
    if (errors.length) errPages += 1

    say('')
    say(`${status} ${p.path}  (期望文案「${p.expect}」)`)
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
  say(`==== 小计: ${ok}/${PAGES.length} 页到达，${miss} 页未到达，${errPages} 页有 console.error ====`)

  writeFileSync('teacher-pages-xcx.txt', lines.join('\n') + '\n', 'utf8')
  console.log('已写出 teacher-pages-xcx.txt')

  cleanup()
  process.exit(0)
}

main().catch(e => { say(`FAIL ${e.message}`); cleanup(); process.exit(1) })
