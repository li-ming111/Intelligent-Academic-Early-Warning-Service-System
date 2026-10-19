// W48 定点核对：`Page.navigate` 到 /admin/messages 到底落在哪？
// 起因：probe-w48-ws.mjs 打 admin/messages，诊断打印出的落点却是 /admin/dashboard。
// 要分清是"路由守卫真把它弹回首页"（产品缺陷）还是"探针自己的加载竞态"。
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const PORT = 9990 + Math.floor(Math.random() * 8)
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
const profile = mkdtempSync(join(tmpdir(), 'cdpr-'))
const chrome = spawn(CHROME, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank'], { stdio: 'ignore' })
const sleep = ms => new Promise(r => setTimeout(r, ms))
const clean = () => { try { chrome.kill() } catch {} try { rmSync(profile, { recursive: true, force: true }) } catch {} }
const main = async () => {
  let t = null
  for (let i = 0; i < 80; i++) { try { const l = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json(); t = l.find(x => x.type === 'page'); if (t) break } catch {} await sleep(250) }
  const ws = new WebSocket(t.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
  let id = 0; const p = new Map()
  ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && p.has(m.id)) { p.get(m.id)(m); p.delete(m.id) } }
  const send = (m, pr = {}) => new Promise(r => { const i = ++id; p.set(i, r); ws.send(JSON.stringify({ id: i, method: m, params: pr })) })
  const ev = async x => (await send('Runtime.evaluate', { expression: x, returnByValue: true, awaitPromise: true })).result?.result?.value
  await send('Page.enable'); await send('Runtime.enable')
  await send('Page.navigate', { url: 'http://localhost:5173/login' }); await sleep(3000)
  const wf = async (x, tt = 40000) => { const d = Date.now() + tt; while (Date.now() < d) { try { if (await ev(x)) return true } catch {} await sleep(400) } return false }
  await wf(`/^[A-Z0-9]{4}$/.test((document.querySelector('.lr-captcha-btn')?.textContent||'').trim())`)
  await ev(`(()=>{const s=(q,v)=>{const e=document.querySelector(q);if(!e)return false;Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));return true};s('input[placeholder="请输入学号或工号"]','admin');s('input[placeholder="请输入密码"]','123456');s('input[placeholder="验证码"]',(document.querySelector('.lr-captcha-btn')?.textContent||'').trim());document.querySelector('.lr-submit')?.click();return true})()`)
  await wf(`location.pathname!=='/login'`, 30000)
  console.log('登录后落点:', await ev('location.pathname'))
  // 连打两次，看落点是否稳定
  for (let i = 1; i <= 2; i++) {
    await send('Page.navigate', { url: 'http://localhost:5173/admin/messages' })
    await sleep(3500)
    const path = await ev('location.pathname')
    const eq = path === '/admin/messages'
    console.log(`第${i}次导航 /admin/messages → 实际 ${path}  ${eq ? '✅ 没被弹走' : '⚠️ 被弹走了'}`)
  }
  clean(); process.exit(0)
}
main().catch(e => { console.log('FAIL', e.message); clean(); process.exit(1) })
