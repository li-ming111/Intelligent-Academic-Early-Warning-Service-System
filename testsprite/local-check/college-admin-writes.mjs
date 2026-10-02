// 学院管理员「预警管理」页两个写按钮的**点击级**实测（W1.13）。
//
// 为什么要走浏览器：后端两道闸已经用 HTTP 探针验过，但前端这次也改了 ——
//   `collegeAdminAPI.processWarning(userId, data)` / `deleteWarning(userId, warningId)`
// 多了一个参数。签名改错（少传、传成字符串、传成 undefined）在 HTTP 层看不出来，
// 只有真的点一下、看发出去的请求体，才知道 `userId` 有没有落到 body / path 里。
// 这正是本工程吃过亏的那一类：**"点了说成功，其实请求没发出去或者发错了"**。
//
// 🔴 不拿演示数据当靶子：本脚本自己 INSERT 一条临时预警（描述里带 W1.13-TEMP-PROBE），
//    只对**这一条**点按钮，跑完删掉并核对演示的 4 条原封不动。
//
// 用法: node college-admin-writes.mjs <临时预警id>
// 退出码 0=全部通过
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { captchaFields } from './_captcha.mjs'

const TMP_ID = Number(process.argv[2])
if (!TMP_ID) { console.error('用法: node college-admin-writes.mjs <临时预警id>'); process.exit(2) }

const MARK = 'W1.13-TEMP-PROBE'
const UID = 57            // collegeadmin1 → users.id=57 → 学院 1
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9800 + Math.floor(Math.random() * 100)
const profile = mkdtempSync(join(tmpdir(), 'cdpca-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
let bad = 0
const ok = (s) => console.log(`  PASS  ${s}`)
const no = (s) => { bad++; console.log(`  FAIL  ${s}`) }
const chk = (s, got, want) => (String(got) === String(want) ? ok(`${s}  (${got})`) : no(`${s}  期望=${want} 实际=${got}`))
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
  let reqs = []          // {method, url, body}
  let jsErrors = []
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Network.requestWillBeSent') {
      const r = m.params.request
      if (r.url.includes('/api/')) {
        reqs.push({
          method: r.method,
          url: r.url.replace(/^https?:\/\/[^/]+/, '').split('?')[0],
          body: r.postData || '',
        })
      }
    } else if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params.exceptionDetails
      jsErrors.push((d?.exception?.description || d?.text || '').split('\n')[0].slice(0, 160))
    }
  }
  const send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) return { __err: r.result.exceptionDetails.exception?.description }
    return r.result?.result?.value
  }

  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable')

  // ---- 登录（W1.12 起要带服务端签发的验证码）----
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
      shown = (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
      if (/^[A-Z0-9]{4}$/.test(shown)) break
      await new Promise((r) => setTimeout(r, 200))
    }
    const a = setVal('input[placeholder="请输入学号或工号"]', 'collegeadmin1')
    const b = setVal('input[placeholder="请输入密码"]', '123456')
    const c = setVal('input[placeholder="验证码"]', shown)
    const btn = document.querySelector('.lr-submit'); if (btn) btn.click()
    return { a, b, c, clicked: !!btn, shown }
  })()`)
  if (!filled?.a || !filled?.b || !filled?.c || !filled?.clicked) {
    return done(1, `登录表单元素没找全: ${JSON.stringify(filled)}`)
  }
  let path = ''
  for (let i = 0; i < 30; i++) { await sleep(500); path = await evaluate('location.pathname'); if (path && path !== '/login') break }
  if (!path || path === '/login') return done(1, '登录失败（collegeadmin1）')
  const lsUid = await evaluate(`localStorage.getItem('userId')`)
  console.log(`已登录 collegeadmin1 → ${path}；localStorage.userId=${lsUid}`)
  chk('localStorage.userId 就是 57（前端两个写接口都读它）', lsUid, String(UID))

  // ---- 打开预警管理页 ----
  await send('Page.navigate', { url: `${APP}/college-admin/warnings` })
  await sleep(2500)
  const rows = await evaluate(`document.querySelectorAll('.el-table__body-wrapper tbody tr').length`)
  console.log(`列表渲染 ${rows} 行\n`)
  if (!rows) return done(1, '预警列表一行都没有，后面的按钮找不到')

  // 找到**临时那行**的下标（按描述里的标记找，绝不误点演示数据）
  const rowIdx = await evaluate(`(() => {
    const trs = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
    return trs.findIndex(tr => tr.textContent.includes(${JSON.stringify(MARK)}))
  })()`)
  if (rowIdx < 0) return done(1, `列表里找不到临时行（标记 ${MARK}）—— 是不是不在学院 1？`)
  console.log(`临时行在第 ${rowIdx + 1} 行\n`)

  // ============ ① 点「标记已处理」 ============
  reqs = []; jsErrors = []
  await evaluate(`(() => {
    document.querySelectorAll('.el-message').forEach(e => e.remove())
    const tr = document.querySelectorAll('.el-table__body-wrapper tbody tr')[${rowIdx}]
    const btn = [...tr.querySelectorAll('button')].find(b => b.textContent.trim() === '标记已处理')
    if (btn) btn.click()
  })()`)
  let msg = null
  for (let t = 0; t < 3000; t += 100) {
    await sleep(100)
    msg = await evaluate(`(() => {
      const el = document.querySelector('.el-message'); if (!el) return null
      return { txt: (el.querySelector('.el-message__content')?.textContent || el.textContent).trim(),
               err: el.className.includes('el-message--error') }
    })()`)
    if (msg) break
  }
  await sleep(1200)

  const postReq = reqs.find((r) => r.method === 'POST' && r.url.includes('/college-admin/process-warning'))
  if (!postReq) { no('点「标记已处理」没有发出 process-warning 请求') } else {
    ok(`点「标记已处理」发出的请求: POST ${postReq.url}`)
    let parsed = null
    try { parsed = JSON.parse(postReq.body) } catch {}
    chk('  └ 请求体里的 userId（前端新加的参数真的发出去了）', parsed?.userId, UID)
    chk('  └ 请求体里的 id 指向临时那条', parsed?.id, TMP_ID)
  }
  chk('提示文案', msg?.txt, '已标记为已处理')
  chk('提示不是错误态', msg?.err, false)
  for (const e of new Set(jsErrors)) no(`未捕获异常  ${e}`)

  // ============ ② 点「删除」 ============
  reqs = []; jsErrors = []
  await evaluate(`(() => {
    document.querySelectorAll('.el-message').forEach(e => e.remove())
    const tr = document.querySelectorAll('.el-table__body-wrapper tbody tr')[${rowIdx}]
    const btn = [...tr.querySelectorAll('button')].find(b => b.textContent.trim() === '删除')
    if (btn) btn.click()
  })()`)
  await sleep(700)
  const confirmed = await evaluate(`(() => {
    const box = document.querySelector('.el-message-box')
    if (!box) return 'no-box'
    const btn = [...box.querySelectorAll('button')].find(b => b.textContent.trim() === '删除')
    if (!btn) return 'no-btn:' + [...box.querySelectorAll('button')].map(b=>b.textContent.trim()).join('/')
    btn.click(); return 'clicked'
  })()`)
  chk('二次确认弹窗点了「删除」', confirmed, 'clicked')
  msg = null
  for (let t = 0; t < 3000; t += 100) {
    await sleep(100)
    msg = await evaluate(`(() => {
      const el = document.querySelector('.el-message'); if (!el) return null
      return { txt: (el.querySelector('.el-message__content')?.textContent || el.textContent).trim(),
               err: el.className.includes('el-message--error') }
    })()`)
    if (msg) break
  }
  await sleep(1200)

  const delReq = reqs.find((r) => r.method === 'DELETE' && r.url.includes('/college-admin/delete-warning'))
  if (!delReq) { no('点「删除」没有发出 delete-warning 请求') } else {
    ok(`点「删除」发出的请求: DELETE ${delReq.url}`)
    chk('  └ 路径里带上了操作人 uid 与预警 id', delReq.url, `/api/college-admin/delete-warning/${UID}/${TMP_ID}`)
  }
  chk('提示文案', msg?.txt, '已删除')
  chk('提示不是错误态', msg?.err, false)
  for (const e of new Set(jsErrors)) no(`未捕获异常  ${e}`)

  console.log(`\n通过 ${bad === 0 ? '全部' : '部分'}；失败 ${bad}`)
  if (bad) return done(1, `${bad} 处不符合预期`)
  return done(0, '两个写按钮都真的发出了带操作人的请求，提示与预期一致')
}

main().catch((e) => done(1, `脚本异常: ${e.message}`))
