// 量**真实点击区**，而不是 CSS 里声明的 height。
//
// 为什么要另写一个：先前那轮审计是 grep `height: 60rpx` 这类**声明值**，
// 于是漏掉一整类控件 —— 纯文字链（"标记已读 / 删除"）根本没有 height，
// 尺寸由 line-height 和 padding 决定，量不到。而它们恰恰是最容易点不中的。
//
// 做法：用 CDP 的 `getEventListeners`（命令行 API，只在 Runtime.evaluate 里可用）
// 找出**真的挂了 click 监听**的元素 —— 这就是 uni-app 的 @tap 在 H5 上的落地形式，
// 比猜 class 名可靠得多。
//
// ---- 2026-09-29 第二版：改成**命中测试**求有效点击区 ----
// 第一版对每个元素取 getBoundingClientRect()，结果是错的 —— 不是"不准"，
// 是**结构性看不见**本工程最主要的那个修法：
//   `@include tap-area` 用 ::after 伪元素撑命中区，伪元素**不进入宿主元素的
//   布局矩形**，所以 rect 永远是 17px（一行字高），无论它实际能点多高。
//   那一版因此把已经修好的 33 个区块标题全报成"未修"。
//
// 现在改为从元素中心向四个方向**逐像素**外扩，用 elementFromPoint 判断
// 命中是否仍落在该元素（或其宿主后代）上 —— 伪元素命中会返回宿主元素本身，
// 所以这条路径能看见 ::after、能看见 padding、也能看见被遮挡。
// 这是"用户到底能不能点中"的直接答案，与实现手法无关。
//
// 报出**有效高度或有效宽度 < 44 CSS px** 的。
//
// 排除两类噪音：
//   · 父元素：若它内部还有另一个可点元素，它的矩形是容器而非控件；
//   · 视口外的（横向 scroll-view 里未滚到的）。
//
// 用法: node xcx-tap-audit.mjs <role> [<path> ...]   （不给 path 就用内置清单）
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.XCX_BASE || 'http://127.0.0.1:5180'
const MIN = 44 // CSS px，与 $tap-min(88rpx @ DPR2 = 44px) 同一个数

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

const PORT = 9500 + Math.floor(Math.random() * 300)
const profile = mkdtempSync(join(tmpdir(), 'cdptap-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--hide-scrollbars', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank'
], { stdio: 'ignore' })
const sleep = ms => new Promise(r => setTimeout(r, ms))
const cleanup = () => { try { chrome.kill() } catch {}; try { rmSync(profile, { recursive: true, force: true }) } catch {} }

/** 在页面里跑的测量函数：返回所有「真的挂了 click 监听、且不是容器」的元素的有效点击区尺寸。 */
const PROBE = `(() => {
  const owns = (el, node) => !!node && (node === el || el.contains(node))

  // 元素被祖先的 overflow 裁掉之后**真正还看得见**的那块矩形。
  // 横向 scroll-view 里没滚到的 chip，它的 rect 中心可能根本不在可视区内，
  // 直接拿 rect 中心做命中测试会误判成"被遮挡"（实测 admin/users、
  // counselor/scores 各误报一处，都是半滚出屏幕的筛选 chip）。
  const clipBox = (el) => {
    const r = el.getBoundingClientRect()
    let l = Math.max(r.left, 0), t = Math.max(r.top, 0)
    let rr = Math.min(r.right, innerWidth), b = Math.min(r.bottom, innerHeight)
    let n = el.parentElement
    while (n && n !== document.documentElement) {
      const cs = getComputedStyle(n)
      if (cs.overflowX !== 'visible' || cs.overflowY !== 'visible') {
        const nr = n.getBoundingClientRect()
        l = Math.max(l, nr.left); t = Math.max(t, nr.top)
        rr = Math.min(rr, nr.right); b = Math.min(b, nr.bottom)
      }
      n = n.parentElement
    }
    return rr <= l || b <= t ? null : { l, t, r: rr, b, w: rr - l, h: b - t, fullW: r.width }
  }

  // 从可见区的中心逐像素外扩，直到命中测试不再落在 el 上。
  // 伪元素命中返回宿主元素本身 → 这条路径看得见 ::after。
  const eff = (el) => {
    const v = clipBox(el)
    if (!v) return null // 完全不可见
    const cx = Math.round((v.l + v.r) / 2)
    const cy = Math.round((v.t + v.b) / 2)
    if (!owns(el, document.elementFromPoint(cx, cy))) return null // 被别的元素盖住
    const walk = (dx, dy, lim) => { let n = 0, x = cx, y = cy
      for (;;) { x += dx; y += dy
        if (n >= lim || x < 0 || y < 0 || x >= innerWidth || y >= innerHeight) break
        if (!owns(el, document.elementFromPoint(x, y))) break
        n++ }
      return n }
    const up = walk(0, -1, cy), down = walk(0, 1, innerHeight - cy)
    const lf = walk(-1, 0, cx), rt = walk(1, 0, innerWidth - cx)
    // 横向半滚出屏幕的元素，可视宽度天然不足 44 —— 那是滚动位置的产物，
    // 不是布局缺陷。只有**横向完整可见**时才拿走出来的宽度作判断。
    const fullyVisibleX = v.w >= v.fullW - 1
    return { w: fullyVisibleX ? lf + rt + 1 : v.fullW, h: up + down + 1, partial: !fullyVisibleX }
  }

  const out = []
  let considered = 0   // 真正量过的控件数 —— 让"0 处"变成可证伪的结论
  for (const el of document.querySelectorAll('uni-view, uni-button, uni-text, uni-navigator, uni-image, button, view, text')) {
    const ls = (typeof getEventListeners === 'function' ? getEventListeners(el) : {})
    if (!ls.click || !ls.click.length) continue
    const r = el.getBoundingClientRect()
    if (r.width === 0 || r.height === 0) continue
    // 容器：内部还有别的可点元素，它的矩形不代表控件本身
    const innerTappable = [...el.querySelectorAll('*')].some(c =>
      typeof getEventListeners === 'function' && (getEventListeners(c).click || []).length)
    if (innerTappable) continue
    if (r.right < 0 || r.left > innerWidth || r.bottom < 0 || r.top > innerHeight) continue
    considered++
    // rect 已经够大就不必花命中测试的钱
    if (r.width >= 44 && r.height >= 44) continue
    const e = eff(el)
    if (e && e.w >= 44 && e.h >= 44) continue // 伪元素/padding 撑够了，不是缺陷
    out.push({
      tag: el.tagName.toLowerCase(),
      cls: (el.getAttribute('class') || '').slice(0, 60),
      text: (el.innerText || '').replace(/\\s+/g, ' ').trim().slice(0, 24),
      rw: Math.round(r.width), rh: Math.round(r.height),
      w: e ? Math.round(e.w) : null, h: e ? Math.round(e.h) : null, occluded: !e
    })
  }
  const seen = new Set()
  const dedup = out.filter(o => {
    const k = o.cls + '|' + o.text
    if (seen.has(k)) return false
    seen.add(k); return true
  })
  return { found: dedup, considered }
})()`

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
  if (!target) { console.error('调试端口未就绪'); cleanup(); process.exit(1) }

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
  let id = 0
  const pending = new Map()
  ws.onmessage = e => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id) }
  }
  const send = (method, params = {}) =>
    new Promise(res => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr, cmdApi = false) =>
    (await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true, includeCommandLineAPI: cmdApi })).result?.result?.value

  await send('Page.enable'); await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 375, height: 812, deviceScaleFactor: 2, mobile: true })
  await send('Page.navigate', { url: APP })

  const waitFor = async (expr, ms = 45000) => {
    const end = Date.now() + ms
    while (Date.now() < end) { try { if (await evaluate(expr)) return true } catch {}; await sleep(400) }
    return false
  }

  await waitFor(`document.querySelectorAll('uni-input').length >= 3`)
  await evaluate(`(() => {
    const byPh = (ph) => {
      const w = Array.from(document.querySelectorAll('uni-input'))
        .find(u => (u.querySelector('.uni-input-placeholder')?.textContent || '').trim() === ph)
      return w ? w.querySelector('input') : null
    }
    const setVal = (el, v) => { if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, v)
      el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true })); return true }
    setVal(byPh('请输入学号或工号'), ${JSON.stringify(cfg.user)})
    setVal(byPh('请输入密码'), '123456')
    setVal(byPh('输入验证码'), (document.querySelector('.captcha-code')?.textContent || '').trim())
    document.querySelector('.btn')?.click(); return true
  })()`)
  const ok = await waitFor(`(localStorage.getItem('token') ? 1 : 0) === 1 && (location.hash||'').includes('/pages/${role}/')`, 30000)
  if (!ok) { console.error('登录未落地'); cleanup(); process.exit(1) }

  // 空页哨兵：上一版出过"142 → 0"的假清白，根因是页面白屏时没有任何可点元素。
  // 每页至少要求有若干个已挂 click 的元素，否则这一页的结果不作数。
  const lines = []
  let total = 0
  for (const p of TARGETS) {
    await evaluate(`location.hash = ${JSON.stringify('#' + p)}`)
    await sleep(4500)
    const all = await evaluate(`document.querySelectorAll('uni-view,uni-text,uni-button,uni-navigator').length`, true) || 0
    const res = await evaluate(PROBE, true) || { found: [], considered: 0 }
    const found = res.found
    // 两道哨兵，防的正是上一版那个"假清白"：
    // 页面白屏 → 节点数为 0 → 结果作废；
    // 页面渲染了但一个可点控件都没有 → considered 为 0 → 结果同样不作数。
    // 只有 considered > 0 的 ✓ 才是"量过了、没问题"，否则只是"什么都没量到"。
    if (all < 20 || res.considered < 1) {
      const row = `!! ${p}  (节点 ${all} / 量到控件 ${res.considered} —— 结果不可信，作废)`
      console.log(row); lines.push(row); total += 999; continue
    }
    total += found.length
    const head = `${found.length ? '✗' : '✓'} ${p}  (量了 ${res.considered} 个控件，${found.length} 处 < ${MIN}px)`
    console.log(head); lines.push(head)
    for (const f of found) {
      const body = f.occluded ? `被遮挡，rect ${f.rw}×${f.rh}` : `rect ${f.rw}×${f.rh} → 有效 ${f.w}×${f.h}`
      const row = `     ${body.padEnd(28)}  ${f.tag}  ${f.cls}  「${f.text}」`
      console.log(row); lines.push(row)
    }
  }
  const summary = `==== ${role}: 共 ${total} 处有效点击区小于 ${MIN}px ====`
  console.log(`\n${summary}`)
  lines.push(summary)
  writeFileSync(`tap-audit-${role}.txt`, lines.join('\n') + '\n', 'utf8')
  cleanup()
  process.exit(0)
}

main().catch(e => { console.error('FAIL', e.message); cleanup(); process.exit(1) })
