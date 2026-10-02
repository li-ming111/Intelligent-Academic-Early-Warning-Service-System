// 探针：验证"ElMessage 提示收集器"本身是不是有效。
//
// 为什么值得单独写一个：
//   admin-messages-page.mjs 在「下游挂了」这一轮里，连续轮询 10 秒也没看到任何提示，
//   于是那句"失败时页面仍然静默"看起来成立。但**收集器没被验证过**的话，
//   "没收到提示"同样可能只是"选择器/可见性判断写错了"——
//   这正是本次会话第三次踩同一类坑（前两次：`\r` 字节比较、mimeType 过滤）。
//
//   这里用一个**确定会弹提示**的动作来打靶：不勾任何消息直接点「批量删除」，
//   页面必定走 `ElMessage.warning('请先选择要删除的消息')`。
//   如果这个都收不到，那"没收到"就不能当证据用。
//
// 用法: node probe-toast-collector.mjs [账号] [密码]
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || 'admin'
const PASS = process.argv[3] || '123456'
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9700 + Math.floor(Math.random() * 80)
const profile = mkdtempSync(join(tmpdir(), 'cdpt-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

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
    return r.result?.result?.value
  }
  const collect = () => evaluate(`[...document.querySelectorAll('.el-message')]
      .filter((e) => e.getBoundingClientRect().height > 0)
      .map((e) => e.innerText.replace(/\\n/g, ' ').trim())`)

  await send('Page.enable'); await send('Runtime.enable')
  closeBrowser = async () => { try { await send('Browser.close') } catch {} }
  await send('Page.navigate', { url: `${APP}/login` })
  for (let i = 0; i < 60; i++) { await sleep(500); if (await evaluate(`!!document.querySelector('.lr-submit')`)) break }
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
    document.querySelector('.lr-submit')?.click()
    return { a, b, c }
  })()`)
  if (!filled?.a || !filled?.b || !filled?.c) throw new Error(`登录表单没找全: ${JSON.stringify(filled)}`)
  let path = ''
  for (let i = 0; i < 30; i++) { await sleep(500); path = await evaluate('location.pathname'); if (path && path !== '/login') break }
  console.log(`已登录 ${USER} → ${path}`)

  await send('Page.navigate', { url: `${APP}/admin/messages` })
  await sleep(6000)

  // 先看没点之前有没有残留提示（基线）
  console.log(`点击前可见提示: ${JSON.stringify(await collect())}`)

  const clicked = await evaluate(`(() => {
    const b = [...document.querySelectorAll('button')].find((x) => x.innerText.trim() === '批量删除')
    if (!b) return '找不到批量删除按钮'
    b.click(); return 'clicked'
  })()`)
  console.log(`点击「批量删除」: ${clicked}`)

  const seen = []
  for (let i = 0; i < 20; i++) {
    await sleep(200)
    const t = await collect()
    if (Array.isArray(t)) for (const s of t) if (s && !seen.includes(s)) seen.push(s)
  }
  console.log(`点击后轮询到的提示: ${JSON.stringify(seen)}`)
  // 额外看一眼 4 秒后还在不在（说明收集窗口够不够）
  await sleep(3000)
  console.log(`再过 3 秒可见提示:   ${JSON.stringify(await collect())}`)

  console.log(`\n结论: ${seen.length ? '收集器有效（能收到 ElMessage）' : '收集器**收不到**提示 —— 它给出的"没提示"不能当证据'}`)
  return seen.length ? 0 : 1
}

let code = 1
let closeBrowser = async () => {}
try { code = await main() } catch (e) { console.log(`脚本异常: ${e.message}`) }
// 直接 kill 浏览器会让 vite 的 dev server 吃到一次未捕获的 socket ECONNRESET 而**整个进程退出**
// （本机实测，跑一次脚本死一次）。先让浏览器自己把连接关干净，再兜底 kill。
await closeBrowser()
await sleep(800)
try { chrome.kill() } catch {}
try { rmSync(profile, { recursive: true, force: true }) } catch {}
process.exit(code)
