// W14：教师「推荐选课」弹窗里那块「我发过的推荐」——**用真浏览器 + DOM 断言**。
//
// 为什么必须上 DOM：本仓有「数据对、渲染错」的先例（接口/库/控制台全正常，只有 DOM 抓得到）。
// `myRecs` 是这一轮**新加的渲染分支**，`v-if="myRecs.length"` 一旦写错，接口再对页面也是空的。
//
// 为什么用**响应替换**（Fetch.fulfillRequest）而不是打真后端：
//   现役 teacher-service(8090) 跑的是**旧 jar**，根本没有 GET /teachers/courses/recommendations。
//   我要证的是**渲染层**：给它两条真形状的推荐，它就得渲染出两行；给空数组，那整块就得消失。
//   接口本身的真值已由 curl 端到端（8192）+ MockMvc 单测钉过。
//
// 四条手工教训（W9 记的）都用上了：
//   ① 抓页面自己那次请求（Fetch.requestPaused），不自己重拼 URL 去 fetch；
//   ② 别指望 `location.hash=` 重发请求 —— 这里靠**关弹窗再开**触发新请求（recommend() 里调 loadMyRecs）；
//   ③ 「我改了响应」必须先自证：E0 断言 fulfillCount>0 排在所有结论断言之前；
//   ④ 拦截 pattern 写窄，只匹配那一条 URL。
//
// 用法: node probe-teacher-course-recommend-ui.mjs [账号] [密码]
// 前置: 5173(vite) + 8076(网关) + user-service + teacher-service(旧包即可) 在跑，且该教师名下**至少有一门课**
// 退出码 0=全过 / 1=有失败
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

// teacher001 = 刘教授 = users.id 18，名下课程 6（操作系统）。这条写死是为了让"用错了人"
// 必然表现为失败，而不是悄悄换个人跑过。
const USER = process.argv[2] || 'teacher001'
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

// 服务端要"喂"给页面的 payload（真形状：ApiResponse，data 是数组）
const CANNED_TWO = [
  { id: 11, courseId: 6, courseName: '操作系统', credits: 3.5, targetClassName: '计算机2102班', reason: '与你们的方向很契合', status: 1 },
  { id: 12, courseId: 9, courseName: '计算机网络', credits: 3.0, targetClassName: null, reason: '打基础用', status: 1 },
]
let CANNED = CANNED_TWO
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
  if (!target) return done(1, 'Chrome 调试端口未就绪')

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })

  let id = 0
  const pending = new Map()
  let jsErrors = []
  const send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })

  ws.onmessage = async (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params.exceptionDetails
      jsErrors.push((d?.exception?.description || d?.text || '').split('\n')[0].slice(0, 160))
    } else if (m.method === 'Fetch.requestPaused') {
      const p = m.params
      if (p.request.url.includes('/teachers/courses/recommendations')) {
        fulfillCount++
        const body = Buffer.from(JSON.stringify({ code: 200, message: 'success', success: true, data: CANNED })).toString('base64')
        await send('Fetch.fulfillRequest', {
          requestId: p.requestId, responseCode: 200,
          responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
          body: body,
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
  for (let i = 0; i < 30; i++) {
    await sleep(500)
    path = await evaluate('location.pathname')
    if (path && path !== '/login') break
  }
  if (!path || path === '/login') return done(1, `login failed for ${USER}`)
  console.log(`logged in ${USER} -> ${path}`)

  // ---------------- 开拦截，进课程页 ----------------
  await send('Fetch.enable', {
    patterns: [{ urlPattern: '*teachers/courses/recommendations*', requestStage: 'Request' }],
  })
  await send('Page.navigate', { url: `${APP}/teacher/courses` })
  await sleep(2500)

  const btns = await evaluate(`document.querySelectorAll('.tcc-acts .el-button').length`)
  const recBtns = await evaluate(`[...document.querySelectorAll('.tcc-acts .el-button')].filter(b => b.textContent.trim() === '推荐选课').length`)
  console.log(`course cards action buttons = ${btns}, of which 推荐选课 = ${recBtns}`)
  if (!recBtns) return done(1, `no 推荐选课 button found -- teacher ${USER} has no course card?`)

  const openDialog = async () => {
    await evaluate(`(() => {
      const b = [...document.querySelectorAll('.tcc-acts .el-button')].find(x => x.textContent.trim() === '推荐选课')
      if (b) b.click(); return !!b
    })()`)
    await sleep(900)
  }
  const closeDialog = async () => {
    await evaluate(`(() => {
      const b = [...document.querySelectorAll('.el-dialog__footer .el-button')].find(x => x.textContent.trim() === '取消')
      if (b) b.click(); return !!b
    })()`)
    await sleep(500)
  }

  // ---------------- 正向：喂两条，页面要渲染两行 ----------------
  console.log('\n[CASE 1] two recommendations stubbed -> dialog must render two rows')
  const before = fulfillCount
  await openDialog()
  // E0：先自证"我确实改到了响应"，再谈结论
  check('E0 interception actually fired (fulfillCount increased)', fulfillCount > before,
    `before=${before} after=${fulfillCount}`)

  const head1 = await evaluate(`document.querySelector('.myrec-head')?.textContent?.trim() || null`)
  const rows1 = await evaluate(`[...document.querySelectorAll('.myrec-item')].map(el => ({
    course: el.querySelector('.myrec-course')?.textContent?.trim() || '',
    scope: el.querySelector('.myrec-scope')?.textContent?.trim() || '',
    reason: el.querySelector('.myrec-reason')?.textContent?.trim() || ''
  }))`)
  const domJson = JSON.stringify(rows1)
  console.log(`    head = ${JSON.stringify(head1)}`)
  console.log(`    rows = ${domJson}`)

  check('C1 块头显示条数（我发过的推荐（2））', head1 === '我发过的推荐（2）', `actual ${JSON.stringify(head1)}`)
  check('C2 渲染出 2 行（DOM 行数 === 喂进去的条数）', Array.isArray(rows1) && rows1.length === 2,
    `rows=${Array.isArray(rows1) ? rows1.length : 'n/a'}`)
  check('C3 第一行课程名来自响应（操作系统）', rows1?.[0]?.course === '操作系统', `actual ${JSON.stringify(rows1?.[0]?.course)}`)
  check('C4 第一行推荐理由来自响应', rows1?.[0]?.reason === '与你们的方向很契合', `actual ${JSON.stringify(rows1?.[0]?.reason)}`)
  check('C5 有 targetClass 的显示班名', rows1?.[0]?.scope === '计算机2102班', `actual ${JSON.stringify(rows1?.[0]?.scope)}`)
  // targetClassName 在真后端里是**键缺失**（MyBatis 不吐 null 键）—— 这里显式喂 null，
  // 断言前端把它渲染成「全部班级」而不是 null/空
  check('C6 target 为 null 的显示「全部班级」（不是 null/空）', rows1?.[1]?.scope === '全部班级',
    `actual ${JSON.stringify(rows1?.[1]?.scope)}`)

  await closeDialog()

  // ---------------- 负向对照：喂空数组，整块必须消失 ----------------
  console.log('\n[CASE 2] empty array stubbed -> the whole block must disappear (v-if)')
  CANNED = []
  const before2 = fulfillCount
  await openDialog()
  check('E0b interception fired again', fulfillCount > before2, `before=${before2} after=${fulfillCount}`)
  const head2 = await evaluate(`document.querySelector('.myrec-head')?.textContent?.trim() || null`)
  const rows2 = await evaluate(`document.querySelectorAll('.myrec-item').length`)
  console.log(`    head = ${JSON.stringify(head2)}  rows = ${rows2}`)
  check('C7 空数组时块头消失', head2 === null, `actual ${JSON.stringify(head2)}`)
  check('C8 空数组时没有残留行', rows2 === 0, `rows=${rows2}`)
  // 负向对照的另一半：对话框本身还在（不是"整个弹窗没打开"造成的假绿）
  const dialogOpen = await evaluate(`!!document.querySelector('.el-dialog') && getComputedStyle(document.querySelector('.el-dialog')).display !== 'none'`)
  check('C9 负向对照有效：弹窗确实开着（不是没打开导致的假绿）', dialogOpen === true, `dialogOpen=${dialogOpen}`)

  const realErrs = jsErrors.filter(e => !/ResizeObserver|favicon/i.test(e))
  check('C10 过程中没有 JS 异常', realErrs.length === 0, realErrs.join(' | ') || '(none)')

  const failed = results.filter(r => !r.ok)
  done(failed.length ? 1 : 0, `assertions failed: ${failed.length} / ${results.length}`)
}

main().catch((e) => done(1, `probe crashed: ${e?.message || e}`))
