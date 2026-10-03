// 诊断：xcx-section-title-check 的三个「登录未落地」是不是竞态（captcha 还没到就被读走）。
//
// 背景（W1.12，2026-10-02）：验证码从「前端 Math.random 本地生成」改成「服务端签发」后，
// 它变成了一次**异步**请求（`generateCaptcha()` 在 `onMounted` 里 await apiClient.getCaptcha()）。
// 而 xcx-* 这一组探针的登录步骤是「等 uni-input 数>=3 → 立刻读 .captcha-code 的 textContent」，
// 元素数够 ≠ 验证码已回。于是探针可能把空串写进验证码框 ⇒ 服务端拒绝 ⇒ 「登录未落地」。
// 本脚本把「填表那一刻的验证码是什么」打出来，用来区分：
//   · 空串/上一轮的码  ⇒ 探针的读法跟不上异步签发（环境/探针问题）
//   · 与服务端一致仍失败 ⇒ 真回归
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const APP = process.env.XCX_BASE || 'http://127.0.0.1:5180'
const PORT = 9900 + Math.floor(Math.random() * 90)
const profile = mkdtempSync(join(tmpdir(), 'cdpdiag-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--hide-scrollbars', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank'
], { stdio: 'ignore' })
const sleep = ms => new Promise(r => setTimeout(r, ms))
const cleanup = () => { try { chrome.kill() } catch {}; try { rmSync(profile, { recursive: true, force: true }) } catch {} }

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
const evaluate = async expr => (await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true, includeCommandLineAPI: true })).result?.result?.value
await send('Page.enable'); await send('Runtime.enable')
const waitFor = async (expr, ms = 30000) => { const end = Date.now() + ms; while (Date.now() < end) { try { if (await evaluate(expr)) return true } catch {}; await sleep(300) } return false }

const USERS = [['2023020616', '/pages/student/'], ['10001', '/pages/teacher/'], ['collegeadmin1', '/pages/college-admin/']]

for (const [user, home] of USERS) {
  for (const mode of ['as-is', 'waitCaptcha']) {
    await evaluate('localStorage.clear()')
    await send('Page.navigate', { url: APP })
    const ready = await waitFor(`document.querySelectorAll('uni-input').length>=3`, 30000)
    if (mode === 'waitCaptcha') await waitFor(`(document.querySelector('.captcha-code')?.textContent||'').trim().length>0`, 10000)
    const captchaAtFill = await evaluate(`(document.querySelector('.captcha-code')?.textContent||'').trim()`)
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
    const landed = await waitFor(`(localStorage.getItem('token')||'') && (location.hash||'').includes(${JSON.stringify(home)})`, 15000)
    const state = await evaluate(`JSON.stringify({ hash: location.hash, token: !!localStorage.getItem('token'),
      err: (document.body.innerText||'').split('\\n').filter(l => /验证码|密码|不存在|审批|错误|失败/.test(l)).slice(0, 3) })`)
    console.log(`${user}\t${mode}\tready=${ready}\tcaptcha="${captchaAtFill}"\tlanded=${landed}\t${state}`)
    if (landed) break
  }
}
cleanup(); process.exit(0)
