// 学生 PC 端「成绩申诉」页：申诉行真的显示出了**是哪一次成绩**吗？
//
// 起因（2026-10-03，第四十三轮 W9）：`score_appeals.score_id` 在 W8.3 被补了数据、也立了不变量，
//   但**页面看不见**它 —— 后端 `appealToMap` 从不 JOIN scores，前端只有「科目」一列。
//   学生看到的是「高等数学」四个字，看不出申诉的是**第5学期那次 56.5**（同一门课有多个学期时说不清）。
//
// 本探针断言的是**页面渲染出来的字**，不是接口有没有那个字段：
//   A 接口层：走完整网关链打 `/api/students/appeals/{uid}/list`，id=6 的行必须带 scoreSemester/scoreTotal
//   B DOM 层：表格里「高等数学」那一行的文本必须含「第5学期」与「56.5」
//   C 负向对照：**只有**那一行能含「第5学期」；「大学英语」那行必须是「第4学期」+「51」
//     —— 少了 C，一个把同一个字符串印到每一行的渲染也能骗过 B
//   D 行数：表格行数 == 接口条数（多一行少一行都判失败）
//
// 用法: node probe-w9-appeal-score-ref.mjs [学号] [密码] [--stub]   退出码 0=一致, 1=不一致
//
// `--stub`：把 `/api/students/appeals/*/list` 的响应**在浏览器里**换成 `_w9_appeal_list.json`
//   （该文件是从**新构建**的 student-service 实跑出来的真响应，不是手编的）。
//   为什么需要它：现役的 8079 还是 W9 之前的包，经网关拿不到 scoreSemester ⇒ 只验得到"降级对不对"，
//   验不到"有字段时渲染对不对"。加上 `--stub` 后 B/C/D 三段验的是**渲染层**，
//   后端那一半由 A 段 + `curl` 直打新实例来验，两半各自在自己能证的层面上证。
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const USER = process.argv[2] || '2023020616'
const PASS = process.argv[3] || '123456'
const STUB = process.argv.includes('--stub')
const HERE = dirname(fileURLToPath(import.meta.url))
const STUB_BODY = STUB ? readFileSync(join(HERE, '_w9_appeal_list.json')) : null
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9700 + Math.floor(Math.random() * 80)
const profile = mkdtempSync(join(tmpdir(), 'cdpw9-'))
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
    if (m.method === 'Fetch.requestPaused') {
      const rid = m.params.requestId
      const isList = /\/api\/students\/appeals\/[^/]+\/list/.test(m.params.request.url)
      if (STUB && isList) {
        ws.send(JSON.stringify({ id: ++id, method: 'Fetch.fulfillRequest', params: {
          requestId: rid, responseCode: 200,
          responseHeaders: [{ name: 'Content-Type', value: 'application/json;charset=UTF-8' }],
          body: STUB_BODY.toString('base64'),
        } }))
      } else {
        ws.send(JSON.stringify({ id: ++id, method: 'Fetch.continueRequest', params: { requestId: rid } }))
      }
      return
    }
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
    const r = await fetch('/api/students/appeals/' + uid + '/list', { headers: { Authorization: 'Bearer ' + t } })
    let body = null; try { body = await r.json() } catch {}
    const rows = Array.isArray(body?.data) ? body.data : []
    return {
      status: r.status, uid, code: body?.code, n: rows.length,
      rows: rows.map(x => ({ id: x.id, sem: x.scoreSemester, total: x.scoreTotal, scoreId: x.scoreId })),
    }
  })()`)
  const apiRows = api?.rows || []
  const row6 = apiRows.find((r) => r.id === 6)
  if (STUB) {
    say('  （--stub 模式：本段不适用 —— 现役 8079 是 W9 之前的包，经网关拿不到 scoreSemester。')
    say('    后端那一半的证据见 curl 直打新实例 8182 的响应，本文件同目录 _w9_appeal_list.json 就是它。）')
    say(`    顺带记下现役包的实况：条数=${apiRows.length} id=6 sem=${row6?.sem} total=${row6?.total}`)
  } else {
    check('A1 接口回 200 且有申诉', api?.status === 200 && api?.code === 200 && apiRows.length > 0,
          `HTTP ${api?.status} code=${api?.code} 条数=${apiRows.length}`)
    check('A2 id=6 那一行带出 scoreSemester=5 与 scoreTotal=56.5',
          row6 && String(row6.sem) === '5' && Number(row6.total) === 56.5,
          `scoreId=${row6?.scoreId} sem=${row6?.sem} total=${row6?.total}`)
  }

  say('\n[B] DOM 层（/student/appeals 表格）')
  // 拦截必须在导航**之前**开，否则页面首次请求已经走掉了
  if (STUB) await send('Fetch.enable', { patterns: [{ urlPattern: '*/api/students/appeals/*' }] })
  await send('Page.navigate', { url: `${APP}/student/appeals` })
  await sleep(6000)
  const dom = await evaluate(`(() => {
    const rows = [...document.querySelectorAll('.el-table__row')]
    return rows.map(r => ({
      text: (r.innerText || '').replace(/\\s+/g, ' ').trim(),
      cells: [...r.querySelectorAll('.el-table__cell')].map(c => (c.innerText || '').replace(/\\s+/g, ' ').trim()),
    }))
  })()`)
  if (!Array.isArray(dom) || dom.length === 0) throw new Error('表格一行都没渲染出来（选择器可能变了，或页面还没加载完）')

  const mathRow = dom.find((r) => r.text.includes('高等数学'))
  const engRow = dom.find((r) => r.text.includes('大学英语'))
  check('B1 「高等数学」那一行渲染出「第5学期」', !!mathRow && mathRow.text.includes('第5学期'),
        mathRow ? mathRow.text.slice(0, 120) : '没找到高等数学那一行')
  check('B2 「高等数学」那一行渲染出成绩 56.5', !!mathRow && mathRow.text.includes('56.5'),
        mathRow ? mathRow.text.slice(0, 120) : '没找到高等数学那一行')

  say('\n[C] 负向对照（证伪力）')
  const sem5Rows = dom.filter((r) => r.text.includes('第5学期'))
  check('C1 「第5学期」只出现在高等数学那一行（别的行没有）',
        sem5Rows.length === 1 && sem5Rows[0].text.includes('高等数学'),
        `含「第5学期」的行数=${sem5Rows.length}`)
  check('C2 「大学英语」那一行是「第4学期」+ 51，不是第5学期',
        !!engRow && engRow.text.includes('第4学期') && engRow.text.includes('51') && !engRow.text.includes('第5学期'),
        engRow ? engRow.text.slice(0, 120) : '没找到大学英语那一行')

  say('\n[D] 行数一致')
  check('D 表格行数 == 接口条数', dom.length === apiRows.length, `DOM ${dom.length} 行 vs 接口 ${apiRows.length} 条`)

  say('\n渲染出来的表格行：')
  dom.forEach((r, i) => say(`  ${i + 1}. ${r.text.slice(0, 150)}`))
  if (net4xx.length) say(`\n期间 4xx/5xx：${JSON.stringify([...new Set(net4xx)])}`)

  say('\n================ 汇总 ================')
  say(fails.length ? `失败 ${fails.length} 项：${fails.join(' / ')}` : '全部通过')
  return fails.length ? 1 : 0
}

main().then((c) => { cleanup(); process.exit(c) })
  .catch((e) => { cleanup(); console.error('探针异常:', e.message); process.exit(2) })
