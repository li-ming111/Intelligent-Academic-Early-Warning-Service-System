// 管理员端"点击级"验证：用户管理 + 权限管理。
//
// 为什么单独写这条：这批 API 修复里，AdminUsers 的「添加用户」是**唯一没被点击验证过**的一处
// —— 之前只用 curl 打过接口。而它当初从 `/auth/register/*` 改成 `/users/add` 的**全部理由**
// 就是"重名时前端必须显示失败"，这条只有真的点一次才算验过：
//   `/auth/register/*` 业务失败时**返回错误字符串但仍包成 code:200 的成功响应**，
//   拦截器当成功放行 → 页面弹「用户已添加」，可库里什么都没建。
// 所以第 2 步断言的是"重名必须弹 **error** 且含「用户名已存在」"，这是有鉴别力的断言。
//
// 探针账号 ZZTEST911 在第 1 步建、第 3 步删；无论成败，脚本退出前都会兜底删掉它，
// 不留垃圾账号（库里账号数是能对上的数，35 个）。
//
// 用法: node admin-clicks.mjs [管理员账号] [密码]     退出码 0=全绿, 1=有失败
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const PROBE = 'ZZTEST911'          // 探针学号（2-50 字符，且不与 35 个账号重名）
const PROBE_NAME = '测试探针'
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const USER = process.argv[2] || 'admin'
const PASS = process.argv[3] || '123456'
const PORT = 9500 + Math.floor(Math.random() * 100)
const profile = mkdtempSync(join(tmpdir(), 'cdpa-'))
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
  let reqs = [], resps = [], jsErrors = []
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Network.requestWillBeSent') {
      const r = m.params.request
      if (r.url.includes('/api/')) reqs.push({ method: r.method, url: r.url.replace(/^https?:\/\/[^/]+/, '').split('?')[0] })
    } else if (m.method === 'Network.responseReceived') {
      const r = m.params.response
      if (r.url.includes('/api/')) resps.push({ status: r.status, url: r.url.replace(/^https?:\/\/[^/]+/, '').split('?')[0] })
    } else if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params.exceptionDetails
      jsErrors.push((d?.exception?.description || d?.text || '').split('\n')[0].slice(0, 160))
    }
  }
  const send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) return { __err: r.result.exceptionDetails.exception?.description }
    return r.result?.result?.value
  }
  const waitIdle = async (quietMs = 600, maxMs = 8000) => {
    let last = reqs.length + resps.length, quiet = 0
    for (let t = 0; t < maxMs; t += 200) {
      await sleep(200)
      const now = reqs.length + resps.length
      if (now === last) { quiet += 200; if (quiet >= quietMs) return } else { quiet = 0; last = now }
    }
  }
  // 抓 ElMessage（3s 自动消失，必须马上读）。
  // maxMs 可调：删除用户那条要等约 11 秒（见步骤 3 注释），2.5s 的默认窗口根本等不到。
  const grabMessage = async (maxMs = 2500) => {
    for (let t = 0; t < maxMs; t += 100) {
      await sleep(100)
      const msg = await evaluate(`(() => {
        const el = document.querySelector('.el-message')
        if (!el) return null
        const txt = el.querySelector('.el-message__content')?.textContent?.trim() || el.textContent.trim()
        return { txt, type: el.className.includes('el-message--error') ? 'error' : (el.className.includes('el-message--success') ? 'success' : 'other') }
      })()`)
      if (msg) return msg
    }
    return null
  }
  const clearMessages = () => evaluate(`(() => { document.querySelectorAll('.el-message').forEach((e) => e.remove()); return 1 })()`)
  // 页面内直接调接口（只用来兜底清理，不用于断言）
  const apiJson = async (path, opts = {}) => {
    const raw = await evaluate(`(async () => {
      const tok = localStorage.getItem('token')
      const r = await fetch(${JSON.stringify('/api')} + ${JSON.stringify(path)}, {
        method: ${JSON.stringify(opts.method || 'GET')},
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + tok },
        ${opts.body ? `body: ${JSON.stringify(JSON.stringify(opts.body))},` : ''}
      })
      return await r.text()
    })()`)
    try { return JSON.parse(raw) } catch { return null }
  }
  // 用 /users/list 找探针账号的 id —— 刻意不用 /users/by-username/{name}：
  // 那个接口返回原始 User 实体（**含 password 哈希**），我只想要 id，不该把哈希读进脚本/终端。
  const findProbe = async () => {
    const j = await apiJson('/users/list?page=1&size=200')
    const rows = j?.data?.data || []
    return rows.find((u) => u.username === PROBE)?.id || null
  }

  let bad = 0
  const report = (ok, title, lines) => {
    if (!ok) bad++
    console.log(`${ok ? '✓' : '✗'} ${title}`)
    for (const l of lines) console.log(`      ${l}`)
  }

  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable')

  // ---- 登录 ----
  await send('Page.navigate', { url: `${APP}/login` })
  await sleep(3000)
  const filled = await evaluate(`(() => {
    const setVal = (sel, val) => {
      const el = document.querySelector(sel); if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    const shown = (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
    const a = setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(USER)})
    const b = setVal('input[placeholder="请输入密码"]', ${JSON.stringify(PASS)})
    const c = setVal('input[placeholder="验证码"]', shown)
    const btn = document.querySelector('.lr-submit'); if (btn) btn.click()
    return { a, b, c, clicked: !!btn }
  })()`)
  if (!filled?.a || !filled?.b || !filled?.c || !filled?.clicked) throw new Error(`登录表单元素没找全: ${JSON.stringify(filled)}`)
  let path = ''
  for (let i = 0; i < 30; i++) { await sleep(500); path = await evaluate('location.pathname'); if (path && path !== '/login') break }
  if (!path || path === '/login') throw new Error(`登录失败（账号 ${USER}）`)
  console.log(`已登录 ${USER} → ${path}\n`)

  // 进用户管理页前，先把上次跑失败可能留下的探针删掉，否则第 1 步会因重名失败（那是脚本残留、不是产品问题）
  const stale = await findProbe()
  if (stale) { await apiJson(`/users/delete/${stale}`, { method: 'DELETE' }); console.log(`（清理上次残留的探针账号 id=${stale}）`) }

  reqs = []; resps = []; jsErrors = []
  await send('Page.navigate', { url: `${APP}/admin/users` })
  await sleep(2500)
  await waitIdle()

  // el-dialog 和 el-select 的下拉都是 teleport 到 body 的，**`offsetParent` 恒为 null**
  // （一开始拿 offsetParent 当"可见"判据，结果把下拉选项全过滤掉了，步骤 1-3 全挂）。
  // 改用**弹窗标题**定位，比"第一个可见的"稳：页面上同时存在 添加/编辑/删除/批量删除 四个弹窗。
  const DLG = (title) =>
    `[...document.querySelectorAll('.el-dialog')].find((d) => d.querySelector('.el-dialog__title')?.textContent.trim() === ${JSON.stringify(title)})`

  // 打开「添加用户」弹窗并填表。
  // 角色是 el-select，下拉挂在 body 上，且**顶部的筛选栏也有一个内容几乎相同的下拉**
  // （都有「学生」），所以不能按文本全局找选项 —— 必须顺着弹窗内 combobox 的
  // aria-controls 找到它自己的那个 listbox。
  const openAndFillAdd = async () => {
    const r = await evaluate(`(() => {
      const btn = [...document.querySelectorAll('button')].find((b) => b.textContent.trim() === '添加用户')
      if (!btn) return '找不到「添加用户」按钮'
      btn.click(); return 'ok'
    })()`)
    if (r !== 'ok') return r
    await sleep(700)
    const sel = await evaluate(`(() => {
      const d = ${DLG('添加用户')}
      if (!d) return '「添加用户」弹窗没出现'
      const s = d.querySelector('.el-select')
      if (!s) return '弹窗里没有角色下拉'
      s.click(); return 'ok'
    })()`)
    if (sel !== 'ok') return sel
    await sleep(800)
    const opt = await evaluate(`(() => {
      const d = ${DLG('添加用户')}
      const input = d?.querySelector('.el-select input[role="combobox"]')
      const listId = input?.getAttribute('aria-controls')
      if (!listId) return '角色下拉的 combobox 上没有 aria-controls'
      const list = document.getElementById(listId)
      if (!list) return 'aria-controls 指向的 listbox 不存在: ' + listId
      const items = [...list.querySelectorAll('.el-select-dropdown__item')]
      const o = items.find((e) => e.textContent.trim() === '学生')
      if (!o) return 'listbox 里没有「学生」：' + items.map((e) => e.textContent.trim()).join('/')
      o.click(); return 'ok'
    })()`)
    if (opt !== 'ok') return opt
    await sleep(500)
    const setVal = (sel2, val) => `(() => {
      const d = ${DLG('添加用户')}
      const el = d?.querySelector(${JSON.stringify(sel2)}); if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, ${JSON.stringify(val)})
      el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    })()`
    const a = await evaluate(setVal('input[placeholder="请输入学号"]', PROBE))
    const b = await evaluate(setVal('input[placeholder="请输入姓名"]', PROBE_NAME))
    if (!a || !b) return `填表失败 学号=${a} 姓名=${b}`
    return 'ok'
  }
  // 按**弹窗标题**里的按钮文本点。删除确认弹窗标题是「确认删除」，批量删除是「批量删除」——不会串。
  const clickDialogButton = (title, text) => evaluate(`(() => {
    const d = ${DLG(title)}
    if (!d) return '找不到标题为「' + ${JSON.stringify(title)} + '」的弹窗'
    const btns = [...d.querySelectorAll('.el-dialog__footer button')]
    const btn = btns.find((b) => b.textContent.trim() === ${JSON.stringify(text)})
    if (!btn) return '该弹窗按钮: ' + btns.map((b) => b.textContent.trim()).join('/')
    btn.click(); return 'ok'
  })()`)
  const tableHas = (text) => evaluate(`(() => {
    const t = [...document.querySelectorAll('.el-table')].filter((e) => e.offsetParent !== null).map((e) => e.innerText).join('\\n')
    return t.includes(${JSON.stringify(text)})
  })()`)

  // ================= 步骤 1：正常添加 =================
  {
    reqs = []; resps = []; jsErrors = []
    const r = await openAndFillAdd()
    if (r !== 'ok') { report(false, '步骤1 添加用户（正常路径）', [`打开/填表失败: ${r}`]); }
    else {
      await clearMessages()
      const c = await clickDialogButton('添加用户', '确认添加')
      let msg = null
      if (c === 'ok') { msg = await grabMessage(); await waitIdle(600, 5000) }
      const hit = reqs.find((x) => x.method === 'POST' && x.url.includes('/api/users/add'))
      const st = hit ? resps.filter((x) => x.url === hit.url).map((x) => x.status).join('/') : ''
      const okMsg = c === 'ok' && msg && msg.txt.includes('用户已添加') && msg.type === 'success'
      const inTable = await tableHas(PROBE)
      const ok = c === 'ok' && okMsg && !!hit && inTable
      report(ok, '步骤1 点「添加用户」→ 填学生 → 点「确认添加」', [
        `弹窗按钮  ${c === 'ok' ? '点到「确认添加」' : c}`,
        `提示      ${msg ? `${msg.type}: ${msg.txt}` : '(没有任何 ElMessage)'}${okMsg ? '' : '   ← 期望 success 且含「用户已添加」'}`,
        `请求      ${hit ? `POST ${hit.url} → ${st || '?'}` : '(没有发出 POST /api/users/add)'}`,
        `表格      ${inTable ? `已出现 ${PROBE}` : `列表里没有 ${PROBE}   ← loadUsers() 没刷新出来`}`,
      ])
    }
  }

  // ================= 步骤 2：重名必须弹失败（这一步才是关键） =================
  {
    reqs = []; resps = []; jsErrors = []
    const r = await openAndFillAdd()
    if (r !== 'ok') { report(false, '步骤2 重名添加（关键）', [`打开/填表失败: ${r}`]); }
    else {
      await clearMessages()
      const c = await clickDialogButton('添加用户', '确认添加')
      let msg = null
      if (c === 'ok') { msg = await grabMessage(); await waitIdle(600, 5000) }
      const hit = reqs.find((x) => x.method === 'POST' && x.url.includes('/api/users/add'))
      const okMsg = c === 'ok' && msg && msg.type === 'error' && msg.txt.includes('用户名已存在')
      const ok = okMsg && !!hit
      report(ok, `步骤2 再用同一学号 ${PROBE} 点「确认添加」（必须失败）`, [
        `提示      ${msg ? `${msg.type}: ${msg.txt}` : '(没有任何 ElMessage)'}${okMsg ? '' : '   ← 期望 error 且含「用户名已存在」'}`,
        `请求      ${hit ? `POST ${hit.url}（HTTP 200，但响应体 code≠200，拦截器 reject）` : '(没有发出请求)'}`,
        `说明      若这里弹的是 success「用户已添加」，说明走回了 /auth/register/* 那条路 —— 业务失败仍返回 code:200`,
      ])
    }
  }

  // ================= 步骤 3：点「删除」把探针删掉 =================
  //
  // ⚠️ 这一步要等**约 11 秒**，不是脚本卡了：前端「删除」打的是 admin-service 的
  // `DELETE /api/admin/users/{id}`，而该方法在删用户前先 Feign 调 message-service 删站内信
  // （`messageServiceClient.deleteMessagesByUserId`）。**message-service 没起时**这次调用
  // 一直等到超时才返回 —— 实测 10,785 ms。异常被 catch 住、删除照样成功，所以是"慢"不是"错"，
  // 但界面上按钮一直转圈，演示时会以为点死了。这里把等待窗口放到 20s 并打印耗时，
  // 让这份慢**在测试输出里可见**，而不是靠调大 timeout 把它藏掉。
  {
    // 步骤 2 重名失败后「添加用户」弹窗是**开着**的（失败不关窗），先关掉再点删除，
    // 否则等于在模态框背后点按钮 —— 那不是真实用户操作。
    const closed = await clickDialogButton('添加用户', '取消')
    if (closed !== 'ok') console.log(`   （提示：没能关掉残留的「添加用户」弹窗：${closed}）`)
    await sleep(500)

    reqs = []; resps = []; jsErrors = []
    const c0 = await evaluate(`(() => {
      const rows = [...document.querySelectorAll('.el-table__row')].filter((r) => r.offsetParent !== null)
      const row = rows.find((r) => r.innerText.includes(${JSON.stringify(PROBE)}))
      if (!row) return '表格里找不到探针那一行'
      const btn = [...row.querySelectorAll('button')].find((b) => b.textContent.trim() === '删除')
      if (!btn) return '该行没有「删除」按钮: ' + [...row.querySelectorAll('button')].map((b) => b.textContent.trim()).join('/')
      btn.click(); return 'ok'
    })()`)
    if (c0 !== 'ok') { report(false, '步骤3 删除探针账号', [c0]) }
    else {
      await sleep(700)
      await clearMessages()
      const t0 = Date.now()
      const c = await clickDialogButton('确认删除', '确认删除')
      let msg = null
      if (c === 'ok') { msg = await grabMessage(20000); await waitIdle(600, 20000) }
      const elapsed = Date.now() - t0
      // 前端走的是 admin-service 的 /admin/users/{id}，**不是** user-service 的 /users/delete/{id}
      const hit = reqs.find((x) => x.method === 'DELETE' && x.url.includes('/api/admin/users/'))
      const okMsg = c === 'ok' && msg && msg.type === 'success' && msg.txt.includes('用户已删除')
      const stillThere = await tableHas(PROBE)
      const ok = c === 'ok' && okMsg && !!hit && !stillThere
      report(ok, '步骤3 点该行「删除」→ 确认删除', [
        `弹窗按钮  ${c === 'ok' ? '点到「确认删除」' : c}`,
        `提示      ${msg ? `${msg.type}: ${msg.txt}` : '(没有任何 ElMessage)'}${okMsg ? '' : '   ← 期望 success 且含「用户已删除」'}`,
        `请求      ${hit ? `DELETE ${hit.url}` : '(没有发出 DELETE /api/admin/users/{id})'}`,
        `表格      ${stillThere ? `${PROBE} 仍在列表里` : '已消失'}`,
        `耗时      ${elapsed} ms${elapsed > 3000 ? '   ← 慢在 Feign 调 message-service(8077) 超时；起了 message-service 就快' : ''}`,
      ])
    }
  }

  // ================= 步骤 4：权限管理页「权限数」列（防回归我修的那处崩溃） =================
  {
    reqs = []; resps = []; jsErrors = []
    await send('Page.navigate', { url: `${APP}/admin/permissions` })
    await sleep(3500)
    await waitIdle()
    const cell = await evaluate(`(() => {
      const rows = [...document.querySelectorAll('.el-table__row')].filter((r) => r.offsetParent !== null)
      if (!rows.length) return { err: '角色表一行都没有' }
      const cells = rows[0].querySelectorAll('td')
      // 列序: 选择框 / 角色ID / 角色名称 / 角色描述 / 权限数 / 权限列表 / 操作
      return { first: rows[0].innerText.split('\\n').slice(0, 6), permCell: cells[4]?.innerText?.trim() }
    })()`)
    const ok = !cell?.err && cell?.permCell === '0' && jsErrors.length === 0
    report(ok, '步骤4 打开 /admin/permissions（角色表「权限数」列）', [
      `首行      ${cell?.err || JSON.stringify(cell.first)}`,
      `权限数格  ${JSON.stringify(cell?.permCell)}${cell?.permCell === '0' ? '' : '   ← 期望 "0"'}`,
      `未捕获异常 ${jsErrors.length ? [...new Set(jsErrors)].join(' | ') : '(无)'}`,
      `说明      角色接口返回的对象没有 permissions 字段；原来写 row.permissions.length 会整页崩，已用 (row.permissions || []).length 兜底`,
    ])
  }

  // ---- 兜底清理：无论成败，探针账号都必须走 ----
  const left = await findProbe()
  if (left) {
    await apiJson(`/users/delete/${left}`, { method: 'DELETE' })
    console.log(`\n（兜底清理：探针账号 id=${left} 已删除）`)
  } else {
    console.log(`\n（兜底清理：库里没有残留的探针账号 ✓）`)
  }

  console.log(`\n通过 ${4 - bad}/4`)
  return bad
}

let code = 1
try {
  const bad = await main()
  code = bad ? 1 : 0
  console.log(code === 0 ? '\nPASS  用户管理的增/重名/删与权限页渲染都符合预期' : `\nFAIL  ${bad} 个步骤失败`)
} catch (e) {
  console.log(`\nFAIL  脚本异常: ${e.message}`)
}
try { chrome.kill() } catch {}
try { rmSync(profile, { recursive: true, force: true }) } catch {}
process.exit(code)
