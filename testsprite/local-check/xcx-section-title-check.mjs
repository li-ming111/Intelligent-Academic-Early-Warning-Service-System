// 函数级回归：section-title 的「可点 vs 不可点」这条分界有没有做对。
//
// 为什么需要它（尺寸审计看不见这件事）：
//   section-title 原先给右侧文字**无条件**挂 @tap，于是 24 个页面里的纯说明性
//   文字（「共 24 人」「1 项」「来自 /admin/dashboard」）也是点击目标。修法是把
//   点击绑到 `arrow` 上（「箭头 = 可跳转」是本组件既有约定）。
//   这个改动一旦写错，症状是**12 个「查看全部」静默失效** —— 元素还在、
//   尺寸也够（所以 xcx-tap-audit 照样全绿），点下去只是不跳转。
//   尺寸审计查不了这个，只有真点一下才知道。
//
// 断言两件事：
//   A. 带 arrow 的（真跳转）：点下去 hash 必须变到预期页面；
//   B. 不带 arrow 的（说明文字）：**不该**挂 click 监听，点了不该有任何跳转。
//
// 用法: node xcx-section-title-check.mjs
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.XCX_BASE || 'http://127.0.0.1:5180'
const PORT = 9900 + Math.floor(Math.random() * 90)
const profile = mkdtempSync(join(tmpdir(), 'cdpst-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--hide-scrollbars', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank'
], { stdio: 'ignore' })
const sleep = ms => new Promise(r => setTimeout(r, ms))
const cleanup = () => { try { chrome.kill() } catch {}; try { rmSync(profile, { recursive: true, force: true }) } catch {} }

// 每个角色一组：账号 + 该角色首页上的「真跳转」断言。
// 必须按角色分组 —— 学生号登不进 teacher/admin 的页面。
const GROUPS = [
  { user: '2023020616', home: '/pages/student/',
    cases: [['/pages/student/dashboard', '查看全部', '/pages/student/warnings']] },
  { user: '10001', home: '/pages/teacher/',
    cases: [['/pages/teacher/dashboard', '查看全部', '/pages/teacher/warnings']] },
  { user: 'admin', home: '/pages/admin/',
    cases: [['/pages/admin/dashboard', '查看全部', '/pages/admin/warnings']] },
  { user: 'collegeadmin1', home: '/pages/college-admin/',
    cases: [['/pages/college-admin/dashboard', '来自成绩聚合', '/pages/college-admin/scores']] }
]

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
  const evaluate = async (expr, cmdApi = true) =>
    (await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true, includeCommandLineAPI: cmdApi })).result?.result?.value

  await send('Page.enable'); await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 375, height: 812, deviceScaleFactor: 2, mobile: true })

  const waitFor = async (expr, ms = 45000) => { const end = Date.now() + ms
    while (Date.now() < end) { try { if (await evaluate(expr)) return true } catch {}; await sleep(400) } return false }

  /** 清掉登录态 → 回登录页 → 用该角色账号登录。 */
  const loginAs = async (user, home) => {
    await evaluate(`localStorage.clear()`)
    await send('Page.navigate', { url: APP })
    if (!await waitFor(`document.querySelectorAll('uni-input').length >= 3`, 30000)) return false
    await evaluate(`(() => {
      const byPh = (ph) => { const w = Array.from(document.querySelectorAll('uni-input'))
        .find(u => (u.querySelector('.uni-input-placeholder')?.textContent || '').trim() === ph); return w ? w.querySelector('input') : null }
      const setVal = (el, v) => { if (!el) return false
        Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, v)
        el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true })); return true }
      setVal(byPh('请输入学号或工号'), ${JSON.stringify(user)})
      setVal(byPh('请输入密码'), '123456')
      setVal(byPh('输入验证码'), (document.querySelector('.captcha-code')?.textContent || '').trim())
      document.querySelector('.btn')?.click(); return true })()`)
    return waitFor(`(localStorage.getItem('token')||'') && (location.hash||'').includes(${JSON.stringify(home)})`, 30000)
  }

  let fails = 0
  for (const g of GROUPS) {
   if (!await loginAs(g.user, g.home)) { console.log(`✗ 登录未落地（${g.user}）`); fails++; continue }
   console.log(`── 角色 ${g.user}`)
   for (const [page, label, expect] of g.cases) {
    await evaluate(`location.hash = ${JSON.stringify('#' + page)}`); await sleep(4000)

    // A. 真跳转的必须能跳
    const jumped = await evaluate(`(() => {
      const hit = [...document.querySelectorAll('.section-title__hit')]
        .find(e => (e.innerText || '').includes(${JSON.stringify(label)}))
      if (!hit) return 'NO_HIT'
      hit.click(); return 'CLICKED'
    })()`)
    if (jumped !== 'CLICKED') { console.log(`✗ ${page}  没找到「${label}」的命中层（${jumped}）`); fails++; continue }
    const okJump = await waitFor(`(location.hash||'').includes(${JSON.stringify(expect)})`, 6000)
    console.log(`${okJump ? '✓' : '✗'} ${page}  点「${label}」→ ${okJump ? '已跳到 ' + expect : '**没有跳转**（12 个跳转链之一已失效）'}`)
    if (!okJump) fails++

    // B. 说明文字不该可点
    const badListening = await evaluate(`(() => {
      const bad = []
      for (const e of document.querySelectorAll('.section-title__extra')) {
        if (e.querySelector('.section-title__hit')) continue      // 真跳转的，跳过
        const ls = getEventListeners(e)
        if (ls.click && ls.click.length) bad.push((e.innerText || '').trim().slice(0, 20))
        for (const c of e.querySelectorAll('*')) {
          const cl = getEventListeners(c)
          if (cl.click && cl.click.length) bad.push((c.innerText || '').trim().slice(0, 20))
        }
      }
      return bad
    })()`)
    if (badListening && badListening.length) {
      console.log(`✗ ${page}  这些说明文字仍挂了 click：${JSON.stringify(badListening)}`); fails++
    } else {
      console.log(`  ↳ ${page}  说明性 extra 均未挂 click`)
    }
   }
  }

  console.log(`\n==== section-title 检查：${fails ? fails + ' 项失败' : '全部通过'} ====`)
  cleanup()
  process.exit(fails ? 1 : 0)
}

main().catch(e => { console.error('FAIL', e.message); cleanup(); process.exit(1) })
