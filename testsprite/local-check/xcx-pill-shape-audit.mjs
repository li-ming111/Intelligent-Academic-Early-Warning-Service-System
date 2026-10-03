// 找出「被撑变形的胶囊」：窄胶囊 + min-height: $tap-min = 椭圆（甚至正圆）。
//
// 背景：上一轮点击区收口给一批元素加了 `min-height: $tap-min`，其中 12 处
// 同时带 `border-radius: $radius-pill`(9999rpx)。圆角是 9999rpx 时，
// 盒子一旦**高度到 44 且宽度≤44**，圆角半径被钳到 min(w,h)/2，
// 视觉上就从"小胶囊"变成"椭圆/正圆"。
//   实测：教师端「编辑」38×44 → 正圆；学生端「撤销申诉」~52×44 → 椭圆。
// 判据：computed border-radius ≥ min(w,h)/2 - 1  →  两端已是完整半圆
//       （也就是"形状被尺寸吃掉了"），且宽度 < 44（窄到撑不出胶囊形）。
//
// 用法: node xcx-pill-shape-audit.mjs <role> [<path> ...]
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.XCX_BASE || 'http://127.0.0.1:5180'

const ACCOUNTS = {
  student: { user: '2023020616', pages: ['/pages/student/dashboard', '/pages/student/scores', '/pages/student/warnings', '/pages/student/notification-center', '/pages/student/class-ranking', '/pages/student/appeals', '/pages/student/assistance', '/pages/student/profile', '/pages/student/assistance-feedback'] },
  teacher: { user: '10001', pages: ['/pages/teacher/dashboard', '/pages/teacher/scores', '/pages/teacher/students', '/pages/teacher/warnings', '/pages/teacher/feedback-management', '/pages/teacher/class-management', '/pages/teacher/courses', '/pages/teacher/analysis', '/pages/teacher/credit-prediction', '/pages/teacher/profile'] },
  counselor: { user: '1000000001', pages: ['/pages/counselor/dashboard', '/pages/counselor/warnings', '/pages/counselor/students', '/pages/counselor/assistance', '/pages/counselor/analytics', '/pages/counselor/scores', '/pages/counselor/credit-monitor', '/pages/counselor/courses', '/pages/counselor/class-management', '/pages/counselor/profile'] },
  admin: { user: 'admin', pages: ['/pages/admin/dashboard', '/pages/admin/users', '/pages/admin/permissions', '/pages/admin/colleges', '/pages/admin/courses', '/pages/admin/rules', '/pages/admin/warnings', '/pages/admin/class-requests', '/pages/admin/profile'] },
  'college-admin': { user: 'collegeadmin1', pages: ['/pages/college-admin/dashboard', '/pages/college-admin/students', '/pages/college-admin/teachers', '/pages/college-admin/counselors', '/pages/college-admin/classes', '/pages/college-admin/warnings', '/pages/college-admin/scores', '/pages/college-admin/assistance', '/pages/college-admin/profile'] }
}

const role = process.argv[2]
const cfg = ACCOUNTS[role]
if (!cfg) { console.error(`role 必须是 ${Object.keys(ACCOUNTS).join(' | ')} 之一`); process.exit(2) }
const PAGES = process.argv.slice(3).filter(a => !a.startsWith('--'))
const TARGETS = PAGES.length ? PAGES : cfg.pages

const PORT = 9300 + Math.floor(Math.random() * 190)
const profile = mkdtempSync(join(tmpdir(), 'cdppill-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--hide-scrollbars', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank'
], { stdio: 'ignore' })
const sleep = ms => new Promise(r => setTimeout(r, ms))
const cleanup = () => { try { chrome.kill() } catch {}; try { rmSync(profile, { recursive: true, force: true }) } catch {} }

const PROBE = `(() => {
  const out = []
  for (const el of document.querySelectorAll('uni-view, uni-text, uni-button')) {
    const r = el.getBoundingClientRect()
    if (r.width === 0 || r.height === 0) continue
    if (r.right < 0 || r.left > innerWidth || r.bottom < 0 || r.top > innerHeight) continue
    const cs = getComputedStyle(el)
    const radRaw = cs.borderTopLeftRadius
    const rad = parseFloat(radRaw)
    if (!rad) continue
    // 百分比圆角（50% = 头像/圆点）是**故意画成圆的**，不是被撑变形的，跳过。
    // Chrome 对 border-radius: 50% 的 computed 值保留百分号，
    // 而 9999rpx 会算成 5000px —— 两者因此可以区分。
    if (radRaw.includes('%')) continue
    const half = Math.min(r.width, r.height) / 2
    // 只看**被竖向撑过**的胶囊：高度到了 40+（= 吃了 min-height: $tap-min），
    // 而宽度还没比高度大出一截 —— 这时圆角被钳到 half，看着就是椭圆/正圆。
    // 不能只判 rad >= half - 1：任何正常胶囊（如 38×22 的 tag）都满足它，
    // 那一版把头像、排名徽章、标题色条这些**本来就是圆/窄条**的全报了进来。
    // 宽度 < 2×高度 = 形状里几乎没有"直边"，看着就不是胶囊而是椭圆
    if (rad >= half - 1 && r.height >= 40 && r.width < r.height * 2) {
      out.push({
        cls: (el.getAttribute('class') || '').slice(0, 46),
        text: (el.innerText || '').replace(/\\s+/g, ' ').trim().slice(0, 18),
        w: Math.round(r.width), h: Math.round(r.height), rad: Math.round(rad)
      })
    }
  }
  const seen = new Set()
  return out.filter(o => { const k = o.cls + '|' + o.text; if (seen.has(k)) return false; seen.add(k); return true })
})()`

const main = async () => {
  let target = null
  for (let i = 0; i < 80; i++) {
    try { const l = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json(); target = l.find(t => t.type === 'page'); if (target) break } catch {}
    await sleep(250)
  }
  if (!target) { console.error('调试端口未就绪'); cleanup(); process.exit(1) }
  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
  let id = 0
  const pending = new Map()
  ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id) } }
  const send = (method, params = {}) => new Promise(res => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) =>
    (await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })).result?.result?.value

  await send('Page.enable'); await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 375, height: 812, deviceScaleFactor: 2, mobile: true })
  await send('Page.navigate', { url: APP })
  const waitFor = async (expr, ms = 45000) => { const end = Date.now() + ms
    while (Date.now() < end) { try { if (await evaluate(expr)) return true } catch {}; await sleep(400) } return false }

  // ⚠️ 必须连验证码一起等：W1.12 起验证码是服务端异步签发的，「表单元素到位」不再等于「码已到手」
  //    （实测读到空串 ⇒ 服务端 401 拒绝 ⇒ 探针报假「登录未落地」，见 _w4_login_diag.mjs）。
  await waitFor(`document.querySelectorAll('uni-input').length >= 3 && (document.querySelector('.captcha-code')?.textContent || '').trim().length > 0`)
  await evaluate(`(() => {
    const byPh = (ph) => { const w = Array.from(document.querySelectorAll('uni-input'))
      .find(u => (u.querySelector('.uni-input-placeholder')?.textContent || '').trim() === ph); return w ? w.querySelector('input') : null }
    const setVal = (el, v) => { if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, v)
      el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true })); return true }
    setVal(byPh('请输入学号或工号'), ${JSON.stringify(cfg.user)})
    setVal(byPh('请输入密码'), '123456')
    setVal(byPh('输入验证码'), (document.querySelector('.captcha-code')?.textContent || '').trim())
    document.querySelector('.btn')?.click(); return true })()`)
  if (!await waitFor(`(localStorage.getItem('token')||'') && (location.hash||'').includes('/pages/${role}/')`, 30000)) {
    console.error('登录未落地'); cleanup(); process.exit(1)
  }

  const lines = []
  let total = 0
  for (const p of TARGETS) {
    await evaluate(`location.hash = ${JSON.stringify('#' + p)}`); await sleep(4000)
    const found = await evaluate(PROBE) || []
    total += found.length
    if (found.length) {
      const head = `✗ ${p}  (${found.length} 处窄胶囊被撑成椭圆/正圆)`
      console.log(head); lines.push(head)
      for (const f of found) {
        const row = `     ${String(f.w).padStart(3)}×${String(f.h).padStart(3)}  圆角${f.rad}  ${f.cls}  「${f.text}」`
        console.log(row); lines.push(row)
      }
    } else console.log(`✓ ${p}  (无被撑变形的胶囊)`)
  }
  const summary = `==== ${role}: 共 ${total} 处窄胶囊被撑变形 ====`
  console.log(`\n${summary}`)
  lines.push(summary)
  writeFileSync(`pill-shape-${role}.txt`, lines.join('\n') + '\n', 'utf8')
  cleanup()
  process.exit(total ? 1 : 0)
}

main().catch(e => { console.error('FAIL', e.message); cleanup(); process.exit(1) })
