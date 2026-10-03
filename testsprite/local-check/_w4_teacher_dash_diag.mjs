// 诊断：xcx-section-title-check 在 10001（教师）上报 NO_HIT —— 页面上到底有没有 section-title。
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const APP = process.env.XCX_BASE || 'http://127.0.0.1:5180'
const PORT = 9900 + Math.floor(Math.random() * 90)
const profile = mkdtempSync(join(tmpdir(), 'cdptd-'))
const chrome = spawn(CHROME, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--hide-scrollbars', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank'], { stdio: 'ignore' })
const sleep = ms => new Promise(r => setTimeout(r, ms))
const cleanup = () => { try { chrome.kill() } catch {}; try { rmSync(profile, { recursive: true, force: true }) } catch {} }

let target = null
for (let i = 0; i < 80; i++) {
  try { const l = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json(); target = l.find(t => t.type === 'page'); if (target) break } catch {}
  await sleep(250)
}
const ws = new WebSocket(target.webSocketDebuggerUrl)
await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
let id = 0; const pending = new Map(); const logs = []
ws.onmessage = e => { const m = JSON.parse(e.data)
  if (m.method === 'Runtime.consoleAPICalled') logs.push((m.params.args || []).map(a => a.value ?? a.description).join(' '))
  if (m.method === 'Runtime.exceptionThrown') logs.push('EXC ' + (m.params.exceptionDetails?.exception?.description || '').split('\n')[0])
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id) } }
const send = (method, params = {}) => new Promise(res => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
const evaluate = async expr => (await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true, includeCommandLineAPI: true })).result?.result?.value
await send('Page.enable'); await send('Runtime.enable')
const waitFor = async (expr, ms = 30000) => { const end = Date.now() + ms; while (Date.now() < end) { try { if (await evaluate(expr)) return true } catch {}; await sleep(300) } return false }

await send('Page.navigate', { url: APP })
await waitFor(`document.querySelectorAll('uni-input').length >= 3 && (document.querySelector('.captcha-code')?.textContent||'').trim().length > 0`)
await evaluate(`(() => {
  const byPh = (ph) => { const w = Array.from(document.querySelectorAll('uni-input'))
    .find(u => (u.querySelector('.uni-input-placeholder')?.textContent || '').trim() === ph); return w ? w.querySelector('input') : null }
  const setVal = (el, v) => { if (!el) return false
    Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, v)
    el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true })); return true }
  setVal(byPh('请输入学号或工号'), '10001')
  setVal(byPh('请输入密码'), '123456')
  setVal(byPh('输入验证码'), (document.querySelector('.captcha-code')?.textContent || '').trim())
  document.querySelector('.btn')?.click() })()`)
console.log('landed:', await waitFor(`(location.hash||'').includes('/pages/teacher/')`, 20000))
await evaluate(`location.hash = '#/pages/teacher/dashboard'`)
await sleep(5000)
console.log('hash:', await evaluate('location.hash'))
console.log('body head:', await evaluate(`(document.body.innerText||'').split('\\n').slice(0,12).join(' | ')`))
console.log('section-title count:', await evaluate(`document.querySelectorAll('[class*=section-title]').length`))
console.log('section-title classes:', await evaluate(`JSON.stringify([...new Set([...document.querySelectorAll('[class*=section-title]')].map(e=>e.className))])`))
console.log('hit elements:', await evaluate(`JSON.stringify([...document.querySelectorAll('.section-title__hit')].map(e=>(e.innerText||'').trim()))`))
console.log('extras:', await evaluate(`JSON.stringify([...document.querySelectorAll('.section-title__extra')].map(e=>(e.innerText||'').trim()))`))
console.log('titles:', await evaluate(`JSON.stringify([...document.querySelectorAll('.section-title__title,.section-title')].map(e=>(e.innerText||'').trim().slice(0,20)))`))
console.log('console tail:', JSON.stringify(logs.slice(-8)))
cleanup(); process.exit(0)
