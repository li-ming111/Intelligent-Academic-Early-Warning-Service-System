// 教师端**写路径**验证（PC 端 5173，浏览器内 fetch 复用登录态）。
//
// ⚠️ 这个脚本会真改库。跑之前必须先快照：
//     mysqldump -uroot -proot --default-character-set=utf8mb4 --skip-comments \
//       academic_warning_system score_record scores score_audit_log feedback \
//       > db_backups/teacher_write_probe_YYYYMMDD_before.sql
//   跑完立刻执行 db_backups/teacher_write_probe_20260929_rollback.sql + verify 脚本。
//
// 为什么需要它：teacher-probe.mjs 刻意只跑 GET（写路径会动数据）。
// 于是"成绩增删改真实落库"这句话里，只有"增"和"删"被验过 ——
// 而恰恰是"改"（PUT /teachers/scores/{id}）和"回复反馈"
// （POST /teachers/feedbacks/{id}/reply）两个写接口长期 500：
//   · 改分：审计日志的 student_id / teacher_id 是 NOT NULL，而调用方传了个
//     只有分数和评语的裸对象进去 → 插入失败 → @Transactional 整体回滚；
//   · 回复：SQL 里写的是 update_time 之外的 reply_time 列，那列不存在。
//
// 用法: node teacher-write-probe.mjs [用户名] [密码] [scoreRecordId] [feedbackId]
//   scoreRecordId 默认 277（司明 · 高等数学 · 第3学期 · 87 分）
//   feedbackId    默认 1  （司明 · 教学建议 · 待回复）
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || '10001'
const PASS = process.argv[3] || '123456'
const SCORE_ID = process.argv[4] || '277'
const FEEDBACK_ID = process.argv[5] || '1'

// 写入值刻意与现值不同，回滚脚本才能用"是否等于这个值"来确认改真的落库了
const NEW_SCORE = 88
const NEW_REMARK = '写路径验证-临时'
const NEW_REPLY = '写路径验证-临时回复'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9700 + Math.floor(Math.random() * 100)

const profile = mkdtempSync(join(tmpdir(), 'cdptw-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const cleanup = () => {
  try { chrome.kill() } catch {}
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
}

let failures = 0
const ok = (m) => console.log(`  ✅ ${m}`)
const fail = (m) => { failures++; console.log(`  ❌ ${m}`) }

const main = async () => {
  let target = null
  for (let i = 0; i < 80; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      target = list.find((t) => t.type === 'page')
      if (target) break
    } catch {}
    await sleep(250)
  }
  if (!target) { console.log('FAIL  Chrome 调试端口未就绪'); cleanup(); process.exit(1) }

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })

  let id = 0
  const pending = new Map()
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id) }
  }
  const send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) throw new Error(r.result.exceptionDetails.text)
    return r.result?.result?.value
  }
  const waitFor = async (expr, timeoutMs = 45000) => {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      try { if (await evaluate(expr)) return true } catch {}
      await sleep(350)
    }
    return false
  }

  await send('Page.enable')
  await send('Runtime.enable')

  // ---------- 登录 ----------
  await send('Page.navigate', { url: `${APP}/login` })
  if (!await waitFor(`!!document.querySelector('.lr-submit')`)) {
    console.log('FAIL  登录页未渲染'); cleanup(); process.exit(1)
  }
  const filled = await evaluate(`(() => {
    const setVal = (sel, val) => {
      const el = document.querySelector(sel); if (!el) return false
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set
      setter.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    const a = setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(USER)})
    const b = setVal('input[placeholder="请输入密码"]', ${JSON.stringify(PASS)})
    setVal('input[placeholder="验证码"]', (document.querySelector('.lr-captcha-btn')?.textContent || '').trim())
    const btn = document.querySelector('.lr-submit'); if (btn) btn.click()
    return { a, b, clicked: !!btn }
  })()`)
  if (!filled?.a || !filled?.clicked) { console.log(`FAIL  登录表单没填上 ${JSON.stringify(filled)}`); cleanup(); process.exit(1) }

  let path = ''
  for (let i = 0; i < 60; i++) {
    await sleep(500)
    path = await evaluate('location.pathname')
    if (path && !path.includes('/login')) break
  }
  if (!path || path.includes('/login')) {
    console.log(`FAIL  登录未成功（当前 ${path}）`); cleanup(); process.exit(1)
  }
  const uname = await evaluate(`localStorage.getItem('name')`)
  console.log(`PASS  已登录为 ${uname}，落地 ${path}\n`)

  // ---------- 写操作 + 回读 ----------
  const out = await evaluate(`(async () => {
    const token = localStorage.getItem('token')
    const H = { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }
    const call = async (method, url, body) => {
      const r = await fetch(url, { method, headers: H, body: body === undefined ? undefined : JSON.stringify(body) })
      const txt = await r.text()
      let j = null
      try { j = JSON.parse(txt) } catch {}
      return {
        status: r.status,
        code: j && j.code,
        message: j && String(j.message).slice(0, 120),
        data: j && (typeof j.data === 'object' ? JSON.stringify(j.data).slice(0, 200) : String(j.data)),
        raw: j ? txt : null
      }
    }
    const r1 = await call('PUT', '/api/teachers/scores/${SCORE_ID}', {
      score: ${NEW_SCORE}, remark: ${JSON.stringify(NEW_REMARK)}, modifiedBy: '写路径验证'
    })
    await new Promise(r => setTimeout(r, 400))
    const r2 = await call('POST', '/api/teachers/feedbacks/${FEEDBACK_ID}/reply', {
      reply: ${JSON.stringify(NEW_REPLY)}
    })
    await new Promise(r => setTimeout(r, 400))
    // 回读：确认写入真的落库、而且**读接口自己也看得到**（不只是写接口说成功）
    const r3 = await call('GET', '/api/teachers/scores?course_id=1')
    const r4 = await call('GET', '/api/teachers/messages/29')

    // 从回读结果里把那一行捞出来，避免"接口说成功、库里其实没变"
    let readScore = null, readRemark = null
    try {
      const arr = JSON.parse(r3.raw).data || []
      const row = arr.find(x => String(x.id) === '${SCORE_ID}')
      if (row) { readScore = row.score; readRemark = row.remark }
    } catch {}
    let readStatus = null, readReply = null
    try {
      const arr = JSON.parse(r4.raw).data || []
      const msg = arr.find(x => String(x.id) === '${FEEDBACK_ID}')
      if (msg) { readStatus = msg.status; readReply = msg.reply }
    } catch {}

    return {
      r1: { status: r1.status, code: r1.code, message: r1.message, data: r1.data },
      r2: { status: r2.status, code: r2.code, message: r2.message, data: r2.data },
      r3: { status: r3.status, code: r3.code },
      r4: { status: r4.status, code: r4.code },
      readScore, readRemark, readStatus, readReply
    }
  })()`)

  const check = (name, r) => {
    if (r.status === 200 && (r.code === 200 || r.code === 0)) {
      ok(`${name} → HTTP ${r.status} code=${r.code} data=${JSON.stringify(r.data)}`)
    } else {
      fail(`${name} → HTTP ${r.status} code=${r.code} msg=${r.message}`)
    }
  }
  check(`PUT /teachers/scores/${SCORE_ID}（改分）`, out.r1)
  check(`POST /teachers/feedbacks/${FEEDBACK_ID}/reply（回复）`, out.r2)
  check('/teachers/scores?course_id=1（回读）', out.r3)
  check('/teachers/messages/29（回读）', out.r4)

  // 落库断言：读接口返回的值必须就是刚写进去的
  if (Number(out.readScore) === NEW_SCORE) ok(`回读 score_record#${SCORE_ID} 的 score = ${out.readScore}（= 刚写入的 ${NEW_SCORE}）`)
  else fail(`回读 score_record#${SCORE_ID} 的 score = ${JSON.stringify(out.readScore)}，期望 ${NEW_SCORE}`)
  if (out.readRemark === NEW_REMARK) ok(`回读 remark = ${JSON.stringify(out.readRemark)}`)
  else fail(`回读 remark = ${JSON.stringify(out.readRemark)}，期望 ${JSON.stringify(NEW_REMARK)}`)
  if (Number(out.readStatus) === 1) ok(`回读 feedback#${FEEDBACK_ID} 的 status = 1（已回复）`)
  else fail(`回读 feedback#${FEEDBACK_ID} 的 status = ${JSON.stringify(out.readStatus)}，期望 1`)
  if (out.readReply === NEW_REPLY) ok(`回读 reply = ${JSON.stringify(out.readReply)}`)
  else fail(`回读 reply = ${JSON.stringify(out.readReply)}，期望 ${JSON.stringify(NEW_REPLY)}`)

  console.log(failures ? `\nFAIL  写路径有 ${failures} 项不通过` : '\nPASS  改分与回复都返回成功，且读接口回读到的就是写入的值')
  console.log('提示：现在去跑 db_backups 里的回滚脚本，把 score_record/scores/score_audit_log/feedback 复原。')
  cleanup()
  process.exit(failures ? 1 : 0)
}

main().catch((e) => { console.log(`FAIL  脚本异常: ${e.message}`); cleanup(); process.exit(1) })
