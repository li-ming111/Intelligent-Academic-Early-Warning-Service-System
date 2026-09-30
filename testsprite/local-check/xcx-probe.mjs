// 小程序端（H5 通道）DOM 探针 —— 断言失败时用来"看真东西"。
//
// xcx-login.mjs 只回答"页面正文里有没有这段字"，答不了"为什么没有"。
// 这个脚本登录后跳到指定内页，把某个选择器的 outerHTML / 计算样式打出来。
//
// 用法:
//   NAV=/pages/student/scores SEL=".score-item" node xcx-probe.mjs 2023020616 123456
//   NAV=/pages/student/scores SEL=".score" STYLE=1 node xcx-probe.mjs 2023020616 123456
//
// 环境变量:
//   NAV     登录后跳转的 hash 路径（必填）
//   SEL     要 dump 的 CSS 选择器，默认 body
//   ALL     =1 时 dump 所有匹配项的 outerHTML
//   STYLE   =1 时同时打印匹配项的计算样式（排查"看不见"的问题）
//   LIMIT   截断长度，默认 3000
//   SHOT    截图存到这个路径（PNG）。设计稿只能靠眼睛看，正文 dump 看不出美丑。
//   H       =1 时截整页（默认只截视口）
//   JS      截图前先执行的任意表达式，结果打印出来。用来问"为什么是空的"这类
//           只有页面内部才知道答案的问题（例如把 v-for 的绑定值打出来）。
//   SCROLL  默认 'top'：截图前把滚动位置归零。不归零的话视口停在上一页的偏移上，
//           截出来的是半截列表，Hero 区永远看不到。
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const [USER, PASS] = process.argv.slice(2)
const NAV = process.env.NAV || ''
const SEL = process.env.SEL || 'body'
const ALL = process.env.ALL === '1'
const WITH_STYLE = process.env.STYLE === '1'
const LIMIT = Number(process.env.LIMIT || 3000)

if (!USER || !PASS || !NAV) {
  console.error('用法: NAV=/pages/x/y [SEL=".sel"] node xcx-probe.mjs <用户名> <密码>')
  process.exit(2)
}

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.XCX_BASE || 'http://127.0.0.1:5180'
const PORT = 9631

const profile = mkdtempSync(join(tmpdir(), 'cdpprobe-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = ms => new Promise(r => setTimeout(r, ms))
const cleanup = () => {
  try { chrome.kill() } catch {}
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
}

const main = async () => {
  let target = null
  for (let i = 0; i < 60; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      target = list.find(t => t.type === 'page')
      if (target) break
    } catch {}
    await sleep(250)
  }
  if (!target) { console.error('Chrome 调试端口未就绪'); process.exit(1) }

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
  const evaluate = async expr => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    return r.result?.result?.value
  }

  await send('Page.enable')
  await send('Runtime.enable')
  await send('Page.navigate', { url: APP })

  const waitFor = async (expr, timeoutMs = 45000) => {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      try { if (await evaluate(expr)) return true } catch {}
      await sleep(400)
    }
    return false
  }

  await waitFor(`document.querySelectorAll('uni-input .uni-input-placeholder').length >= 3 && !!document.querySelector('.btn')`)
  await evaluate(`(() => {
    const byPh = (ph) => {
      const wrap = Array.from(document.querySelectorAll('uni-input'))
        .find(u => (u.querySelector('.uni-input-placeholder')?.textContent || '').trim() === ph)
      return wrap ? wrap.querySelector('input') : null
    }
    const setVal = (el, val) => {
      if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true }))
      el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    setVal(byPh('请输入学号或工号'), ${JSON.stringify(USER)})
    setVal(byPh('请输入密码'), ${JSON.stringify(PASS)})
    setVal(byPh('输入验证码'), (document.querySelector('.captcha-code')?.textContent || '').trim())
    document.querySelector('.btn')?.click()
    return true
  })()`)

  await sleep(5000)
  await evaluate(`location.hash = ${JSON.stringify('#' + NAV.replace(/^#/, ''))}`)
  await waitFor(`(location.hash || '').includes(${JSON.stringify(NAV)})`, 25000)
  await sleep(5000)

  const withJS = process.env.JS || ''
  if (withJS) {
    const r = await evaluate(withJS)
    console.log('JS 结果 =', typeof r === 'string' ? r : JSON.stringify(r, null, 2))
  }

  const SCROLL = process.env.SCROLL === '' ? '' : (process.env.SCROLL || 'top')
  if (SCROLL === 'top') {
    // uni-app H5 的滚动容器不一定是 window：可能是 uni-page-wrapper 或某个
    // scroll-view。全量归零最省事，也避免"截图看着缺一块"被误读成布局 bug。
    await evaluate(`(() => {
      window.scrollTo(0, 0)
      for (const el of document.querySelectorAll('*')) {
        if (el.scrollTop > 0) el.scrollTop = 0
      }
      return document.documentElement.scrollTop || window.scrollY || 0
    })()`)
    await sleep(600)
  }

  const SHOT = process.env.SHOT || ''
  if (SHOT) {
    // 手机视口。小程序端在桌面 Chrome 里默认是宽屏，看不出现实观感。
    await send('Emulation.setDeviceMetricsOverride', {
      width: 390, height: 844, deviceScaleFactor: 2, mobile: true
    })
    await sleep(1500)
    const shot = await send('Page.captureScreenshot', {
      format: 'png',
      captureBeyondViewport: process.env.H === '1'
    })
    const data = shot.result?.data
    if (data) {
      writeFileSync(SHOT, Buffer.from(data, 'base64'))
      console.log(`截图已保存: ${SHOT}`)
    } else {
      console.log('截图失败：CDP 未返回数据')
    }
  }

  const out = await evaluate(`(() => {
    const nodes = ${ALL} ? Array.from(document.querySelectorAll(${JSON.stringify(SEL)}))
                          : [document.querySelector(${JSON.stringify(SEL)})].filter(Boolean)
    const style = el => ${WITH_STYLE} ? (() => {
      const c = getComputedStyle(el)
      return { display: c.display, visibility: c.visibility, opacity: c.opacity, color: c.color, fontSize: c.fontSize, width: c.width, height: c.height }
    })() : undefined
    return JSON.stringify({
      count: nodes.length,
      items: nodes.slice(0, 5).map(el => ({
        tag: el.tagName,
        text: (el.textContent || '').trim().slice(0, 120),
        html: el.outerHTML.slice(0, ${LIMIT}),
        style: style(el)
      }))
    }, null, 2)
  })()`)

  console.log(out)
  cleanup()
  process.exit(0)
}

main().catch(e => { console.error('脚本异常:', e.message); cleanup(); process.exit(1) })
