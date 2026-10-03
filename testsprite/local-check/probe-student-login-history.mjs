// 学生端设置页「登录历史」卡片：DOM 里真的渲染出登录记录了吗？
//
// 起因（2026-10-03，W8.3）：这一格曾经**永远空**。
//   后端端点 `GET /api/users/{id}/login-logs` 2026-10-02 才加（带 isAdminOrSelf 归属校验），
//   但**网关**的自助白名单 `USER_SELF_PATTERN` 只认 `{id}` / `info/{id}` / `update/{id}` /
//   `{id}/settings`，把 `{id}/login-logs` 漏在外面 ⇒ 学生/教师/辅导员经网关一律
//   HTTP 403 `{"code":403,"message":"权限不足"}`（**直连 user-service :8082 同一 token 是 200 有数据**）。
//   症状：设置页「登录历史」卡片恒显示「暂无登录记录」+ 控制台一行「加载登录历史失败」，
//   接口、库、网关日志各自看着都"正常"—— 只有把 DOM 和接口对着看才抓得住。
//
// 判据（不是"看到有行就算过"）：
//   A 接口层：用**页面自己的 token** 走完整网关链打 `/{uid}/login-logs`，必须 code=200 且条数 > 0
//   B DOM 层：`.ss-log` 行数 **等于** A 的条数（多一行少一行都判失败）
//   C 空态：`暂无登录记录` 与 `.ss-empty` **必须不在**（在 = 卡片还是空的）
//   D 「上次登录」：必须不是「暂无记录」/「--」（它是从同一份 logs 派生出来的）
//
// 用法: node probe-student-login-history.mjs [学号] [密码]   退出码 0=一致, 1=不一致
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || '2023020616'
const PASS = process.argv[3] || '123456'
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9600 + Math.floor(Math.random() * 80)
const profile = mkdtempSync(join(tmpdir(), 'cdplog-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const cleanup = () => { try { chrome.kill() } catch {}; try { rmSync(profile, { recursive: true, force: true }) } catch {} }

const fails = []
const say = (s = '') => console.log(s)
const check = (name, ok, detail = '') => {
  say(`  ${ok ? '✓' : '✗'} ${name}`)
  if (detail) say(`      ${detail}`)
  if (!ok) fails.push(name)
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
  const net4xx = []
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Network.responseReceived') {
      const r = m.params.response
      if (r.url.includes('/api/') && r.status >= 400) net4xx.push(`${r.status} ${r.url.replace(/^https?:\/\/[^/]+/, '').split('?')[0]}`)
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
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: `${APP}/login` })
  await sleep(3000)
  const filled = await evaluate(`(async () => {
    const setVal = (sel, val) => {
      const el = document.querySelector(sel); if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    // 验证码服务端签发：必须等它变成 4 位码再读（读早了拿到「加载中…」⇒ 假登录失败）
    const shown = await (async () => {
      for (let i = 0; i < 60; i++) {
        const t = (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
        if (/^[A-Z0-9]{4}$/.test(t)) return t
        await new Promise((r) => setTimeout(r, 200))
      }
      return (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
    })()
    const a = setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(USER)})
    const b = setVal('input[placeholder="请输入密码"]', ${JSON.stringify(PASS)})
    const c = setVal('input[placeholder="验证码"]', shown)
    const btn = document.querySelector('.lr-submit'); if (btn) btn.click()
    return { a, b, c, clicked: !!btn }
  })()`)
  if (!filled?.a || !filled?.clicked) throw new Error(`登录表单没找全: ${JSON.stringify(filled)}`)
  let path = ''
  for (let i = 0; i < 30; i++) { await sleep(500); path = await evaluate('location.pathname'); if (path && path !== '/login') break }
  if (!path || path === '/login') throw new Error(`登录失败（${USER}）`)
  say(`已登录 ${USER} → ${path}\n`)

  say('[A] 接口层（用页面自己的 token，走完整网关链）')
  const api = await evaluate(`(async () => {
    const t = localStorage.getItem('token')
    const uid = localStorage.getItem('userId')
    const r = await fetch('/api/users/' + uid + '/login-logs', { headers: { Authorization: 'Bearer ' + t } })
    let body = null; try { body = await r.json() } catch {}
    return { status: r.status, uid, code: body?.code, n: Array.isArray(body?.data) ? body.data.length : null, message: body?.message }
  })()`)
  const apiN = api?.n
  check(`A 接口回 200 且有记录（uid=${api?.uid}）`,
        api?.status === 200 && api?.code === 200 && typeof apiN === 'number' && apiN > 0,
        `HTTP ${api?.status} code=${api?.code} 条数=${apiN} message=${api?.message}`)

  say('\n[B] DOM 层（设置页「登录历史」卡片）')
  await send('Page.navigate', { url: `${APP}/student/settings` })
  await sleep(6000)
  const dom = await evaluate(`(() => {
    const rows = document.querySelectorAll('.ss-log')
    const texts = [...rows].map(r => (r.innerText || '').replace(/\\s+/g, ' ').trim().slice(0, 90))
    const card = [...document.querySelectorAll('.el-card')].find(c => (c.querySelector('.ss-card-title')?.textContent || '').includes('登录历史'))
    // 「上次登录」那格在同一页的另一张卡里
    const lastLogin = [...document.querySelectorAll('div')].map(d => d.innerText || '').find(t => /上次登录/.test(t)) || ''
    return {
      rowCount: rows.length,
      rows: texts,
      emptyStateShown: !!card && !!card.querySelector('.ss-empty'),
      emptyStateText: card?.querySelector('.ss-empty')?.textContent?.trim() || null,
      hasCard: !!card,
      lastLogin: lastLogin.replace(/\\s+/g, ' ').trim().slice(0, 120),
    }
  })()`)
  if (!dom?.hasCard) throw new Error('页面上找不到「登录历史」卡片（选择器可能变了）')
  check('B1 卡片上渲染的登录行数 == 接口条数',
        dom.rowCount === apiN,
        `DOM ${dom.rowCount} 行 vs 接口 ${apiN} 条`)
  check('C 空态「暂无登录记录」没有出现', dom.emptyStateShown === false,
        `emptyState=${dom.emptyStateShown} text=${JSON.stringify(dom.emptyStateText)}`)
  const ll = dom.lastLogin || ''
  check('D 「上次登录」不是「暂无记录」/「--」',
        ll.includes('上次登录') && !/暂无记录|--/.test(ll),
        ll)

  say('\n前 3 行渲染出来的内容：')
  ;(dom.rows || []).slice(0, 3).forEach((t, i) => say(`  ${i + 1}. ${t}`))
  if (net4xx.length) say(`\n期间 4xx/5xx：${JSON.stringify([...new Set(net4xx)])}`)

  say('\n================ 汇总 ================')
  say(fails.length ? `失败 ${fails.length} 项：${fails.join(' / ')}` : '全部通过')
  return fails.length ? 1 : 0
}

main().then((c) => { cleanup(); process.exit(c) })
  .catch((e) => { cleanup(); console.error('探针异常:', e.message); process.exit(2) })
