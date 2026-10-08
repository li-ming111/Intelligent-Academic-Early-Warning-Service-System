// W19 —— 管理员端「数据导出/导入」页里「上传导入」按钮的**界面级**判据。
//
// 同 probe-w19-teacher-ui.mjs 的道理：接口层验过"后端收 multipart 能落库"，
// 这一层验的是**页面上点下去发出的那个请求**。W19 的病根就在这一层 ——
// AdminDataExport.vue 原来把 el-upload 的 UploadFile 对象当 body 发出去
// （{name,url,status,raw}，不是文件内容），后端收 @RequestBody ⇒ 两边永远对不上。
//
// 走临时 dev server 5273：/api/admin 直连修后的 8170，其余走网关 8076。
//
// 自保：上传的是"学号不存在"的文件 ⇒ 后端逐行跳过，零写库。跑完用 SQL 哨兵复核。
//
// ⚠️ 本页有**两个** el-upload（导入学生 / 导入成绩），要取第 2 个（uploadScores）。
//
// 用法: node probe-w19-admin-ui.mjs [用户名] [密码]
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

const USER = process.argv[2] || 'admin'
const PASS = process.argv[3] || '123456'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.APP_BASE || 'http://localhost:5273'
const PORT = 9800 + Math.floor(Math.random() * 90)
const GHOST_FILE = resolve('_w19_ghost_student.xlsx')

const lines = []
const say = (s) => { lines.push(s); console.log(s) }

const profile = mkdtempSync(join(tmpdir(), 'cdpwa19-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const cleanup = () => { try { chrome.kill() } catch {} try { rmSync(profile, { recursive: true, force: true }) } catch {} }

const main = async () => {
  let target = null
  for (let i = 0; i < 80; i++) {
    try { target = (await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()).find((t) => t.type === 'page'); if (target) break } catch {}
    await sleep(250)
  }
  if (!target) { say('FAIL  Chrome 调试端口未就绪'); cleanup(); process.exit(1) }

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
  let id = 0
  const pending = new Map()
  const captured = []
  const responses = new Map()
  let send = null
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Network.requestWillBeSent') {
      const r = m.params.request
      if (r.url.includes('/import/scores')) {
        const entry = { method: r.method, url: r.url, contentType: r.headers['Content-Type'] || r.headers['content-type'] || '', postData: r.postData || '' }
        captured.push(entry)
        send('Network.getRequestPostData', { requestId: m.params.requestId })
          .then((r2) => { entry.postData = r2?.result?.postData || '' }).catch(() => {})
      }
    }
    if (m.method === 'Network.responseReceived' && m.params.response.url.includes('/import/scores')) {
      responses.set(m.params.requestId, { status: m.params.response.status })
    }
    if (m.method === 'Network.loadingFinished') {
      const rec = responses.get(m.params.requestId)
      if (rec && rec.status) send('Network.getResponseBody', { requestId: m.params.requestId })
        .then((r2) => { rec.body = (r2?.result?.body || '').slice(0, 300) }).catch(() => {})
    }
  }
  send = (method, params = {}) => new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) throw new Error(r.result.exceptionDetails.text)
    return r.result?.result?.value
  }
  const waitFor = async (expr, t = 40000) => { const d = Date.now() + t; while (Date.now() < d) { try { if (await evaluate(expr)) return true } catch {} ; await sleep(300) } return false }

  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable'); await send('DOM.enable')

  await send('Page.navigate', { url: `${APP}/login` })
  if (!await waitFor(`!!document.querySelector('.lr-submit')`, 45000)) { say('FAIL  登录页未渲染'); cleanup(); process.exit(1) }
  const filled = await evaluate(`(async () => {
    const setVal = (sel, val) => { const el = document.querySelector(sel); if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set.call(el, val)
      el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); return true }
    const a = setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(USER)})
    const b = setVal('input[placeholder="请输入密码"]', ${JSON.stringify(PASS)})
    setVal('input[placeholder="验证码"]', (await (async () => { for (let i=0;i<60;i++){ const t=(document.querySelector('.lr-captcha-btn')?.textContent||'').trim(); if(/^[A-Z0-9]{4}$/.test(t)) return t; await new Promise(r=>setTimeout(r,200)) } return '' })()))
    const btn = document.querySelector('.lr-submit'); if (btn) btn.click()
    return { a, b, clicked: !!btn }
  })()`)
  if (!filled?.a || !filled?.clicked) { say(`FAIL  登录表单没填上 ${JSON.stringify(filled)}`); cleanup(); process.exit(1) }

  let path = ''
  for (let i = 0; i < 60; i++) { await sleep(500); path = await evaluate('location.pathname'); if (path && path !== '/login') break }
  say(`登录: user=${USER} -> ${path}`)
  if (path === '/login') { say('FAIL  没登进去'); cleanup(); process.exit(1) }

  await send('Page.navigate', { url: `${APP}/admin/data-export` })
  if (!await waitFor(`document.querySelectorAll('.el-upload__input').length >= 2`, 40000)) {
    say('FAIL  数据导出页没渲染出两个上传控件'); cleanup(); process.exit(1)
  }
  await sleep(1500)

  const shape = await evaluate(`(() => {
    const ins = [...document.querySelectorAll('.el-upload__input')]
    const btns = [...document.querySelectorAll('button')].map(b => b.textContent.trim())
    return { inputs: ins.length, accepts: ins.map(i => i.accept), hasUploadBtn: btns.some(t => t === '上传导入'), btns: btns.filter(Boolean).slice(0, 16) }
  })()`)
  say('')
  say('--- 1. 页面结构 ---')
  say(`  el-upload 的文件 input 数 = ${shape.inputs}（第 1 个=导入学生，第 2 个=导入成绩）accept=${JSON.stringify(shape.accepts)}`)
  say(`  「上传导入」按钮: ${shape.hasUploadBtn ? '有' : '没有 !!!'}`)

  // 本页有**两张**「上传导入」卡：`.card-header` 分别是「导入学生数据」和「导入成绩数据」，
  // 两张都有个一模一样的 `<el-button>上传导入</el-button>`。第一版按 button 文本取的**第一个**
  // ⇒ 点到了导入学生（提示里出现"学生档案的姓名列不允许为空"，一看就不是成绩那条）。
  // 所以必须**按所在卡片认**，不能用序号也不能只用按钮文字。
  const SCORES_CARD = `[...document.querySelectorAll('.el-card')].find(c => (c.querySelector('.card-header')?.textContent || '').includes('导入成绩'))`

  const before = captured.length
  const setFile = async (filePath) => {
    const doc = await send('DOM.getDocument', { depth: -1 })
    const q = await send('DOM.querySelectorAll', { nodeId: doc.result.root.nodeId, selector: '.el-upload__input' })
    const all = q.result?.nodeIds || []
    if (!all.length) return '没找到任何 .el-upload__input'
    // 三个 input 都置入文件 —— 它们都是 :auto-upload="false"，不点按钮一个请求都不会发。
    // 这样就不必猜"哪个 input 配哪张卡"。真正决定归属的是下面**在哪张卡里派发 change**：
    // el-upload 的 on-change 收到之后才写进对应的 ref（handleScoreFileChange）。
    for (const nodeId of all) {
      await send('DOM.setFileInputFiles', { nodeId, files: [] })
      await sleep(120)
      await send('DOM.setFileInputFiles', { nodeId, files: [filePath] })
    }
    const where = await evaluate(`(() => {
      const card = ${SCORES_CARD}
      if (!card) return '没找到「导入成绩」卡片'
      const inputs = [...card.querySelectorAll('.el-upload__input')]
      inputs.forEach(i => i.dispatchEvent(new Event('change', { bubbles: true })))
      return '在成绩卡的 ' + inputs.length + ' 个 input 上派发了 change'
    })()`)
    return `ok (置入 ${all.length} 个 input；${where})`
  }

  say(`  置入成绩文件 -> ${await setFile(GHOST_FILE)}`)
  await sleep(1500)
  say(`  选文件阶段发出的 /import/scores 请求数 = ${captured.length - before}（期望 0：本页是"选完再点按钮"）`)

  const clicked = await evaluate(`(() => {
    const card = ${SCORES_CARD}
    const b = card && [...card.querySelectorAll('button')].find(x => x.textContent.trim() === '上传导入')
    if (!b) return '没找到成绩卡里的上传导入按钮'
    b.click(); return true
  })()`)
  say('')
  say('--- 2. 点「上传导入」 ---')
  say(`  按钮点到 = ${clicked}`)

  // 一边等请求，一边**持续**读提示 —— el-message 3 秒就没了，等完 20 秒再读是读不到的
  // （第一版就是这么把"请选择文件"读成了空串，看着像"点了没反应"）。
  const toasts = new Set()
  let waited = 0
  while (captured.length === before && waited < 20000) {
    const t = await evaluate(`(() => { const m = [...document.querySelectorAll('.el-message')].map(e => e.textContent.trim()); return m[m.length - 1] || '' })()`)
    if (t) toasts.add(t)
    await sleep(500); waited += 500
  }
  for (let i = 0; i < 8; i++) {
    const t = await evaluate(`(() => { const m = [...document.querySelectorAll('.el-message')].map(e => e.textContent.trim()); return m[m.length - 1] || '' })()`)
    if (t) toasts.add(t)
    await sleep(400)
  }
  const toast = [...toasts].join(' | ')

  const last = captured[captured.length - 1]
  say('')
  say('--- 3. 页面**真正发出**的那个请求（W19 的判据在这一层）---')
  say(`  发出去的 /import/scores 请求数 = ${captured.length - before}`)
  if (!last) {
    say('  !!! 一个请求都没发出去')
  } else {
    say(`  ${last.method} ${last.url}`)
    say(`  Content-Type: ${last.contentType}`)
    say(`  => 是 multipart/form-data: ${/multipart\/form-data/i.test(last.contentType) ? '是' : '否 !!! （旧病根就是这个）'}`)
    const parts = [...last.postData.matchAll(/name="([^"]+)"/g)].map((x) => x[1])
    say(`  请求体里的部件名 = ${JSON.stringify(parts)}`)
    say(`  file = ${parts.includes('file') ? '有（文件名 ' + (last.postData.match(/filename="([^"]*)"/) || [])[1] + '）' : '没有 !!!'}`)
    const rec = [...responses.values()].pop()
    say(`  后端返回 HTTP ${rec ? rec.status : '?'}`)
    say(`  响应体 = ${JSON.stringify(rec && rec.body ? rec.body : '（没取到）')}`)
  }
  say(`  页面提示 = ${JSON.stringify(toast)}`)

  writeFileSync('probe-w19-admin-ui.txt', lines.join('\n'), 'utf8')
  say('')
  say('=> 结果已写入 probe-w19-admin-ui.txt')
  cleanup(); process.exit(0)
}
main().catch((e) => { console.log(`FAIL  脚本异常: ${e.message}`); cleanup(); process.exit(1) })
