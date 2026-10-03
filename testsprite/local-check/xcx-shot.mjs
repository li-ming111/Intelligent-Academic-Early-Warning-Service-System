// 给指定页面截图，用来肉眼核对**视觉类改动**（触达尺寸、配色、字号）。
//
// 冒烟只能证明"页面有真数据、没报错"，证明不了"按钮没被撑破/没被裁掉"——
// 一次 height 从 60rpx 改到 88rpx 的调整，数据判据一律照过。
//
// 用法: node xcx-shot.mjs <role> <outPrefix> <path> [<path>...]
// 例:   node xcx-shot.mjs admin shot-admin /pages/admin/users
// 输出: <outPrefix>-<序号>.png（1440x2560，模拟手机视口）
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.XCX_BASE || 'http://127.0.0.1:5180'
const ACCOUNTS = {
  student: '2023020616', teacher: '10001', counselor: '1000000001',
  admin: 'admin', 'college-admin': 'collegeadmin1'
}

const role = process.argv[2]
const prefix = process.argv[3]
const paths = process.argv.slice(4)
if (!ACCOUNTS[role] || !prefix || !paths.length) {
  console.error('用法: node xcx-shot.mjs <role> <outPrefix> <path>...')
  process.exit(2)
}

const PORT = 9900 + Math.floor(Math.random() * 90)
const profile = mkdtempSync(join(tmpdir(), 'cdpshot-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--hide-scrollbars',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank'
], { stdio: 'ignore' })
const sleep = ms => new Promise(r => setTimeout(r, ms))
const cleanup = () => { try { chrome.kill() } catch {}; try { rmSync(profile, { recursive: true, force: true }) } catch {} }

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
  const evaluate = async expr =>
    (await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })).result?.result?.value

  await send('Page.enable'); await send('Runtime.enable')
  // 手机视口：750rpx 设计稿按 DPR=2 铺满 750 物理像素
  await send('Emulation.setDeviceMetricsOverride', {
    width: 375, height: 812, deviceScaleFactor: 2, mobile: true
  })
  await send('Page.navigate', { url: APP })

  const waitFor = async (expr, ms = 45000) => {
    const end = Date.now() + ms
    while (Date.now() < end) { try { if (await evaluate(expr)) return true } catch {}; await sleep(400) }
    return false
  }

  // ⚠️ 必须连验证码一起等：W1.12 起验证码是服务端异步签发的，「表单元素到位」不再等于「码已到手」
  //    （实测读到空串 ⇒ 服务端 401 拒绝 ⇒ 探针报假「登录未落地」，见 _w4_login_diag.mjs）。
  await waitFor(`document.querySelectorAll('uni-input').length >= 3 && (document.querySelector('.captcha-code')?.textContent || '').trim().length > 0`)
  // 登录页要在**填之前**截 —— 点完登录会有 1.2s 的 showToast 遮罩，
  // 那层半透明会把整页冲成灰白，截出来不是真实观感。
  await sleep(600)
  await shoot('登录页', 0)

  await evaluate(`(() => {
    const byPh = (ph) => {
      const w = Array.from(document.querySelectorAll('uni-input'))
        .find(u => (u.querySelector('.uni-input-placeholder')?.textContent || '').trim() === ph)
      return w ? w.querySelector('input') : null
    }
    const setVal = (el, v) => {
      if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, v)
      el.dispatchEvent(new Event('input', { bubbles: true }))
      el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    setVal(byPh('请输入学号或工号'), ${JSON.stringify(ACCOUNTS[role])})
    setVal(byPh('请输入密码'), '123456')
    setVal(byPh('输入验证码'), (document.querySelector('.captcha-code')?.textContent || '').trim())
    document.querySelector('.btn')?.click()
    return true
  })()`)

  const ok = await waitFor(`(localStorage.getItem('token') ? 1 : 0) === 1 && (location.hash||'').includes('/pages/${role}/')`, 30000)
  if (!ok) { console.error('登录未落地'); cleanup(); process.exit(1) }

  let n = 0
  for (const p of paths) {
    n++
    await evaluate(`location.hash = ${JSON.stringify('#' + p)}`)
    await sleep(4500)
    await shoot(p, n)
  }

  async function shoot(label, idx) {
    const name = `${prefix}-${String(idx).padStart(2, '0')}.png`
    const r = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
    const data = r.result?.data
    if (!data) { console.error(`截图失败 ${name}`); return }
    writeFileSync(name, Buffer.from(data, 'base64'))
    console.log(`${name}  ← ${label}`)
  }

  cleanup()
  process.exit(0)
}

main().catch(e => { console.error('FAIL', e.message); cleanup(); process.exit(1) })
