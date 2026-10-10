// W32 —— 管理员端「用户管理」页对 role 5（学院管理员）的**界面级**判据。
//
// 三件事，全都是"接口对、库里有、只有界面不对"这一族：
//   ① 用户列表里 `collegeadmin1/2`（活库 role 5）的角色列，此前渲染成「未知」；
//   ② 角色筛选下拉里没有「学院管理员」⇒ 选不出这批人；
//   ③ 「添加用户」弹窗角色下拉里没有「学院管理员」⇒ 根本建不了这类账号。
//
// 为什么非要走界面：这三处的数据源（`GET /users?...`）在修复前后一模一样 ——
// role 字段一直是 5，`getRoleText` 少了那一支才显示「未知」。接口层永远验不出来。
//
// 走 5173 的 vite dev server（它把 /api 转发到网关 8076），不额外起实例。
//
// 只读：本探针**不发任何写请求**，只读列表 + 打开弹窗看选项。
//
// 用法: node w32-admin-users-role5-ui.mjs
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || 'admin'
const PASS = process.argv[3] || '123456'
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.APP_BASE || 'http://localhost:5173'
const PORT = 9900 + Math.floor(Math.random() * 90)

const lines = []
const say = (s) => { lines.push(s); console.log(s) }
let failed = 0
const ok = (s) => say('  PASS ' + s)
const bad = (s) => { failed++; say('  FAIL ' + s) }

const profile = mkdtempSync(join(tmpdir(), 'cdpwa32-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const cleanup = () => { try { chrome.kill() } catch {} try { rmSync(profile, { recursive: true, force: true }) } catch {} }

const main = async () => {
  let target = null
  for (let i = 0; i < 80; i++) {
    try { target = (await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()).find((t) => t.type === 'page'); if (target) break } catch {}
    await sleep(250)
  }
  if (!target) { say('FAIL  Chrome 调试端口未就绪'); cleanup(); process.exit(1) }

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
  let id = 0
  const pending = new Map()
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id) }
  }
  const send = (method, params = {}) => new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) throw new Error(r.result.exceptionDetails.text)
    return r.result?.result?.value
  }
  const waitFor = async (expr, t = 40000) => { const d = Date.now() + t; while (Date.now() < d) { try { if (await evaluate(expr)) return true } catch {} ; await sleep(300) } return false }

  await send('Page.enable'); await send('Runtime.enable'); await send('DOM.enable')

  // ---------- 登录 ----------
  await send('Page.navigate', { url: `${APP}/login` })
  if (!await waitFor(`!!document.querySelector('.lr-submit')`, 45000)) { say('FAIL  登录页未渲染'); cleanup(); process.exit(1) }
  const filled = await evaluate(`(async () => {
    const setVal = (sel, val) => { const el = document.querySelector(sel); if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set.call(el, val)
      el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); return true }
    const a = setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(USER)})
    const b = setVal('input[placeholder="请输入密码"]', ${JSON.stringify(PASS)})
    setVal('input[placeholder="验证码"]', (await (async () => { for (let i=0;i<60;i++){ const t=(document.querySelector('.lr-captcha-btn')?.textContent||'').trim(); if(/^[A-Z0-9]{4}$/.test(t)) return t; await new Promise(r=>setTimeout(r,200)) } return '' })()))
    const btn = document.querySelector('.lr-submit'); if (btn) btn.click()
    return { a, b, clicked: !!btn }
  })()`)
  if (!filled?.a || !filled?.clicked) { say(`FAIL  登录表单没填上 ${JSON.stringify(filled)}`); cleanup(); process.exit(1) }

  let path = ''
  for (let i = 0; i < 60; i++) { await sleep(500); path = await evaluate('location.pathname'); if (path && path !== '/login') break }
  say(`登录: user=${USER} -> ${path}`)
  if (path === '/login') { say('FAIL  没登进去'); cleanup(); process.exit(1) }

  // ---------- 打开用户管理页 ----------
  await send('Page.navigate', { url: `${APP}/admin/users` })
  if (!await waitFor(`document.querySelectorAll('.el-table__row').length > 0`, 45000)) {
    say('FAIL  用户列表没渲染出行'); cleanup(); process.exit(1)
  }
  await sleep(1200)

  // ---------- ① 列表里 role 5 的角色列 ----------
  say('')
  say('--- ① 用户列表里 role 5 账号的角色列 ---')
  // 列序（含首列选择框，故整体 +1）：0=选择 1=ID 2=学号/工号 3=用户名 4=姓名 5=角色 …
  const rows = await evaluate(`(() => {
    return [...document.querySelectorAll('.el-table__body-wrapper .el-table__row')].map(tr => {
      const tds = [...tr.querySelectorAll('td')]
      const txt = i => (tds[i]?.innerText || '').trim()
      return { username: txt(3), name: txt(4), role: txt(5) }
    })
  })()`)
  say(`  共 ${rows.length} 行`)
  const collegeAdmins = rows.filter(r => /^collegeadmin/.test(r.username))
  if (collegeAdmins.length === 0) {
    bad('列表里没找到 collegeadmin* 行（活库应有 collegeadmin1/2 —— 可能翻页/筛选把它们挡住了）')
  } else {
    for (const r of collegeAdmins) {
      if (r.role === '学院管理员') ok(`${r.username}: 角色列 =「${r.role}」`)
      else bad(`${r.username}: 角色列 =「${r.role}」（期望「学院管理员」）`)
    }
  }
  const anyUnknown = rows.filter(r => r.role === '未知')
  if (anyUnknown.length) bad(`仍有 ${anyUnknown.length} 行显示「未知」：${anyUnknown.map(r => r.username).join(',')}`)
  else ok('列表里没有任何一行显示「未知」')

  // ---------- ② 角色筛选下拉里有没有「学院管理员」 ----------
  say('')
  say('--- ② 角色筛选下拉 ---')
  // Element Plus 的 el-select 里 input 没有 placeholder（占位符是 div 里的文本），
  // 所以按**控件文本**认，不能按 input 属性认（第一版就这么漏掉了「角色筛选」）。
  const openSelectByPlaceholder = async (ph) => {
    const found = await evaluate(`(() => {
      const el = [...document.querySelectorAll('.el-select')].find(s => (s.innerText || '').includes(${JSON.stringify(ph)}))
      if (!el) return false
      ;(el.querySelector('.el-select__wrapper') || el.querySelector('.el-input__wrapper') || el).click()
      return true
    })()`)
    await sleep(700)
    return found
  }
  // ⚠️ 页面上同时挂着好几个 el-select 的 popper，**真正的可见信号在 popper 父节点
  // （.el-popper）的 display 上** —— 内层 `.el-select-dropdown` 一律 display:block，
  // 按它过滤会把收起的 popper 也当成"开着"（第一版因此取到了分页的下拉）。
  const popperOptions = async () => await evaluate(`(() => {
    const ps = [...document.querySelectorAll('.el-select-dropdown')].filter(p => getComputedStyle(p.parentElement).display !== 'none')
    const p = ps[ps.length - 1]
    return p ? [...p.querySelectorAll('.el-select-dropdown__item')].map(i => i.textContent.trim()) : []
  })()`)

  if (!await openSelectByPlaceholder('角色筛选')) bad('没找到「角色筛选」下拉')
  else {
    const opts = await popperOptions()
    say(`  选项 = ${JSON.stringify(opts)}`)
    if (opts.includes('学院管理员')) ok('角色筛选下拉里有「学院管理员」')
    else bad('角色筛选下拉里没有「学院管理员」')
  }
  // 关掉这个 popper
  await evaluate(`document.body.click()`); await sleep(400)

  // ---------- ③ 添加用户弹窗的角色下拉 ----------
  say('')
  say('--- ③ 添加用户弹窗的角色下拉 ---')
  const openedDlg = await evaluate(`(() => {
    const b = [...document.querySelectorAll('button')].find(x => /添加用户|新增用户/.test(x.textContent))
    if (!b) return false
    b.click(); return true
  })()`)
  if (!openedDlg) bad('没找到「添加用户」按钮')
  else if (!await waitFor(`!!document.querySelector('.el-dialog__body .el-select')`, 15000)) bad('添加用户弹窗没打开')
  else {
    await sleep(500)
    await evaluate(`(() => {
      const sels = [...document.querySelectorAll('.el-dialog__body .el-select')]
      const s = sels[0]
      s?.querySelector('.el-select__wrapper, .el-input__wrapper')?.click() || s?.click()
    })()`)
    await sleep(800)
    const opts = await popperOptions()
    say(`  选项 = ${JSON.stringify(opts)}`)
    if (opts.includes('学院管理员')) ok('添加用户弹窗的角色下拉里有「学院管理员」')
    else bad('添加用户弹窗的角色下拉里没有「学院管理员」')

    // 选「学院管理员」后应出现「所属学院」这一项
    const picked = await evaluate(`(() => {
      const ps = [...document.querySelectorAll('.el-select-dropdown')].filter(p => getComputedStyle(p.parentElement).display !== 'none')
      const p = ps[ps.length - 1]
      if (!p) return false
      const it = [...p.querySelectorAll('.el-select-dropdown__item')].find(i => i.textContent.trim() === '学院管理员')
      if (!it) return false
      it.click(); return true
    })()`)
    await sleep(900)
    if (!picked) bad('没法在弹窗里选中「学院管理员」')
    else {
      const hasCollege = await evaluate(`(() => {
        const labels = [...document.querySelectorAll('.el-dialog__body .el-form-item')].map(fi => (fi.querySelector('.el-form-item__label')?.innerText || '').trim())
        return { labels, hasIt: labels.some(l => l.includes('所属学院')) }
      })()`)
      say(`  选中「学院管理员」后的表单项 = ${JSON.stringify(hasCollege.labels)}`)
      if (hasCollege.hasIt) ok('选中「学院管理员」后出现「所属学院」选择项')
      else bad('选中「学院管理员」后没有出现「所属学院」选择项')
    }
  }

  say('')
  say(failed === 0 ? '==== 全部 PASS ====' : `==== FAIL ${failed} 项 ====`)
  writeFileSync('w32-admin-users-role5-ui.txt', lines.join('\n') + '\n', 'utf8')
  cleanup()
  process.exit(failed === 0 ? 0 : 1)
}

main().catch((e) => { say('EXCEPTION ' + (e && e.stack || e)); writeFileSync('w32-admin-users-role5-ui.txt', lines.join('\n') + '\n', 'utf8'); cleanup(); process.exit(1) })
