// 辅导员「通知中心」(/counselor/notifications) 页面级核对（2026-09-30）。
//
// 背景：这条链此前整条是假的 ——
//   发送(POST /counselors/students/notify) 只 log 一行就回 {success:true}，不写任何库；
//   历史(GET /counselors/notifications/history) 恒回 {total:0,list:[]}；
//   周报数恒 0；模板写死 3 条；班级活动写死 2 条（其中一条日期 = now()+3天）。
//   页面上却弹「通知已发送」，历史永远「暂无通知记录」。
//
// 断言口径（沿用本目录其它脚本的做法）：**接口真值 == 页面真实渲染出的数量**。
// 只断言"页面有东西"会被写死的假数据骗过；只断言"接口有数据"则证明不了渲染。
//
// ⚠️ 顺序很重要：**先在空态上标定**（此时接口 total=0，页面必须显示空态），
//    再发一条真的。否则"页面出现了 1 行"无法区分"渲染对了"和"渲染器坏了一直显示旧数据"。
//
// 用法: node counselor-notifications.mjs        （需要 vite:5173 + 网关:8076）
// 退出码 0 = 页面与接口对得上。
// 注意：本脚本会**真的写库**（发一条通知给全部学生，24 行）。收尾会打印要删的 id 范围。
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const GATEWAY = 'http://localhost:8076'
const PORT = 9500 + Math.floor(Math.random() * 90)
const profile = mkdtempSync(join(tmpdir(), 'cdpnotif-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const fails = []
let closeBrowser = async () => { try { chrome.kill() } catch {} }

const TITLE = '期末复习安排'
const BODY = '同学们：本周五下午三点在 3 号教学楼 201 开期末复习动员会，请准时参加。'

const login = async (username, password) => {
  const r = await fetch(`${GATEWAY}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  const j = await r.json()
  if (!j?.data?.token) throw new Error(`登录失败(${username}): ${JSON.stringify(j).slice(0, 140)}`)
  return j.data
}
const api = async (path, token) => {
  const r = await fetch(`${GATEWAY}${path}`, { headers: { Authorization: 'Bearer ' + token } })
  return r.json().catch(() => null)
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
  if (!target) throw new Error('Chrome 调试端口未就绪')

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
  let id = 0
  const pending = new Map()
  const jsErrors = []
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params.exceptionDetails
      jsErrors.push((d?.exception?.description || d?.text || '').split('\n')[0].slice(0, 200))
    }
  }
  const send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) return { __err: r.result.exceptionDetails.exception?.description }
    return r.result?.result?.value
  }

  await send('Page.enable'); await send('Runtime.enable')
  closeBrowser = async () => { try { await send('Browser.close') } catch {} }

  // ---------- 接口真值（基准） ----------
  const c = await login('1000000001', '123456')
  const before = await api('/api/counselors/notifications/history?counselor_id=53&page=1&size=50', c.token)
  const beforeTotal = before?.data?.total
  const weeklyBefore = await api('/api/counselors/notifications/weekly-count?counselor_id=53', c.token)
  const rosterApi = await api('/api/counselors/students?counselor_id=53', c.token)
  const roster = Array.isArray(rosterApi?.data) ? rosterApi.data.length : -1
  console.log(`【接口真值·发送前】history.total=${beforeTotal}  weekly.thisWeek=${weeklyBefore?.data?.thisWeek}  名册=${roster} 人`)
  if (beforeTotal !== 0) {
    console.log(`  ⚠️ 库里有 ${beforeTotal} 条历史 —— 本脚本的"空态标定"步骤需要 0 条；`)
    console.log('     请先清掉本次验证造的行（参考脚本末尾打印的删除范围）再跑。')
  }

  // ---------- 登录并进页面 ----------
  await send('Page.navigate', { url: `${APP}/login` })
  await sleep(1200)
  await evaluate('localStorage.clear()')
  await send('Page.navigate', { url: `${APP}/login` })
  for (let i = 0; i < 60; i++) { await sleep(500); if (await evaluate(`!!document.querySelector('.lr-submit')`)) break }
  const filled = await evaluate(`(() => {
    const setVal = (sel, val) => {
      const el = document.querySelector(sel); if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    const shown = (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
    const a = setVal('input[placeholder="请输入学号或工号"]', '1000000001')
    const b = setVal('input[placeholder="请输入密码"]', '123456')
    const cc = setVal('input[placeholder="验证码"]', shown)
    document.querySelector('.lr-submit')?.click()
    return { a, b, c: cc }
  })()`)
  if (!filled?.a || !filled?.b || !filled?.c) throw new Error(`登录表单没找全: ${JSON.stringify(filled)}`)
  let path = ''
  for (let i = 0; i < 30; i++) { await sleep(500); path = await evaluate('location.pathname'); if (path && path !== '/login') break }
  if (!path || path === '/login') throw new Error('辅导员登录失败')
  console.log(`已登录 → ${path}`)

  await send('Page.navigate', { url: `${APP}/counselor/notifications` })
  await sleep(5000)

  // ---------- 第一步：空态标定 ----------
  const emptyView = await evaluate(`(() => {
    const body = document.body.innerText.replace(/\\s+/g, ' ')
    return {
      hasEmpty: /暂无发送记录/.test(body),
      rows: document.querySelectorAll('.el-table__body tbody tr').length,
      hasTable: !!document.querySelector('.el-table'),
    }
  })()`)
  console.log(`\n===== 空态标定（接口 total=${beforeTotal}）=====`)
  console.log(`  页面: ${JSON.stringify(emptyView)}`)
  if (beforeTotal === 0) {
    if (emptyView.hasEmpty && emptyView.rows === 0) {
      console.log('  → 接口 0 条 / 页面空态：对得上（说明这个探针能读出"空"，不是恒绿）')
    } else {
      fails.push(`接口 0 条，页面却没走空态: ${JSON.stringify(emptyView)}`)
      console.log('  → 对不上')
    }
  }

  // ---------- 第二步：负向对照（空内容必须失败） ----------
  await evaluate(`[...document.querySelectorAll('button')].find(b => b.innerText.trim() === '发送通知')?.click()`)
  await sleep(1500)
  const emptyToast = await evaluate(`(document.querySelector('.el-message')?.innerText || '').trim()`)
  console.log(`\n===== 负向对照：内容为空点发送 =====`)
  console.log(`  提示: ${JSON.stringify(emptyToast)}`)
  if (!/请填写通知内容/.test(emptyToast)) {
    fails.push(`空内容点发送没有报错，提示是 ${JSON.stringify(emptyToast)}`)
    console.log('  → 空内容居然不拦，对不上')
  } else {
    console.log('  → 拦住了，符合预期')
  }
  await sleep(2500)

  // ---------- 第三步：真发一条 ----------
  const typed = await evaluate(`(() => {
    const setVal = (el, val) => {
      const proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype
      Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true }))
      el.dispatchEvent(new Event('change', { bubbles: true }))
    }
    const title = document.querySelector('input[placeholder^="输入通知标题"]')
    const body = document.querySelector('textarea[placeholder="输入通知内容"]')
    if (!title || !body) return { ok: false, title: !!title, body: !!body }
    setVal(title, ${JSON.stringify(TITLE)})
    setVal(body, ${JSON.stringify(BODY)})
    return { ok: true, title: title.value, body: body.value }
  })()`)
  console.log(`\n===== 真发一条 =====\n  填入: ${JSON.stringify(typed)}`)
  if (!typed?.ok) { fails.push('找不到通知表单字段'); throw new Error('表单字段缺失') }

  await evaluate(`[...document.querySelectorAll('button')].find(b => b.innerText.trim() === '发送通知')?.click()`)
  await sleep(6000)
  const toast = await evaluate(`(document.querySelector('.el-message')?.innerText || '').trim()`)
  console.log(`  提示: ${JSON.stringify(toast)}`)

  const after = await api('/api/counselors/notifications/history?counselor_id=53&page=1&size=50', c.token)
  const total = after?.data?.total
  const entry = Array.isArray(after?.data?.list) ? after.data.list[0] : null
  const apiRecipients = entry?.recipientCount
  const weeklyAfter = await api('/api/counselors/notifications/weekly-count?counselor_id=53', c.token)
  console.log(`【接口真值·发送后】history.total=${total}  recipientCount=${apiRecipients}  weekly.thisWeek=${weeklyAfter?.data?.thisWeek}`)

  // ---------- 第四步：页面渲染是否等于接口真值 ----------
  await sleep(1500)
  const view = await evaluate(`(() => {
    const rows = [...document.querySelectorAll('.el-table__body tbody tr')]
    const cells = rows.map(r => [...r.querySelectorAll('td')].map(td => td.innerText.trim()))
    const body = document.body.innerText.replace(/\\s+/g, ' ')
    return {
      rowCount: rows.length,
      cells,
      countLabel: (document.body.innerText.match(/共\\s*(\\d+)\\s*次发送/) || [])[1] || null,
      showsContent: /期末复习安排/.test(body),
      stillEmpty: /暂无发送记录/.test(body),
    }
  })()`)
  console.log(`\n===== 页面渲染 =====`)
  console.log(`  行数=${view.rowCount}  「共 N 次发送」=${view.countLabel}  含标题文本=${view.showsContent}  仍是空态=${view.stillEmpty}`)
  console.log(`  单元格: ${JSON.stringify(view.cells)}`)

  console.log('\n===== 判定 =====')
  if (total !== 1) {
    fails.push(`发送后接口 history.total=${total}，期望 1`)
    console.log(`  ✗ 接口 total=${total}（期望 1）：发送没有真的落库`)
  } else if (view.stillEmpty || view.rowCount !== 1) {
    fails.push(`接口 total=1，页面上却有 ${view.rowCount} 行 / 空态=${view.stillEmpty}`)
    console.log(`  ✗ 接口有 1 条，页面没渲染出来：对不上`)
  } else if (apiRecipients !== roster) {
    fails.push(`recipientCount=${apiRecipients} 与名册 ${roster} 人不符`)
    console.log(`  ✗ 接收人数对不上：接口 ${apiRecipients} / 名册 ${roster}`)
  } else if (!view.showsContent) {
    fails.push('页面上没有出现刚填的通知标题')
    console.log('  ✗ 表格里没有刚发的内容')
  } else if (Number(view.countLabel) !== total) {
    fails.push(`页面「共 N 次发送」=${view.countLabel}，接口 total=${total}`)
    console.log('  ✗ 计数标签与接口不符')
  } else {
    console.log(`  ✓ 接口 total=${total}、接收人数=${apiRecipients}（= 名册 ${roster} 人）、页面渲染 1 行且内容匹配、周报数 ${weeklyAfter?.data?.thisWeek}`)
  }

  console.log(`\n【收尾】本次真发了 1 条通知给全部学生（${roster} 行）。`)
  console.log(`  清理: DELETE FROM communication_logs WHERE category IN ('warning','important','normal') AND created_at >= '<本次时间>';`)
  console.log(`        DELETE FROM notifications WHERE id > 1002;`)
  console.log(`  本轮新增的唯一 communication_logs id = ${entry?.id}（同批共 ${apiRecipients} 行）`)
  console.log(`JS 异常: ${jsErrors.length ? JSON.stringify(jsErrors) : '无'}`)
  if (jsErrors.length) fails.push(`页面有 JS 异常: ${jsErrors[0]}`)

  console.log(fails.length ? `\n结果: FAIL\n - ${fails.join('\n - ')}` : '\n结果: PASS')
  await closeBrowser()
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
  process.exit(fails.length ? 1 : 0)
}

main().catch(async (e) => {
  console.error('脚本异常:', e.message)
  await closeBrowser()
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
  process.exit(2)
})
