// 批量冒烟：一次登录，逐页整页导航，报告每页的
//   (a) 未捕获 JS 异常 / console.error   (b) 命中 /api/ 的 4xx·5xx   (c) 正文长度
// 用途：抓"接口挂了但前端把错误吞成空态/暂无数据"这类**看着正常其实没数据**的页面
// —— 单看页面文字是查不出来的，必须看网络层。
// 用法: node smoke-pages.mjs <角色key> [用户名] [密码]
//   角色key ∈ student | teacher | counselor | admin | college-admin
// 退出码 0=全清, 1=有异常或 5xx
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

// ⚠️ 这个脚本打的是 **PC 前端（5173）**，不是小程序 H5（5180）。
// 所以下面的清单是 PC 端的路由，别拿小程序 src/pages.json 的页名往里填 ——
// 两端的页名高度重叠但**不是同一套**（PC 有 statistics/data-export/majors，
// 小程序有 class-requests/credit-monitor/benchmark-analysis）。
// 小程序的页面清单与检测走 testsprite/local-check/ 里另建的 xcx 脚本。
// ⚠️ 清单里只放**路由表真有的**页。2026-10-03（W8.3）删掉两个幻影页：
//   student/notification-center —— NotificationCenter 组件其实挂在 `messages` 上，
//        `notification-center` 这条路由不存在（导航里也没有）；列着它只会导航到空页 ⇒ 假红。
//   teacher/feedback-management —— 已删的孤儿路由（见 router/index.js 顶部注释），
//        现在只有 `feedback`。同理假红。
// 2026-10-18（W48）：清单一度比 router **少 4 条真路由**（student/timetable、
//   teacher/timetable、admin/course-plans、admin/appeals）—— 前三条正是本轮新加的排课/课表，
//   于是"五个角色全绿"这句结论**根本没覆盖新功能**。判法：拿 router/index.js 的
//   `path:` 逐条与这份 PAGES 对差（别信"上次也是全绿"）。新加的页一律补进来。
const PAGES = {
  student: ['dashboard','scores','warnings','assistance','appeals','messages',
            'benchmark-analysis','assistance-feedback',
            'class-members','class-ranking','timetable','settings','statistics'],
  teacher: ['dashboard','scores','warnings','analysis','credit-prediction','audit-log',
            'feedback','courses','timetable','class-management','messages','settings'],
  counselor: ['dashboard','students','warnings','assistance','scores','analytics','courses',
              'credit-monitor','notifications','communication','class-management','settings'],
  admin: ['dashboard','colleges','majors','users','rules','statistics','warnings','courses',
          'course-plans','permissions','messages','data-export','class-management','appeals',
          'settings'],
  'college-admin': ['dashboard','students','teachers','warnings','classes','analysis',
                    'counselors','export','scores'],
}
const DEFAULTS = {
  student: '2023020616', teacher: 'teacher001', counselor: '1000000001',
  admin: 'admin', 'college-admin': 'collegeadmin1',
}

const ROLE = process.argv[2]
if (!PAGES[ROLE]) {
  console.error(`用法: node smoke-pages.mjs <${Object.keys(PAGES).join('|')}> [用户名] [密码]`)
  process.exit(2)
}
const USER = process.argv[3] || DEFAULTS[ROLE]
const PASS = process.argv[4] || '123456'
const PREFIX = '/' + ROLE

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9600 + Math.floor(Math.random() * 100)
const profile = mkdtempSync(join(tmpdir(), 'cdps-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const done = (code, msg) => {
  console.log(`\n${code === 0 ? 'PASS' : 'FAIL'}  ${msg}`)
  try { chrome.kill() } catch {}
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
  process.exit(code)
}

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
  if (!target) return done(1, 'Chrome 调试端口未就绪')

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })

  let id = 0
  const pending = new Map()
  let events = []            // 当前页累积的事件，每页导航前清空
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Network.responseReceived') {
      const r = m.params.response
      if (r.status >= 400 && r.url.includes('/api/')) {
        events.push({ kind: 'HTTP ' + r.status, text: r.url.replace(/^https?:\/\/[^/]+/, '').split('?')[0] })
      }
    } else if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params.exceptionDetails
      events.push({ kind: '未捕获异常', text: (d?.exception?.description || d?.text || '').split('\n')[0].slice(0, 140) })
    } else if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') {
      const t = (m.params.args || []).map((a) => a.value ?? a.description ?? '').join(' ').split('\n')[0]
      if (t && !t.includes('favicon')) events.push({ kind: 'console.error', text: t.slice(0, 140) })
    }
  }
  const send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    return r.result?.result?.value
  }

  await send('Page.enable')
  await send('Runtime.enable')
  await send('Network.enable')

  // ---- 登录（验证码要读页面显示的那个，别硬编码）----
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
      // W1.12：验证码改由服务端签发，按钮会先显示「加载中…」——必须等它变成一个
      // 4 位码再读，否则填进去的是「加载中…」，症状是"登录失败"，看着像账号坏了。
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
    return { a, b, c, captcha: shown, clicked: !!btn }
  })()`)
  if (!filled?.a || !filled?.b || !filled?.c || !filled?.clicked) {
    return done(1, `登录表单元素没找全: ${JSON.stringify(filled)}`)
  }
  let path = ''
  for (let i = 0; i < 30; i++) {
    await sleep(500)
    path = await evaluate('location.pathname')
    if (path && path !== '/login') break
  }
  if (!path || path === '/login') return done(1, `登录失败，仍停在 /login（账号 ${USER}，验证码填的是 "${filled.captcha}"）`)
  console.log(`已登录 ${USER} → ${path}\n`)

  // ---- 逐页冒烟 ----
  const rows = []
  let bad = 0
  for (const p of PAGES[ROLE]) {
    const url = `${APP}${PREFIX}/${p}`
    events = []
    await send('Page.navigate', { url })
    await sleep(2600)
    const landed = await evaluate('location.pathname')
    const info = await evaluate(`(() => {
      const t = (document.body.innerText || '').replace(/\\s+/g, ' ').trim()
      const mask = document.querySelector('.el-loading-mask')
      // echarts 画在 <canvas> 上，不进 innerText —— 正文长度看不出图表有没有画出来。
      // 只统计"有尺寸"的 canvas，排除隐藏容器里的空壳。
      const canvases = Array.from(document.querySelectorAll('canvas'))
        .filter(c => c.width > 0 && c.height > 0).length
      return { len: t.length, head: t.slice(0, 60), stuckLoading: !!(mask && mask.offsetParent), canvases }
    })()`)
    const uniq = []
    for (const ev of events) if (!uniq.some((u) => u.kind === ev.kind && u.text === ev.text)) uniq.push(ev)
    const hard = uniq.filter((e) => e.kind === '未捕获异常' || /^HTTP 5/.test(e.kind))
    // ⚠️ W48：`landed` 原来只是**记录**、从不参与判定 —— 于是"请求的页被静默重定向到别处"
    //   （路由守卫把角色不符/别的原因弹回 dashboard）在报表里**长得和 ✓ 完全一样**，
    //   而且只会记下 landing 页的数据，等于那一格根本没验目标页。
    //   ⇒ 现在进入判定：落点不是请求的 URL 路径就算失败。
    const expected = `${PREFIX}/${p}`
    const redirected = !!landed && landed !== expected
    if (hard.length || !info || info.len < 30 || redirected) bad++
    rows.push({ page: p, landed, expected, redirected, len: info?.len ?? 0, stuck: info?.stuckLoading, evs: uniq, head: info?.head || '', canvases: info?.canvases ?? 0 })
  }

  // ---- 报表 ----
  for (const r of rows) {
    const badRow = r.evs.some((e) => e.kind === '未捕获异常' || /^HTTP 5/.test(e.kind)) || r.len < 30 || r.redirected
    const tag = badRow ? '✗' : (r.evs.length ? '·' : '✓')
    const redir = r.redirected ? `⚠被重定向到 ${r.landed}（目标 ${r.expected}）` : ''
    console.log(`${tag} ${(PREFIX + '/' + r.page).padEnd(34)} 正文${String(r.len).padStart(5)}字  ${r.canvases ? `图${r.canvases} ` : ''}${r.stuck ? '⚠加载遮罩未消失 ' : ''}${redir}${redir ? '' : r.head}`)
    for (const e of r.evs) console.log(`      ${e.kind.padEnd(14)} ${e.text}`)
  }
  console.log(`\n通过 ${rows.length - bad}/${rows.length}`)
  if (bad) {
    // 失败原因别写死成"异常/5xx/正文过短"——W48 加了"被重定向"这一档之后，
    // 那句总结会把重定向说成别的东西（打靶时实测：9 页全因重定向判失败，
    // 结尾却打印"存在未捕获异常 / 5xx / 正文过短"）。按实际原因归类。
    const why = []
    if (rows.some(r => r.redirected)) why.push(`${rows.filter(r => r.redirected).length} 个被重定向`)
    if (rows.some(r => r.evs.some(e => e.kind === '未捕获异常'))) why.push('未捕获异常')
    if (rows.some(r => r.evs.some(e => /^HTTP 5/.test(e.kind)))) why.push('5xx')
    if (rows.some(r => r.len < 30)) why.push('正文过短')
    return done(1, `${bad} 个页面不合格：${why.join(' / ')}`)
  }
  return done(0, `全部 ${rows.length} 个页面无异常`)
}

main().catch((e) => done(1, `脚本异常: ${e.message}`))
