// W14：学生端首页那块「老师推荐选课」——**用真浏览器 + DOM 断言**。
//
// 为什么必须上 DOM：本仓有「数据对、渲染错」的先例（接口/库/控制台全正常，只有 DOM 抓得到）。
// 这块面板是这一轮**新加的渲染分支**，`v-if="recommendations.length"` / `slice(0,3)` 写错，
// 接口再对页面也是错的。
//
// 为什么用**响应替换**（Fetch.fulfillRequest）：现役 student-service(8079) 跑的是旧 jar，
// 没有 GET /students/recommendations/{id}。接口本身的真值已由 curl 端到端（8191）+ MockMvc 单测钉过；
// 这里只证**渲染层**。
//
// 三条手工教训（W9）都用上：
//   ① 抓页面自己那次请求（Fetch.requestPaused），不自己重拼 URL；
//   ② 这块数据在**页面挂载时**就拉了（Promise.allSettled）—— 所以必须**先开拦截再导航**，
//      换 payload 重来时用 Page.reload({ignoreCache:true})（同文档 hash 导航不会重发请求）；
//   ③ 先自证"我确实改到了响应"（E0 fulfillCount>0），再谈结论。
//
// 用法: node probe-student-course-recommend-ui.mjs [学号] [密码]
// 前置: 5173(vite) + 8076(网关) + user-service + student-service(旧包即可) 在跑
// 退出码 0=全过 / 1=有失败
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

// 2023020616 = 司明 = users.id 27（class 1）。写死是为了让"换了人"必然翻红。
const USER = process.argv[2] || '2023020616'
const PASS = process.argv[3] || '123456'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9700 + Math.floor(Math.random() * 100)
const profile = mkdtempSync(join(tmpdir(), 'cdpp-'))
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
const results = []
const check = (name, ok, detail) => {
  results.push({ name, ok })
  console.log(`  ${ok ? 'OK ' : 'XX '} ${name}${detail ? `\n      ${detail}` : ''}`)
}

const TWO = [
  { id: 1, courseId: 1, courseName: '高等数学', credits: 4.0, reason: '这门课对你们的专业方向很有用', teacherName: '于凤海' },
  { id: 2, courseId: 4, courseName: '数据结构', credits: 4.0, reason: '打好编程底子', teacherName: '于凤海' },
]
const FOUR = [
  ...TWO,
  { id: 3, courseId: 6, courseName: '操作系统', credits: 3.5, reason: 'R', teacherName: '刘教授' },
  { id: 4, courseId: 9, courseName: '计算机网络', credits: 3.0, reason: 'R', teacherName: '刘教授' },
]
let CANNED = TWO
let fulfillCount = 0

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
  if (!target) return done(1, 'Chrome debug port not ready')

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })

  let id = 0
  const pending = new Map()
  let jsErrors = []
  let net = 0
  const send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })

  ws.onmessage = async (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params.exceptionDetails
      jsErrors.push((d?.exception?.description || d?.text || '').split('\n')[0].slice(0, 160))
    } else if (m.method === 'Network.requestWillBeSent' || m.method === 'Network.responseReceived') {
      net++
    } else if (m.method === 'Fetch.requestPaused') {
      const p = m.params
      if (p.request.url.includes('/students/recommendations/')) {
        fulfillCount++
        const body = Buffer.from(JSON.stringify({ code: 200, message: 'success', success: true, data: CANNED })).toString('base64')
        await send('Fetch.fulfillRequest', {
          requestId: p.requestId, responseCode: 200,
          responseHeaders: [{ name: 'Content-Type', value: 'application/json' }], body,
        })
      } else {
        await send('Fetch.continueRequest', { requestId: p.requestId })
      }
    }
  }

  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) return { __err: r.result.exceptionDetails.exception?.description }
    return r.result?.result?.value
  }
  const settle = async (quietMs = 900, maxMs = 12000) => {
    let last = net, quiet = 0
    for (let t = 0; t < maxMs; t += 200) {
      await sleep(200)
      if (net === last) { quiet += 200; if (quiet >= quietMs) return } else { quiet = 0; last = net }
    }
  }
  const readPanel = () => evaluate(`(() => {
    const panel = [...document.querySelectorAll('.panel')].find(p => (p.querySelector('.ph-title')?.textContent || '').includes('老师推荐选课'))
    if (!panel) return { err: 'panel not found' }
    return {
      present: true,
      rows: [...panel.querySelectorAll('.rec-item')].map(el => ({
        course: el.querySelector('.rec-course')?.textContent?.trim() || '',
        teacher: el.querySelector('.rec-teacher')?.textContent?.trim() || '',
        text: el.querySelector('.rec-text')?.textContent?.trim() || ''
      })),
      empty: !!panel.querySelector('.panel-empty'),
      emptyText: panel.querySelector('.panel-empty p')?.textContent?.trim() || null
    }
  })()`)

  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable')

  // ---------------- 登录 ----------------
  await send('Page.navigate', { url: `${APP}/login` })
  await sleep(3000)
  const filled = await evaluate(`(async () => {
    const setVal = (sel, val) => {
      const el = document.querySelector(sel); if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    let shown = ''
    for (let i = 0; i < 60; i++) {
      const t = (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
      if (/^[A-Z0-9]{4}$/.test(t)) { shown = t; break }
      await new Promise((r) => setTimeout(r, 200))
    }
    const a = setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(USER)})
    const b = setVal('input[placeholder="请输入密码"]', ${JSON.stringify(PASS)})
    const c = setVal('input[placeholder="验证码"]', shown)
    const btn = document.querySelector('.lr-submit'); if (btn) btn.click()
    return { a, b, c, shown, clicked: !!btn }
  })()`)
  if (!filled?.a || !filled?.b || !filled?.c || !filled?.clicked) {
    return done(1, `login form incomplete: ${JSON.stringify(filled)}`)
  }
  let path = ''
  for (let i = 0; i < 30; i++) { await sleep(500); path = await evaluate('location.pathname'); if (path && path !== '/login') break }
  if (!path || path === '/login') return done(1, `login failed for ${USER}`)
  console.log(`logged in ${USER} -> ${path}`)

  // 开拦截**再**导航（这块数据在挂载时就拉）
  await send('Fetch.enable', { patterns: [{ urlPattern: '*students/recommendations/*', requestStage: 'Request' }] })

  // ---------------- CASE 1：两条 ----------------
  console.log('\n[CASE 1] two recommendations stubbed -> panel must render two rows')
  // ⚠ 登录成功时 router.push 已经落到 /student/dashboard 并挂载过一次 —— 那次请求可能
  //   恰好在 Fetch.enable 之前发出（于是计数变成 1）。所以先**离开**再回来，强制重新挂载，
  //   这样 `before -> after` 的增量才真的由我这次导航产生。
  await send('Page.navigate', { url: `${APP}/student/scores` })
  await settle()
  const before = fulfillCount
  await send('Page.navigate', { url: `${APP}/student/dashboard` })
  await settle()
  check('E0 interception actually fired', fulfillCount > before, `before=${before} after=${fulfillCount}`)
  const p1 = await readPanel()
  console.log(`    panel = ${JSON.stringify(p1)}`)
  check('C1 面板存在', p1?.present === true, JSON.stringify(p1))
  check('C2 渲染出 2 行（DOM 行数 === 喂进去的条数）', Array.isArray(p1?.rows) && p1.rows.length === 2, `rows=${p1?.rows?.length}`)
  check('C3 课程名来自响应（高等数学）', p1?.rows?.[0]?.course === '高等数学', `actual ${JSON.stringify(p1?.rows?.[0]?.course)}`)
  check('C4 推荐理由来自响应', p1?.rows?.[0]?.text === '这门课对你们的专业方向很有用', `actual ${JSON.stringify(p1?.rows?.[0]?.text)}`)
  check('C5 教师名渲染成「于凤海 老师推荐」', p1?.rows?.[0]?.teacher === '于凤海 老师推荐', `actual ${JSON.stringify(p1?.rows?.[0]?.teacher)}`)
  check('C6 有数据时不显示空态', p1?.empty === false, `empty=${p1?.empty}`)

  // ---------------- CASE 2：空数组（负向对照） ----------------
  console.log('\n[CASE 2] empty array stubbed -> empty state must show, no rows')
  CANNED = []
  const before2 = fulfillCount
  await send('Page.reload', { ignoreCache: true })
  await settle()
  check('E0b interception fired again', fulfillCount > before2, `before=${before2} after=${fulfillCount}`)
  const p2 = await readPanel()
  console.log(`    panel = ${JSON.stringify(p2)}`)
  check('C7 空数组时显示空态「暂无老师推荐」', p2?.empty === true && p2?.emptyText === '暂无老师推荐',
    `empty=${p2?.empty} text=${JSON.stringify(p2?.emptyText)}`)
  check('C8 空数组时没有残留行', p2?.rows?.length === 0, `rows=${p2?.rows?.length}`)

  // ---------------- CASE 3：四条 -> 只显示前 3（slice 契约） ----------------
  console.log('\n[CASE 3] four recommendations stubbed -> panel must cap at 3 rows')
  CANNED = FOUR
  const before3 = fulfillCount
  await send('Page.reload', { ignoreCache: true })
  await settle()
  check('E0c interception fired again', fulfillCount > before3, `before=${before3} after=${fulfillCount}`)
  const p3 = await readPanel()
  console.log(`    rows = ${JSON.stringify(p3?.rows?.map(r => r.course))}`)
  check('C9 喂 4 条只渲染 3 条（slice(0,3) 真的生效）', p3?.rows?.length === 3, `rows=${p3?.rows?.length}`)
  check('C10 前三条的课程名顺序正确', JSON.stringify(p3?.rows?.map(r => r.course)) === JSON.stringify(['高等数学', '数据结构', '操作系统']),
    JSON.stringify(p3?.rows?.map(r => r.course)))

  const realErrs = jsErrors.filter(e => !/ResizeObserver|favicon/i.test(e))
  check('C11 过程中没有 JS 异常', realErrs.length === 0, realErrs.join(' | ') || '(none)')

  const failed = results.filter(r => !r.ok)
  done(failed.length ? 1 : 0, `assertions failed: ${failed.length} / ${results.length}`)
}

main().catch((e) => done(1, `probe crashed: ${e?.message || e}`))
