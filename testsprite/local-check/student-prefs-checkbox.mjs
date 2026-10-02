// 学生「消息中心」左侧「订阅偏好」的勾选状态，是否与后端返回的 1/0 对得上。
//
// 起因（2026-10-01 第二十四轮）：该页把后端返回的 1/0 直接 `Object.assign` 给
// `el-checkbox` 的 v-model，而 el-checkbox 判的是 `modelValue === trueValue`
// （trueValue 默认 `true`）⇒ `1 === true` 为 false ⇒ **九个勾一律显示未勾选**，
// 与库里的真实设置相反。接口返回、数据库、控制台全都正常，只有渲染是错的 ——
// 所以判据必须是"**DOM 里哪个勾被勾上了**"对"**接口说哪个键是 1**"。
//
// 用法: node student-prefs-checkbox.mjs [学号] [密码]   退出码 0=一致, 1=不一致
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || '2023020616'
const PASS = process.argv[3] || '123456'
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9500 + Math.floor(Math.random() * 90)
const profile = mkdtempSync(join(tmpdir(), 'cdppref-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const cleanup = () => { try { chrome.kill() } catch {}; try { rmSync(profile, { recursive: true, force: true }) } catch {} }

// 勾选框文案 → 后端键。两边对不上的键会被判"没验到"，不允许静默忽略。
const LABEL_TO_KEY = {
  '订阅预警通知': 'subscribeWarnings',
  '低级预警': 'subscribeLow',
  '中级预警': 'subscribeMedium',
  '高级预警': 'subscribeHigh',
  '帮扶计划更新': 'subscribeAssistance',
  '系统消息': 'subscribeSystem',
  '应用内通知': 'pushApp',
  '邮件通知': 'pushEmail',
  '短信通知': 'pushSms'
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
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id) }
  }
  const send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) return { __err: r.result.exceptionDetails.exception?.description }
    return r.result?.result?.value
  }
  const closeBrowser = async () => { try { await send('Browser.close') } catch {} }

  await send('Page.enable'); await send('Runtime.enable')
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
    const shown = (await (async () => {
      // W1.12：验证码改由服务端签发，按钮会先显示「加载中…」——必须等它变成一个
      // 4 位码再读，否则填进去的是「加载中…」，症状是"登录失败"，看着像账号坏了。
      for (let i = 0; i < 60; i++) {
        const t = (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
        if (/^[A-Z0-9]{4}$/.test(t)) return t
        await new Promise((r) => setTimeout(r, 200))
      }
      return (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
    })())
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
  console.log(`已登录 ${USER} → ${path}\n`)

  await send('Page.navigate', { url: `${APP}/student/messages` })
  await sleep(6000)

  // 后端自己怎么说（用页面自己的 token，走完整网关链）
  const api = await evaluate(`(async () => {
    const t = localStorage.getItem('token')
    const h = { Authorization: 'Bearer ' + t }
    const me = await (await fetch('/api/students/student-by-user/' + localStorage.getItem('userId'), { headers: h })).json()
    const profileId = me?.data?.id
    if (!profileId) return { error: '拿不到 profileId: ' + JSON.stringify(me).slice(0, 120) }
    const r = await (await fetch('/api/students/subscription/' + profileId + '/preferences', { headers: h })).json()
    return { profileId, prefs: r?.data }
  })()`)
  if (api?.error) throw new Error(api.error)
  for (const [k, v] of Object.entries(api.prefs || {})) if (k !== 'id' && k !== 'studentId') console.log(`  ${k} = ${v}`)

  // 页面上每个勾选框当前是勾/未勾
  const dom = await evaluate(`(() => {
    const out = {}
    document.querySelectorAll('.el-checkbox').forEach((c) => {
      const label = (c.innerText || '').replace(/\\s+/g, '')
      out[label] = c.className.includes('is-checked')
    })
    return out
  })()`)
  console.log('\n页面上的勾选状态：')
  for (const [label, checked] of Object.entries(dom)) console.log(`  ${checked ? '[✓]' : '[ ]'} ${label}`)

  console.log('\n===== 判定（文案 → 键 → 后端值 vs 页面勾选）=====')
  let bad = 0, checkedCount = 0, unmatched = 0
  for (const [label, key] of Object.entries(LABEL_TO_KEY)) {
    const raw = api.prefs?.[key]
    // 页面上文案可能带前缀空格，用"包含"匹配
    const domLabel = Object.keys(dom).find((l) => l.includes(label))
    if (domLabel === undefined) { console.log(`  ? ${label.padEnd(8)} 页面上找不到这个勾选框`); unmatched++; continue }
    const want = Number(raw) === 1
    const got = dom[domLabel]
    if (got) checkedCount++
    const ok = want === got
    if (!ok) bad++
    console.log(`  ${ok ? 'OK  ' : 'MISMATCH'} ${label.padEnd(8)} 后端 ${key}=${raw} (期望${want ? '勾' : '不勾'}) / 页面 ${got ? '勾' : '不勾'}`)
  }
  console.log(`\n  页面上勾上的数量: ${checkedCount}  对不上的: ${bad}  页面上没找到的: ${unmatched}`)

  // 可选：顺手验一次「保存设置」链路（勾上"短信通知"→保存）。
  // 这一步会**真的写库**，调用方必须自己复原（脚本只如实报告写成了什么）。
  if (process.argv[4] === 'save') {
    console.log('\n===== 保存设置（把「短信通知」勾上）=====')
    const toggled = await evaluate(`(() => {
      const box = [...document.querySelectorAll('.el-checkbox')].find(c => (c.innerText || '').includes('短信通知'))
      if (!box) return false
      box.querySelector('input')?.click(); return true
    })()`)
    await sleep(600)
    const saved = await evaluate(`(() => {
      const b = [...document.querySelectorAll('button')].find(e => e.textContent.trim() === '保存设置')
      if (!b) return false
      b.click(); return true
    })()`)
    await sleep(3000)
    const toasts = await evaluate(`[...document.querySelectorAll('.el-message__content')].map(e => e.textContent.trim())`)
    const after = await evaluate(`(async () => {
      const h = { Authorization: 'Bearer ' + localStorage.getItem('token') }
      const r = await (await fetch('/api/students/subscription/${api.profileId}/preferences', { headers: h })).json()
      return r?.data
    })()`)
    console.log(`  勾上「短信通知」=${toggled}  点「保存设置」=${saved}  提示=${JSON.stringify(toasts)}`)
    console.log(`  保存后后端 pushSms=${after?.pushSms}（应为 1） 其余键: ` +
      Object.entries(after || {}).filter(([k]) => k !== 'id' && k !== 'studentId' && k !== 'pushSms')
        .map(([k, v]) => `${k}=${v}`).join(' '))
    if (Number(after?.pushSms) !== 1) { console.log('  → 保存没落地'); return 1 }
    console.log('  → 保存真的写进去了（记得把 pushSms 复原成 0）')
    return 0
  }
  if (bad === 0 && unmatched === 0) { console.log('  → 勾选状态与后端一致'); return 0 }
  console.log('  → 勾选状态与后端**不一致**（或有的勾选框整个缺失）')
  return 1
}

let code = 1
let closeBrowser = async () => {}
try { code = (await main()) ? 1 : 0 } catch (e) { console.log(`\n脚本异常: ${e.message}`) }
await closeBrowser()
await sleep(800)
cleanup()
console.log(code === 0 ? '\nPASS(0)  勾选状态对得上' : '\nFAIL(1)  勾选状态对不上')
process.exit(code)
