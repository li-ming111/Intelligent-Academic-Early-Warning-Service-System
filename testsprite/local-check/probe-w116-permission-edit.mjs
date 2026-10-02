// 管理员「权限配置」页的「编辑权限」弹窗 —— 点击级验证（2026-10-02 W1.16）。
//
// 为什么必须点一遍：这个按钮此前是
//     ElMessage.info('编辑功能开发中...')
// —— **点了有反馈、弹窗不开、库里不动**。改成真弹窗 + `PUT /admin/permissions/{id}`
// 之后，"编译过 / 静态检查过"完全说明不了问题（[[abs-data-right-control-wrong]] 同型：
// 接口、库、控制台全正常，只有 DOM 能抓到）。
//
// 有鉴别力的断言（不是"页面没报错"）：
//   ① 弹窗真的出现（旧实现下 `document.querySelector('.el-dialog')` 里根本没有「编辑权限」这个标题）；
//   ② 权限码输入框是 **disabled** 且显示的是这一行的码；
//   ③ 保存后**表格里那一行的名称真的变了** —— 这一条同时证明
//      "后端写成功" + "前端 `loadPermissions()` 重拉了"，只改本地数组的话表格不会变；
//   ④ 库里读回（脚本退出后用 bash 查，见调用方）。
//
// 探针权限行由调用方先用 SQL 插好（`W116_PROBE_CODE`），脚本只改它、不碰 9 条真实权限。
//
// 用法: node probe-w116-permission-edit.mjs <权限码>
import { spawn } from 'node:child_process'
import { mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CODE = process.argv[2] || 'W116_PROBE_CODE'
const NEW_NAME = 'W116_UI_RENAMED'
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const USER = process.argv[3] || 'admin'
const PASS = process.argv[4] || '123456'
const PORT = 9500 + Math.floor(Math.random() * 100)
const profile = mkdtempSync(join(tmpdir(), 'cdp-'))
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
  const jsErrors = []
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Runtime.exceptionThrown') {
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
  const grabMessage = async (maxMs = 3000) => {
    for (let t = 0; t < maxMs; t += 100) {
      await sleep(100)
      const msg = await evaluate(`(() => {
        const el = document.querySelector('.el-message')
        if (!el) return null
        return { txt: (el.querySelector('.el-message__content')?.textContent || el.textContent).trim(),
                 type: el.className.includes('el-message--error') ? 'error'
                     : (el.className.includes('el-message--success') ? 'success' : 'other') }
      })()`)
      if (msg) return msg
    }
    return null
  }

  let bad = 0
  const report = (ok, title, lines = []) => {
    if (!ok) bad++
    console.log(`${ok ? '✓' : '✗'} ${title}`)
    for (const l of lines) console.log(`      ${l}`)
  }

  await send('Page.enable'); await send('Runtime.enable')

  // ---- 登录 ----
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
  if (!filled?.a || !filled?.b || !filled?.c || !filled?.clicked) throw new Error(`登录表单元素没找全: ${JSON.stringify(filled)}`)
  let path = ''
  for (let i = 0; i < 30; i++) { await sleep(500); path = await evaluate('location.pathname'); if (path && path !== '/login') break }
  if (!path || path === '/login') throw new Error(`登录失败（账号 ${USER}）`)
  console.log(`已登录 ${USER} → ${path}\n`)

  await send('Page.navigate', { url: `${APP}/admin/permissions` })
  await sleep(2500)

  // ---- 切到「权限配置」tab ----
  const tab = await evaluate(`(() => {
    const t = [...document.querySelectorAll('.el-tabs__item')].find((e) => e.textContent.trim() === '权限配置')
    if (!t) return '找不到「权限配置」tab'
    t.click(); return 'ok'
  })()`)
  if (tab !== 'ok') throw new Error(tab)
  await sleep(1500)

  // ---- 找到探针那一行，点它的「编辑」 ----
  // 用**行内是否含这个权限码**定位，而不是"第几行"——行序会漂。
  const clicked = await evaluate(`(() => {
    const rows = [...document.querySelectorAll('.el-table__row')]
    const row = rows.find((r) => r.textContent.includes(${JSON.stringify(CODE)}))
    if (!row) return '表格里找不到权限码 ' + ${JSON.stringify(CODE)} + ' 的行（共 ' + rows.length + ' 行）'
    const btn = [...row.querySelectorAll('button')].find((b) => b.textContent.trim().includes('编辑'))
    if (!btn) return '那一行没有「编辑」按钮'
    btn.click(); return 'ok'
  })()`)
  report(clicked === 'ok', '① 点「编辑」', [clicked === 'ok' ? '找到探针行并点击' : clicked])
  await sleep(1000)

  // ---- 断言弹窗内容 ----
  const dlg = await evaluate(`(() => {
    const d = [...document.querySelectorAll('.el-dialog')]
      .find((x) => x.querySelector('.el-dialog__title')?.textContent.trim() === '编辑权限')
    if (!d) return { found: false, titles: [...document.querySelectorAll('.el-dialog__title')].map((t) => t.textContent.trim()) }
    const item = (label) => [...d.querySelectorAll('.el-form-item')]
      .find((f) => f.querySelector('.el-form-item__label')?.textContent.trim() === label)
    const codeInput = item('权限码')?.querySelector('input')
    const nameInput = item('权限名称')?.querySelector('input')
    const descInput = item('权限描述')?.querySelector('textarea')
    return {
      found: true,
      codeValue: codeInput?.value ?? null,
      codeDisabled: codeInput ? (codeInput.disabled || codeInput.classList.contains('is-disabled')) : null,
      nameValue: nameInput?.value ?? null,
      hasDesc: !!descInput
    }
  })()`)
  report(dlg?.found === true, '② 「编辑权限」弹窗出现（旧实现下不会出现）',
    dlg?.found ? [] : [`页面上现有弹窗标题：${JSON.stringify(dlg?.titles || [])}`])
  report(dlg?.codeValue === CODE && dlg?.codeDisabled === true, '③ 权限码显示为这一行的码且被禁用',
    [`值=${dlg?.codeValue}  禁用=${dlg?.codeDisabled}`])
  report(typeof dlg?.nameValue === 'string' && dlg.nameValue.length > 0, '④ 权限名称已预填',
    [`名称=${dlg?.nameValue}`])
  report(dlg?.hasDesc === true, '⑤ 权限描述是 textarea（留空即清除）', [])

  if (!dlg?.found) { console.log('\n弹窗没开，后面步骤无意义'); ws.close(); chrome.kill(); process.exit(1) }

  // ---- 改名 + 清空描述，保存 ----
  const saved = await evaluate(`(() => {
    const d = [...document.querySelectorAll('.el-dialog')]
      .find((x) => x.querySelector('.el-dialog__title')?.textContent.trim() === '编辑权限')
    const item = (label) => [...d.querySelectorAll('.el-form-item')]
      .find((f) => f.querySelector('.el-form-item__label')?.textContent.trim() === label)
    const setNative = (el, val) => {
      const proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype
      Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true }))
    }
    const nameInput = item('权限名称').querySelector('input')
    setNative(nameInput, ${JSON.stringify(NEW_NAME)})
    const descInput = item('权限描述').querySelector('textarea')
    setNative(descInput, '')
    const btn = [...d.querySelectorAll('.el-dialog__footer button')].find((b) => b.textContent.trim() === '保存')
    if (!btn) return '找不到「保存」按钮'
    btn.click(); return 'ok'
  })()`)
  report(saved === 'ok', '⑥ 点「保存」', [saved === 'ok' ? '' : String(saved)])

  const msg = await grabMessage()
  report(msg?.type === 'success', '⑦ 弹出 success 提示',
    [`实际：${msg ? msg.type + ' / ' + msg.txt : '（3 秒内没抓到任何 ElMessage）'}`])

  // ---- 表格那一行必须真的变了（证明后端写成功 + 前端重拉） ----
  await sleep(1200)
  const tableName = await evaluate(`(() => {
    const rows = [...document.querySelectorAll('.el-table__row')]
    const row = rows.find((r) => r.textContent.includes(${JSON.stringify(CODE)}))
    if (!row) return null
    const cells = [...row.querySelectorAll('td')].map((c) => c.textContent.trim())
    return { rowText: row.textContent.replace(/\\s+/g, ' ').trim().slice(0, 120), cells }
  })()`)
  report(tableName?.rowText?.includes(NEW_NAME) === true,
    '⑧ 表格里那一行的名称真的变了（旧实现下这行纹丝不动）',
    [`该行文本：${tableName?.rowText ?? '（行没了）'}`])

  report(jsErrors.length === 0, '⑨ 无未捕获 JS 异常', jsErrors.slice(0, 3))

  console.log(`\n${bad === 0 ? '全绿' : bad + ' 项失败'}`)
  ws.close(); chrome.kill()
  process.exit(bad === 0 ? 0 : 1)
}

main().catch((e) => { console.error('探针异常:', e.message); chrome.kill(); process.exit(1) })
