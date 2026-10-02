// 管理员「数据备份」页**删除流程**的点击级验证（2026-10-02，W1.10）。
//
// 为什么单写一条：这一页的删除有三个入口（行内「删除」、勾选后的「批量删除」、
// 以及导出历史那侧的同类逻辑），而它们共用同一个 `confirmDelete`。
// 原实现用 `deleteCount === 1` 来判断"这是单条删除" —— 但**只勾一行再点批量删除时
// 计数也是 1**，于是会走进单条分支去读一个从未被设置过的 id：
//   · 备份那条：弹「没有拿到要删除的备份名」，文件一个没删；
//   · 导出那条更糟：跳过删除却照旧弹「成功删除 1 条」—— 典型的"假成功"。
// 这类缺陷**接口/库/控制台全正常，只有 DOM 能抓到**，所以必须是点击级断言。
//
// ⚠️⚠️ 安全设计（第一版探针在这里翻过车，务必保留）：
//   本页有四个 tab，而 el-tabs **默认把所有 tab-pane 都渲染进 DOM**，
//   `document.querySelectorAll('.el-table')` 会同时抓到「数据导出」那张表 ——
//   它上面也有一个叫「删除」的按钮，点下去走的是 `DELETE /admin/export/{id}`，
//   **删的是 export_history 里的真行**。第一版探针就是这么误删了 2 行演示数据的。
//   所以这里加了三道锁，缺一不可：
//     ① 切 tab 用 `div.el-tabs__item`（tab 头不是 <button>，按按钮找永远找不到）；
//     ② 每次删除前 `assertOnBackupTab()`：活动 tab 必须是「数据备份」，且
//        末张表的表头必须含「备份名称」；
//     ③ 每次删除前 `assertRowsAreBackups()`：要删的那几行名字必须匹配
//        `backup_*.sql`。**不匹配就直接抛，绝不点下去。**
//   收尾还会比对 export_history 的行数快照，一旦变化立刻报红。
//
// 用法: node probe-backup-delete-flow.mjs [管理员账号] [密码]   退出码 0=全绿
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const USER = process.argv[2] || 'admin'
const PASS = process.argv[3] || '123456'
const PORT = 9500 + Math.floor(Math.random() * 100)
const profile = mkdtempSync(join(tmpdir(), 'cdpdel-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
let bad = 0
const report = (ok, title, lines = []) => {
  if (!ok) bad++
  console.log(`${ok ? '✓' : '✗'} ${title}`)
  for (const l of lines) console.log(`      ${l}`)
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
  const clearMessages = () => evaluate(`(() => { document.querySelectorAll('.el-message').forEach((e) => e.remove()); return 1 })()`)
  const grabMessage = async (maxMs = 3000) => {
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
  // 锁 ①：tab 头是 div.el-tabs__item，**不是 <button>**
  const clickTab = (label) => evaluate(`(() => {
    const items = [...document.querySelectorAll('.el-tabs__item')]
    const t = items.find(x => x.textContent.trim() === ${JSON.stringify(label)})
    if (!t) return 'NOT_FOUND'
    t.click(); return 'CLICKED'
  })()`)
  const activeTab = () => evaluate(`(() => {
    const t = document.querySelector('.el-tabs__item.is-active')
    return t ? t.textContent.trim() : null
  })()`)
  const clickByText = (text) => evaluate(`(() => {
    const btns = [...document.querySelectorAll('button')].filter(b => b.offsetParent !== null)
    const b = btns.find(x => x.textContent.trim() === ${JSON.stringify(text)})
           || btns.find(x => x.textContent.includes(${JSON.stringify(text)}))
    if (!b) return 'NOT_FOUND'
    b.click(); return 'CLICKED'
  })()`)
  // 备份表**按表头找**，不按位置找 —— 实测"最后一个 .el-table"抓到的是
  // 「数据导出」那张表（它有 文件名/记录数/导出时间 这些列），位置假设是错的。
  // 按「备份名称」这个只在备份表出现的表头定位，才不会被 tab-pane 的渲染顺序坑到。
  const lastTable = `(() => {
    const ts = [...document.querySelectorAll('.el-table')]
    return ts.find(t => [...t.querySelectorAll('.el-table__header-wrapper th .cell')]
      .some(c => c.innerText.trim() === '备份名称')) || null
  })()`
  const backupHeaders = () => evaluate(`(() => {
    const t = ${lastTable}
    if (!t) return []
    return [...t.querySelectorAll('.el-table__header-wrapper th .cell')].map(c => c.innerText.trim()).filter(Boolean)
  })()`)
  const backupRows = () => evaluate(`(() => {
    const t = ${lastTable}
    if (!t) return []
    return [...t.querySelectorAll('.el-table__body-wrapper tbody tr')].map(tr =>
      [...tr.querySelectorAll('td')].map(td => td.innerText.trim()))
  })()`)
  const backupNames = async () => (await backupRows()).map(r => r[2] || r[1]).filter(Boolean)

  // 锁 ②：活动 tab 必须是「数据备份」，且页面上有该 tab 独有的「立即备份数据」按钮。
  //   ⚠️ 不能拿"表头里有备份名称"当判据：备份数为 0 时这张表**根本不渲染**（走的是
  //   「暂无备份记录」那个分支），第一次进页面就会误判。
  const assertOnBackupTab = async (where) => {
    const tab = await activeTab()
    const hasBackupButton = await evaluate(`[...document.querySelectorAll('button')]
      .some(b => b.offsetParent !== null && b.textContent.includes('立即备份数据'))`)
    const ok = tab === '数据备份' && hasBackupButton
    if (!ok) throw new Error(`[${where}] 不在「数据备份」tab 上（活动 tab=${tab}，有「立即备份数据」按钮=${hasBackupButton}）—— 拒绝继续，避免误删别的表的数据`)
    return ok
  }
  // 锁 ③：要删的行名必须都是 backup_*.sql
  const assertRowsAreBackups = async (names, where) => {
    const badNames = names.filter(n => !/^backup_.*\.sql$/.test(n || ''))
    if (names.length === 0 || badNames.length) {
      throw new Error(`[${where}] 待删行里出现了非备份文件名 ${JSON.stringify(badNames)} —— 拒绝点击`)
    }
    return true
  }
  const clickRowDelete = async (idx = 0, where = '') => {
    await assertOnBackupTab(where)
    const rows = await backupRows()
    const name = rows[idx]?.[2] || rows[idx]?.[1]
    await assertRowsAreBackups([name], where)
    return evaluate(`(() => {
      const t = ${lastTable}
      const rows = [...t.querySelectorAll('.el-table__body-wrapper tbody tr')]
      const tr = rows[${idx}]
      if (!tr) return 'NO_ROW'
      const btns = [...tr.querySelectorAll('button')].filter(b => b.offsetParent !== null)
      const b = btns.find(x => x.textContent.trim() === '删除')
      if (!b) return 'NOT_FOUND'
      b.click(); return 'CLICKED'
    })()`)
  }
  const checkRow = (idx) => evaluate(`(() => {
    const t = ${lastTable}
    if (!t) return 'NO_TABLE'
    const rows = [...t.querySelectorAll('.el-table__body-wrapper tbody tr')]
    const tr = rows[${idx}]
    if (!tr) return 'NO_ROW'
    const cb = tr.querySelector('td .el-checkbox')
    if (!cb) return 'NO_CHECKBOX'
    cb.click(); return 'CHECKED'
  })()`)
  const waitFor = async (expr, maxMs = 8000, step = 200) => {
    for (let t = 0; t < maxMs; t += step) { if (await evaluate(expr)) return true; await sleep(step) }
    return false
  }
  const apiJson = async (path) => {
    const raw = await evaluate(`(async () => {
      const tok = localStorage.getItem('token')
      const r = await fetch('/api' + ${JSON.stringify(path)}, { headers: { Authorization: 'Bearer ' + tok } })
      return await r.text()
    })()`)
    try { return JSON.parse(raw) } catch { return null }
  }
  const diskBackups = async () => ((await apiJson('/admin/backups/list'))?.data?.items || []).map(i => i.name)
  // 快照：export_history 的行数。**本探针不许动它**，动了就报红。
  const exportRowCount = async () => {
    const j = await apiJson('/admin/export/history?page=1&size=1')
    return j?.data?.total ?? null
  }

  await send('Page.enable'); await send('Runtime.enable')

  // ---- 登录 ----
  await send('Page.navigate', { url: `${APP}/login` })
  await sleep(3000)
  await evaluate(`(() => {
    const setVal = (sel, val) => {
      const el = document.querySelector(sel); if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    const shown = (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
    setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(USER)})
    setVal('input[placeholder="请输入密码"]', ${JSON.stringify(PASS)})
    setVal('input[placeholder="验证码"]', shown)
    return 1
  })()`)
  await clickByText('登 录')
  if (!await waitFor(`location.pathname.includes('/admin')`, 10000)) {
    report(false, '登录', [`路径 ${await evaluate('location.pathname')}`]); return
  }

  await send('Page.navigate', { url: `${APP}/admin/data-export` })
  await sleep(2500)
  const tabClick = await clickTab('数据备份')
  await sleep(900)
  await assertOnBackupTab('进入页面')
  report(tabClick === 'CLICKED' && (await activeTab()) === '数据备份',
    '0. 切到「数据备份」tab（tab 头是 div 不是 button，第一版就是栽在这）',
    [`活动 tab = ${await activeTab()}`, `末张表表头 = ${JSON.stringify(await backupHeaders())}`])

  const baseline = await diskBackups()
  const exportBaseline = await exportRowCount()
  console.log(`baseline: 磁盘 ${baseline.length} 份备份 / export_history ${exportBaseline} 行`)

  // ---- 1. 造一份备份 ----
  await clearMessages()
  const backupClick = await clickByText('立即备份数据')
  const msg1 = await grabMessage(8000)
  await sleep(1500)
  const afterOne = await diskBackups()
  const first = afterOne.find(n => !baseline.includes(n))
  report(afterOne.length === baseline.length + 1 && !!first,
    '1. 点「立即备份数据」真的在磁盘上多出一份 .sql',
    [`点击=${backupClick} 消息「${msg1?.txt}」(${msg1?.type})`,
     `磁盘 ${baseline.length} -> ${afterOne.length}`, `新文件 ${first}`])

  // ---- 2. 行内「删除」走单条分支 ----
  await clearMessages()
  const clicked2 = await clickRowDelete(0, '步骤2')
  await sleep(500)
  await clickByText('确认删除')
  const msg2 = await grabMessage(5000)
  await sleep(900)
  const afterSingle = await diskBackups()
  report(clicked2 === 'CLICKED' && afterSingle.length === baseline.length && !afterSingle.includes(first),
    '2. 行内「删除」真删掉了文件（单条分支）',
    [`消息「${msg2?.txt}」(${msg2?.type})`, `磁盘 ${afterOne.length} -> ${afterSingle.length}`])

  // ---- 3. 再造两份 ----
  await clearMessages()
  await clickByText('立即备份数据'); await waitFor(`document.querySelector('.el-message') !== null`, 4000); await sleep(1500)
  await clickByText('立即备份数据'); await waitFor(`document.querySelector('.el-message') !== null`, 4000); await sleep(1500)
  const beforeBatch = await diskBackups()
  report(beforeBatch.length === baseline.length + 2,
    '3. 再造两份备份', [`磁盘 ${afterSingle.length} -> ${beforeBatch.length}`])

  // ---- 4. ⭐ 只勾一行 → 批量删除（回归的就是这一步） ----
  await assertOnBackupTab('步骤4')
  const checked = await checkRow(0)
  await sleep(400)
  const selectedNames = await evaluate(`(() => {
    const t = ${lastTable}
    return [...t.querySelectorAll('.el-table__body-wrapper tbody tr')]
      .filter(tr => tr.querySelector('.el-checkbox.is-checked'))
      .map(tr => [...tr.querySelectorAll('td')].map(td => td.innerText.trim())[2] || '')
  })()`)
  await assertRowsAreBackups(selectedNames, '步骤4')
  const shownSelection = await evaluate(`(() => {
    const el = [...document.querySelectorAll('.selection-info')].pop()
    return el ? el.innerText.replace(/\\s+/g,' ').trim() : null
  })()`)
  await clearMessages()
  const batchClick = await clickByText('批量删除')
  await sleep(600)
  const dialogText = await evaluate(`(() => {
    const d = [...document.querySelectorAll('.el-dialog')].filter(e => e.offsetParent !== null).pop()
    return d ? d.innerText.replace(/\\s+/g,' ').trim().slice(0, 90) : null
  })()`)
  await clickByText('确认删除')
  const msg4 = await grabMessage(6000)
  await sleep(1200)
  const afterBatch = await diskBackups()

  report(batchClick === 'CLICKED' && checked === 'CHECKED' && selectedNames.length === 1,
    '4a. 只勾一行时「批量删除」按钮确实出现且可点',
    [`勾选=${checked} 勾中的行=${JSON.stringify(selectedNames)}`, `点击=${batchClick} 提示「${shownSelection}」`, `弹窗「${dialogText}」`])
  report(afterBatch.length === beforeBatch.length - 1 && !afterBatch.includes(selectedNames[0]),
    '4b. ⭐ 只勾一行 + 批量删除 → 真的删掉那一份（不是报错、也不是假成功）',
    [`消息「${msg4?.txt}」(${msg4?.type})`, `磁盘 ${beforeBatch.length} -> ${afterBatch.length}`, `被删的是 ${selectedNames[0]}`])
  report(msg4?.type === 'success' && /已删除 1 个备份文件/.test(msg4?.txt || ''),
    '4c. 成功提示与"真删了几份"一致',
    [`期望「已删除 1 个备份文件」，实际「${msg4?.txt}」`])

  // ---- 5. 收尾 ----
  let guard = 0
  while ((await diskBackups()).length > baseline.length && guard++ < 10) {
    await clearMessages()
    await clickRowDelete(0, '收尾')
    await sleep(400)
    await clickByText('确认删除')
    await grabMessage(4000)
    await sleep(800)
  }
  const finalList = await diskBackups()
  report(finalList.length === baseline.length,
    '5. 收尾后磁盘回到基线（净变化 0）',
    [`磁盘 ${afterBatch.length} -> ${finalList.length}（基线 ${baseline.length}）`])

  // ---- 6. 安全网：本探针**不许**动 export_history ----
  const exportAfter = await exportRowCount()
  report(exportAfter === exportBaseline,
    '6. ⚠️ export_history 行数没变（本探针只许碰备份文件）',
    [`${exportBaseline} -> ${exportAfter}`])

  const realErrors = jsErrors.filter(e => !/ResizeObserver|favicon/i.test(e))
  report(realErrors.length === 0, '7. 全程无 JS 异常', realErrors.slice(0, 3))
}

main()
  .catch((e) => { console.error('探针异常:', e.message); bad++ })
  .finally(async () => {
    try { chrome.kill() } catch {}
    await sleep(300)
    try { rmSync(profile, { recursive: true, force: true }) } catch {}
    console.log(`\nFAILURES: ${bad}`)
    process.exit(bad ? 1 : 0)
  })
