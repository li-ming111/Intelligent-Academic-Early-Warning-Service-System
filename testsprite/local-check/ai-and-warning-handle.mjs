// 2026-09-30 第三批实机核对：三处「报告与事实不符」修完之后的页面级验证。
//
// 本轮修的三件：
//   1) ai-service 整个服务恒 403（SecurityConfig 里 permitAll 的 matcher 写的是网关前缀
//      `/api/ai/**`，而到达本服务的路径是 `/ai/chat`；且它是唯一没注册 GatewayAuthFilter 的服务）
//      → AI 相关按钮**从来就没到过 Controller**，与 DashScope key 无关。
//   2) AIController.chat/qa 在模型没答时，塞一句**写死的话**冒充 AI 输出并回 success:true
//      → 辅导员"生成处理意见"会把这句话填进意见框还说"生成成功"。
//   3) AdminDataExport 横幅写死「系统自动每日备份」，而同页下方就印着后端说的"未实现运行时备份"；
//      「立即备份数据」按钮的失败原因被抹平成"备份失败"。
//   4) AdminWarnings 的「处理」从不看响应、从不传 handledBy，处理人/处理时间**页面上一个字都不显示**。
//
// 断言口径还是那一条：**接口/库里的真值 == 页面真实渲染出的东西**。
//
// ⚠️ 本脚本会往 academic_warning 里插两行**本会话的探针行**（ASCII 文本，避免命令行编码坑），
//    用字面 id 删除并复原 AUTO_INCREMENT；不碰任何演示数据。收尾会打印净额核对。
//
// 用法: node ai-and-warning-handle.mjs   （需 vite:5173 + 网关:8076 + 8070/8071/8074）
import { spawn, execSync } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { captchaFields } from './_captcha.mjs'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const GATEWAY = 'http://localhost:8076'
const MYSQL = 'mysql -uroot -proot academic_warning_system -N -B -e'
const PORT = 9500 + Math.floor(Math.random() * 90)
const profile = mkdtempSync(join(tmpdir(), 'cdpai-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const fails = []
let closeBrowser = async () => { try { chrome.kill() } catch {} }

const q = (sql) => execSync(`${MYSQL} "${sql}"`, { encoding: 'utf8' }).trim()
const qOne = (sql) => (q(sql).split('\n')[0] || '').trim()
const PROBE_TITLE_A = 'PROBE_WARNING_A_DELETE_ME'
const PROBE_TITLE_B = 'PROBE_WARNING_B_DELETE_ME'
const CANNED = '已收到您的问题，请提供具体成绩数据以便精确分析'

let probeA = null, probeB = null, aiBefore = null
const cleanup = () => {
  try {
    if (probeA || probeB) {
      const ids = [probeA, probeB].filter(Boolean).join(',')
      q(`DELETE FROM academic_warning WHERE id IN (${ids})`)
    }
    // 复原 AUTO_INCREMENT（本会话插过行）
    if (aiBefore) q(`ALTER TABLE academic_warning AUTO_INCREMENT = ${aiBefore}`)
  } catch (e) { console.error('清理失败:', e.message) }
}

const loginApi = async (username, password = '123456') => {
  const r = await fetch(`${GATEWAY}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, ...(await captchaFields(GATEWAY)) }),
  })
  const j = await r.json()
  if (!j?.data?.token) throw new Error(`登录失败(${username})`)
  return j.data
}
const api = async (path, token) => {
  const r = await fetch(`${GATEWAY}${path}`, { headers: { Authorization: 'Bearer ' + token } })
  return r.json().catch(() => null)
}
const check = (label, actual, expected) => {
  const ok = String(actual) === String(expected)
  if (!ok) fails.push(`${label}: 页面=${JSON.stringify(actual)} 期望=${JSON.stringify(expected)}`)
  console.log(`  ${ok ? '✓' : '✗'} ${label}: ${JSON.stringify(actual)}（期望 ${JSON.stringify(expected)}）`)
  return ok
}
const checkNot = (label, haystack, needle) => {
  const bad = String(haystack || '').includes(needle)
  if (bad) fails.push(`${label}: 出现了不该有的 ${JSON.stringify(needle)}`)
  console.log(`  ${bad ? '✗' : '✓'} ${label}（不该含 ${JSON.stringify(needle)}）`)
  return !bad
}

const main = async () => {
  // ---------- 探针行（先记下 AUTO_INCREMENT 以便复原） ----------
  const cols = q("SELECT id,status,handled_by,handle_result FROM academic_warning ORDER BY id")
  console.log('【核对前】academic_warning 现状:')
  console.log(cols.split('\n').map((l) => '   ' + l).join('\n'))
  aiBefore = qOne("SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA='academic_warning_system' AND TABLE_NAME='academic_warning'")
  console.log(`   AUTO_INCREMENT=${aiBefore}`)
  // ⚠️ `mysql -e` 每次都是**新连接**，LAST_INSERT_ID() 必须和 INSERT 同一条连接里取。
  // 上一版就是分成两次调用 → 两次都拿到 0 → 收尾删的是 `id IN (0,0)`（一行没删），
  // 后面所有按 id 查库的断言全部落空。这条注释是给下一个改脚本的人看的。
  const ins = (title) => {
    const out = q(`INSERT INTO academic_warning (student_id, student_name, rule_id, type, title, description, warning_level, warning_message, trigger_score, status, appeal_status, created_at) VALUES (3, 'PROBE', 2, 1, '${title}', 'probe row created by ai-and-warning-handle.mjs', 2, 'probe', 40.00, 0, 'none', NOW()); SELECT LAST_INSERT_ID()`)
    const id = out.split('\n').map((s) => s.trim()).filter((s) => /^\d+$/.test(s)).pop()
    if (!id || id === '0') throw new Error(`拿不到探针行 id（mysql 输出: ${JSON.stringify(out)}）`)
    return id
  }
  probeA = ins(PROBE_TITLE_A)
  probeB = ins(PROBE_TITLE_B)
  console.log(`【探针】本次会话插入两行: A=${probeA} B=${probeB}（收尾按字面 id 删除）`)
  const warnCountNow = qOne('SELECT COUNT(*) FROM academic_warning')
  console.log(`   academic_warning 现有 ${warnCountNow} 行（演示数据 4 + 探针 2）`)

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
  let jsErrors = []
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

  const uiLogin = async (username) => {
    const d = await loginApi(username)
    await send('Page.navigate', { url: `${APP}/login` })
    await sleep(800)
    await evaluate(`(() => {
      localStorage.clear()
      localStorage.setItem('token', ${JSON.stringify(d.token)})
      localStorage.setItem('userId', String(${JSON.stringify(String(d.userId))}))
      localStorage.setItem('username', ${JSON.stringify(username)})
      localStorage.setItem('name', ${JSON.stringify(d.name || username)})
      localStorage.setItem('role', String(${JSON.stringify(String(d.role))}))
      return localStorage.getItem('name')
    })()`)
    return d
  }
  const nav = async (path, waitMs = 6000) => {
    jsErrors = []
    await send('Page.navigate', { url: `${APP}${path}` })
    await sleep(waitMs)
  }
  // 提示条会 ~3 秒自动消失，必须在窗口内读
  const readToast = () => evaluate(`(document.querySelector('.el-message')?.innerText || '').trim()`)

  // ================= A) AI 不是"生成成功"，而是如实失败 =================
  console.log('\n===== A) 辅导员「生成处理意见」/counselor/warnings =====')
  {
    const c = await loginApi('1000000001')
    const ai = await api('/api/ai/chat', c.token) // GET 会 405，这里只用来看路径是否可达
    console.log(`  （可达性参考）GET /api/ai/chat → code=${ai?.code}`)
    const post = await fetch(`${GATEWAY}/api/ai/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + c.token },
      body: JSON.stringify({ userId: c.userId, message: 'probe: generate an opinion' }),
    })
    const postJson = await post.json().catch(() => null)
    console.log(`  接口真值: POST /api/ai/chat → http=${post.status} inner code=${postJson?.code} success=${postJson?.success}`)
    console.log(`  接口原话: ${JSON.stringify(postJson?.message)}`)
    if (postJson?.code === 200) fails.push('接口竟然回了 200 —— 没有 key 时它不该有 AI 回答')
    if (postJson?.message && String(postJson.message).includes(CANNED)) fails.push('接口仍在返回写死的那句话')

    await uiLogin('1000000001')
    await nav('/counselor/warnings', 7000)
    const opened = await evaluate(`(() => {
      const rows = [...document.querySelectorAll('.el-table__body tbody tr')]
      const btn = rows.length ? [...rows[0].querySelectorAll('button')].find(b => b.innerText.trim() === '处理') : null
      if (!btn) return { rows: rows.length, clicked: false }
      btn.click(); return { rows: rows.length, clicked: true }
    })()`)
    console.log(`  预警列表行数=${opened?.rows} 打开处理弹窗=${opened?.clicked}`)
    if (!opened?.clicked) fails.push('预警列表里找不到「处理」按钮，A 用例无法继续')
    await sleep(1200)

    const before = await evaluate(`(() => {
      const ta = document.querySelector('.el-dialog textarea[placeholder="记录处理意见..."]')
      return ta ? ta.value : null
    })()`)
    await evaluate(`[...document.querySelectorAll('.el-dialog button')].find(b => b.innerText.trim() === '生成处理意见')?.click()`)
    await sleep(2500)
    const toast = await readToast()
    const after = await evaluate(`(() => {
      const ta = document.querySelector('.el-dialog textarea[placeholder="记录处理意见..."]')
      return ta ? ta.value : null
    })()`)
    console.log(`  弹窗内「处理意见」空=${JSON.stringify(before)} → ${JSON.stringify(after)}`)
    console.log(`  提示: ${JSON.stringify(toast)}`)
    check('点 AI 后意见框仍是空的（没被塞进任何东西）', after, before)
    checkNot('意见框没被塞进写死的那句话', after, CANNED)
    checkNot('提示没说"生成成功"', toast, '生成成功')
    if (!/API Key|DASHSCOPE_KEY|AI/i.test(toast || '')) {
      fails.push(`提示里没有说明原因（应当带出后端的"未配置 API Key"原话），实际=${JSON.stringify(toast)}`)
      console.log('  ✗ 提示没带出失败原因')
    } else console.log('  ✓ 提示带出了后端给的原因')
    await evaluate(`[...document.querySelectorAll('.el-dialog button')].find(b => b.innerText.trim() === '取消')?.click()`)
    await sleep(800)
  }

  // ================= B) 备份：横幅不再许诺，按钮不再假装成功 =================
  console.log('\n===== B) 数据备份 /admin/data-export =====')
  {
    const a = await loginApi('admin')
    const bk = await api('/api/admin/backups/list', a.token)
    console.log(`  接口真值: implemented=${bk?.data?.implemented} total=${bk?.data?.total} items=${(bk?.data?.items || []).length}`)
    await uiLogin('admin')
    await nav('/admin/data-export', 6000)
    await evaluate(`(() => {
      const t = [...document.querySelectorAll('.el-tabs__item')].find(e => e.innerText.trim() === '数据备份'); t && t.click()
    })()`)
    await sleep(4000)
    const v = await evaluate(`(() => {
      const panes = [...document.querySelectorAll('.el-tab-pane')]
      const pane = panes.find(p => p.innerText.includes('备份操作'))
      const txt = pane ? pane.innerText.replace(/\\s+/g, ' ') : ''
      return { txt: txt.slice(0, 400) }
    })()`)
    console.log(`  页面横幅区: ${JSON.stringify(v?.txt?.slice(0, 160))}`)
    checkNot('横幅不再宣称「系统自动每日备份」', v?.txt, '系统自动每日备份')
    if (!/不会生成备份文件/.test(v?.txt || '')) {
      fails.push('横幅没有如实说明「立即备份数据」不会生成备份文件')
      console.log('  ✗ 横幅话没说清')
    } else console.log('  ✓ 横幅如实说明按钮不产生备份文件')

    await evaluate(`[...document.querySelectorAll('button')].find(b => b.innerText.trim() === '立即备份数据')?.click()`)
    await sleep(2000)
    const toast = await readToast()
    console.log(`  点「立即备份数据」提示: ${JSON.stringify(toast)}`)
    checkNot('没有谎报"备份已完成"', toast, '备份已完成')
    if (!/未实现|不会生成/.test(toast || '')) {
      fails.push(`失败提示没带出后端原话，实际=${JSON.stringify(toast)}`)
      console.log('  ✗ 提示没带出后端原因')
    } else console.log('  ✓ 提示带出了后端给的原因')
  }

  // ================= C) 预警处理：处理人落库 + 页面回读 + 失败不谎报 =================
  console.log('\n===== C) 管理员预警处理 /admin/warnings =====')
  {
    const a = await loginApi('admin')
    const all = await api('/api/admin/warnings', a.token)
    const list = Array.isArray(all?.data) ? all.data : []
    console.log(`  接口真值: /admin/warnings 共 ${list.length} 行（含 2 行探针）`)

    await uiLogin('admin')
    await nav('/admin/warnings', 7000)
    const before = await evaluate(`(() => {
      const cards = [...document.querySelectorAll('.aw-card')]
      const card = cards.find(c => c.innerText.includes(${JSON.stringify(PROBE_TITLE_A)}))
      return { cards: cards.length, found: !!card, text: card ? card.innerText.replace(/\\s+/g,' ').slice(0,120) : null }
    })()`)
    console.log(`  页面卡片数=${before?.cards} 找到探针A=${before?.found}`)
    console.log(`  探针A卡片: ${JSON.stringify(before?.text)}`)
    check('页面卡片数 == 接口行数', before?.cards, list.length)
    if (!before?.found) fails.push('页面上找不到探针 A 的卡片')

    // 打开 A 的处理弹窗并确认
    await evaluate(`(() => {
      const card = [...document.querySelectorAll('.aw-card')].find(c => c.innerText.includes(${JSON.stringify(PROBE_TITLE_A)}))
      card && card.querySelector('.awc-btn') && card.querySelector('.awc-btn').click()
    })()`)
    await sleep(1200)
    await evaluate(`(() => {
      const ta = document.querySelector('.el-dialog textarea[placeholder="处理备注..."]')
      if (!ta) return false
      Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set.call(ta, 'probe remark by CDP')
      ta.dispatchEvent(new Event('input', { bubbles: true }))
      return true
    })()`)
    await evaluate(`[...document.querySelectorAll('.el-dialog button')].find(b => b.innerText.trim() === '确认')?.click()`)
    await sleep(3500)
    const toast = await readToast()
    console.log(`  确认后提示: ${JSON.stringify(toast)}`)

    // ⚠️ 中文**不要**用 mysql CLI 的 stdout 去断言：Windows 上它是 CP936，
    //    读进 Node 全是 `ϵͳ����Ա` 这种乱码，会把"写对了"读成"写错了"（这两条踩过）。
    //    中文一律走 API 判定 —— 那也正是页面渲染用的同一条路。
    const dbRow = qOne(`SELECT CONCAT(status,'|',IFNULL(handled_at,'NULL')) FROM academic_warning WHERE id=${probeA}`)
    console.log(`  库里探针A: status|handled_at = ${dbRow}`)
    const [dbStatus] = dbRow.split('|')
    check('库里 status 变成 1', dbStatus, '1')

    const apiAll = await api('/api/admin/warnings', a.token)
    const probeRow = (Array.isArray(apiAll?.data) ? apiAll.data : []).find((w) => String(w.id) === String(probeA))
    console.log(`  接口回读探针A: ${JSON.stringify(probeRow && { status: probeRow.status, handledBy: probeRow.handledBy, handleResult: probeRow.handleResult })}`)
    check('接口回读 handled_by == 登录人真名（不再被备注顶替）', probeRow?.handledBy, '系统管理员')
    check('接口回读 status == 1', probeRow?.status, 1)
    if (!/已处理/.test(probeRow?.handleResult || '')) fails.push(`handle_result 没写进去: ${JSON.stringify(probeRow?.handleResult)}`)
    console.log(`  ${/已处理/.test(probeRow?.handleResult || '') ? '✓' : '✗'} handle_result 含「已处理」`)
    if (!/probe remark by CDP/.test(probeRow?.handleResult || '')) fails.push('备注没有并入 handle_result')
    console.log(`  ${/probe remark by CDP/.test(probeRow?.handleResult || '') ? '✓' : '✗'} 备注并入 handle_result`)

    const after = await evaluate(`(() => {
      const card = [...document.querySelectorAll('.aw-card')].find(c => c.innerText.includes(${JSON.stringify(PROBE_TITLE_A)}))
      return card ? card.innerText.replace(/\\s+/g,' ') : null
    })()`)
    console.log(`  探针A卡片（处理后）: ${JSON.stringify(after)}`)
    if (!/已处理/.test(after || '')) fails.push('处理后卡片没显示「已处理」')
    console.log(`  ${/已处理/.test(after || '') ? '✓' : '✗'} 卡片显示「已处理」`)
    if (!/处理人：系统管理员/.test(after || '')) fails.push('卡片没有回读显示处理人')
    console.log(`  ${/处理人：系统管理员/.test(after || '') ? '✓' : '✗'} 卡片回读显示「处理人：系统管理员」`)
    checkNot('卡片不再把备注当处理人显示', after, '处理人：probe remark')

    // ---- 负向对照：卡片点下去之前，那行已经被删了 ----
    console.log('\n  --- 负向对照：渲染出来之后、点确认之前把该行删掉 ---')
    await evaluate(`(() => {
      const card = [...document.querySelectorAll('.aw-card')].find(c => c.innerText.includes(${JSON.stringify(PROBE_TITLE_B)}))
      card && card.querySelector('.awc-btn') && card.querySelector('.awc-btn').click()
    })()`)
    await sleep(1200)
    q(`DELETE FROM academic_warning WHERE id=${probeB}`)
    probeB = null
    console.log(`  已从库里删掉探针B（页面此刻还画着它）`)
    await evaluate(`[...document.querySelectorAll('.el-dialog button')].find(b => b.innerText.trim() === '确认')?.click()`)
    await sleep(3000)
    const badToast = await readToast()
    console.log(`  提示: ${JSON.stringify(badToast)}`)
    checkNot('后端失败时没有谎报"已处理"', badToast, '预警已处理')
    if (!/处理失败/.test(badToast || '')) {
      fails.push(`失败路径没有如实报错，实际=${JSON.stringify(badToast)}`)
      console.log('  ✗ 没报错')
    } else console.log('  ✓ 如实报了"处理失败"并带出后端原因')
    const cardB = await evaluate(`(() => {
      const card = [...document.querySelectorAll('.aw-card')].find(c => c.innerText.includes(${JSON.stringify(PROBE_TITLE_B)}))
      return card ? card.innerText.replace(/\\s+/g,' ') : 'CARD_GONE'
    })()`)
    console.log(`  探针B卡片（失败后）: ${JSON.stringify(cardB)}`)
    checkNot('失败后卡片没有变成「已处理」', cardB, '已处理')
  }

  // ---------- 收尾核对：库里必须回到核对前的样子 ----------
  console.log('\n===== 收尾核对 =====')
  cleanup()
  const restIds = q('SELECT id FROM academic_warning ORDER BY id').split('\n').map((s) => s.trim()).filter(Boolean)
  console.log(`  academic_warning 剩 ${restIds.length} 行: ${restIds.join(',')}`)
  check('行数回到 4（探针都删干净了）', restIds.length, 4)
  check('探针行已不存在', restIds.some((i) => i === probeA || i === probeB), false)

  console.log(`\nJS 异常: ${jsErrors.length ? JSON.stringify(jsErrors) : '无'}`)
  if (jsErrors.length) fails.push(`页面有 JS 异常: ${jsErrors[0]}`)
  console.log(fails.length ? `\n结果: FAIL (${fails.length})\n - ${fails.join('\n - ')}` : '\n结果: PASS')
  await closeBrowser()
  cleanup()
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
  process.exit(fails.length ? 1 : 0)
}


main().catch(async (e) => {
  console.error('脚本异常:', e.message)
  await closeBrowser()
  cleanup()
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
  process.exit(2)
})
