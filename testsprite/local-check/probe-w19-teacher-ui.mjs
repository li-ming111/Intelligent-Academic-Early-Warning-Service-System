// W19 —— 教师端成绩页「导入」按钮的**界面级**判据。
//
// 为什么接口层验过了还要跑这个：接口层（probe-w19-score-import.sh）验的是
// "后端收到这个 multipart 能落库"，验不到**页面上点下去发出的到底是不是那个形状**。
// W19 的病根恰恰就在这一层：页面发 FormData、后端收 @RequestBody，两边从第一天起对不上。
// 所以这一轮的价值就在"页面自己发出的那个请求"。
//
// 走的是**临时 dev server 5273**（vite.config.w19.mjs）：它把 /api/teachers 直接指到
// 修后的备用实例 8195，其余路径照旧走网关 8076（登录还是真实链路）。
//
// 设计上的自保措施：
//   1. **零写库**：上传用的是学号不存在的文件（9999999999）—— 后端逐行走"学号不存在"跳过，
//      rows 为空 ⇒ 根本不调 saveScores。整条链路（页面→文件→后端解析→汇总）全跑通，
//      库里一行不留。跑完用 SQL 哨兵复核。
//   2. 先测**不选课程**：文件选完必须弹出明确错误且**一个请求都不发**（静默失败是另一类病）。
//
// 用法: node probe-w19-teacher-ui.mjs [用户名] [密码]
// 输出: probe-w19-teacher-ui.txt（UTF-8）
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

const USER = process.argv[2] || '10001'
const PASS = process.argv[3] || '123456'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.APP_BASE || 'http://localhost:5273'
const PORT = 9700 + Math.floor(Math.random() * 90)

const GHOST_FILE = resolve('_w19_ghost_student.xlsx')   // 学号 9999999999 —— 库里没有 ⇒ 零写库
const PROBE_FILE = GHOST_FILE

const lines = []
const say = (s) => { lines.push(s); console.log(s) }

const profile = mkdtempSync(join(tmpdir(), 'cdpw19-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const cleanup = () => {
  try { chrome.kill() } catch {}
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
}

const main = async () => {
  let target = null
  for (let i = 0; i < 80; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      target = list.find((t) => t.type === 'page')
      if (target) break
    } catch {}
    await sleep(250)
  }
  if (!target) { say('FAIL  Chrome 调试端口未就绪'); cleanup(); process.exit(1) }

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })

  let id = 0
  const pending = new Map()
  const captured = []          // 页面发出的 /scores/import 请求
  const responses = new Map()  // requestId -> { status, body }
  let send = null              // 在下面赋值；事件里要用，先占位
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Network.requestWillBeSent') {
      const r = m.params.request
      if (r.url.includes('/scores/import')) {
        const entry = {
          method: r.method,
          url: r.url,
          contentType: r.headers['Content-Type'] || r.headers['content-type'] || '',
          postData: r.postData || '',
        }
        captured.push(entry)
        // 带文件的 multipart 请求体 CDP 默认**不给**（request.postData 是空的，
        // 第一版探针因此把"没带 courseId"读成了假红）。要显式把请求体捞出来。
        send('Network.getRequestPostData', { requestId: m.params.requestId })
          .then((r2) => { entry.postData = r2?.result?.postData || '' })
          .catch(() => {})
      }
    }
    if (m.method === 'Network.responseReceived') {
      if (m.params.response.url.includes('/scores/import')) {
        responses.set(m.params.requestId, { status: m.params.response.status })
      }
    }
    if (m.method === 'Network.loadingFinished') {
      const rec = responses.get(m.params.requestId)
      if (rec && rec.status) {
        send('Network.getResponseBody', { requestId: m.params.requestId })
          .then((r2) => { rec.body = (r2?.result?.body || '').slice(0, 300) })
          .catch(() => {})
      }
    }
  }
  send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) throw new Error(r.result.exceptionDetails.text)
    return r.result?.result?.value
  }
  const waitFor = async (expr, timeoutMs = 40000) => {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      try { if (await evaluate(expr)) return true } catch {}
      await sleep(300)
    }
    return false
  }

  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable'); await send('DOM.enable')

  // ---------------------------------------------------------------- 登录
  await send('Page.navigate', { url: `${APP}/login` })
  if (!await waitFor(`!!document.querySelector('.lr-submit')`, 45000)) {
    say('FAIL  登录页未渲染'); cleanup(); process.exit(1)
  }
  const filled = await evaluate(`(async () => {
    const setVal = (sel, val) => {
      const el = document.querySelector(sel); if (!el) return false
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set
      setter.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    const a = setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(USER)})
    const b = setVal('input[placeholder="请输入密码"]', ${JSON.stringify(PASS)})
    const c = setVal('input[placeholder="验证码"]', (await (async () => {
      for (let i = 0; i < 60; i++) {
        const t = (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
        if (/^[A-Z0-9]{4}$/.test(t)) return t
        await new Promise((r) => setTimeout(r, 200))
      }
      return (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
    })()))
    const btn = document.querySelector('.lr-submit'); if (btn) btn.click()
    return { a, b, c, clicked: !!btn }
  })()`)
  if (!filled?.a || !filled?.clicked) { say(`FAIL  登录表单没填上 ${JSON.stringify(filled)}`); cleanup(); process.exit(1) }

  let path = ''
  for (let i = 0; i < 60; i++) { await sleep(500); path = await evaluate('location.pathname'); if (path && path !== '/login') break }
  say(`登录: user=${USER} -> ${path}`)
  if (path === '/login') { say('FAIL  没登进去（验证码或口令）'); cleanup(); process.exit(1) }

  // ---------------------------------------------------------------- 进成绩页
  await send('Page.navigate', { url: `${APP}/teacher/scores` })
  if (!await waitFor(`document.querySelectorAll('.el-select').length >= 3`, 40000)) {
    say('FAIL  成绩页没渲染出三个下拉（班级/学期/课程）'); cleanup(); process.exit(1)
  }
  await sleep(1200)

  // 页面结构判据
  const shape = await evaluate(`(() => {
    const inputs = [...document.querySelectorAll('.el-select input')].map(i => i.placeholder)
    const btns = [...document.querySelectorAll('button')].map(b => b.textContent.trim()).filter(Boolean)
    return { placeholders: inputs, hasTemplateBtn: btns.some(t => t.includes('下载模板')), hasUploadInput: !!document.querySelector('.el-upload__input'), buttons: btns.slice(0, 12) }
  })()`)
  say('')
  say('--- 1. 页面结构 ---')
  say(`  下拉的 placeholder: ${JSON.stringify(shape.placeholders)}`)
  say(`  「下载模板」按钮: ${shape.hasTemplateBtn ? '有' : '没有 !!!'}`)
  say(`  el-upload 的文件 input: ${shape.hasUploadInput ? '有' : '没有 !!!'}`)

  // ---------------------------------------------------------------- 选班级
  //
  // Element Plus 2.6 的 el-select：input 上**没有** placeholder 属性，占位文字在
  // `.el-select__placeholder` 里；每个 select 的下拉是 teleport 到 body 的 popper，
  // 靠 input 的 `aria-controls` 指过去（id 对得上）。所以按**序号**取，
  // 并用 aria-controls 精确拿属于它的那个下拉，不要去全局混着数。
  // 本页顺序（见 TeacherScores.vue 模板）：0=班级 1=学期 2=课程。
  const openSelect = async (idx) => {
    const ok = await evaluate(`(() => {
      const s = document.querySelectorAll('.el-select')[${idx}]
      if (!s) return false
      s.querySelector('.el-select__wrapper').dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
      return true
    })()`)
    await sleep(700)
    return ok
  }
  const readOptions = async (idx) => evaluate(`(() => {
    const s = document.querySelectorAll('.el-select')[${idx}]
    const inp = s && s.querySelector('input[aria-controls]')
    const box = inp && document.getElementById(inp.getAttribute('aria-controls'))
    if (!box) return null
    return [...box.querySelectorAll('.el-select-dropdown__item')].map(e => e.textContent.trim())
  })()`)
  const pickOption = async (idx, i = 0) => evaluate(`(() => {
    const s = document.querySelectorAll('.el-select')[${idx}]
    const inp = s && s.querySelector('input[aria-controls]')
    const box = inp && document.getElementById(inp.getAttribute('aria-controls'))
    if (!box) return null
    const items = [...box.querySelectorAll('.el-select-dropdown__item')]
    if (!items[${i}]) return null
    const t = items[${i}].textContent.trim()
    items[${i}].dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    items[${i}].click()
    return t
  })()`)

  const CLASS_SEL = 0, COURSE_SEL = 2

  await openSelect(CLASS_SEL)
  const classOpts = await readOptions(CLASS_SEL)
  const klass = await pickOption(CLASS_SEL, 0)
  await sleep(1800)
  say('')
  say('--- 2. 选中班级 ---')
  say(`  班级下拉的选项数 = ${classOpts ? classOpts.length : 'null'}，选了 = ${JSON.stringify(klass)}`)
  if (!klass) { say('FAIL  班级下拉没有可选项（这个教师没有班？换账号）'); cleanup(); process.exit(1) }

  // ---------------------------------------------------------------- 课程下拉有没有选项
  await openSelect(COURSE_SEL)
  const courseOpts = await readOptions(COURSE_SEL) || []
  say(`  课程下拉的选项: ${JSON.stringify(courseOpts)}`)
  say(`  => ${courseOpts.length ? '下拉渲染出了本人所授课程' : '!!! 下拉是空的'}`)

  // ---------------------------------------------------------------- 反例: 选了文件但没选课程
  const setFile = async (filePath) => {
    const doc = await send('DOM.getDocument', { depth: -1 })
    const q = await send('DOM.querySelector', { nodeId: doc.result.root.nodeId, selector: '.el-upload__input' })
    if (!q.result?.nodeId) return '没有找到 .el-upload__input'
    // 先清空再设置：同一个文件路径连设两次，Chrome 认为 input.value 没变、
    // 不触发 change，el-upload 的 on-change 就永远不会再跑（第一版探针就卡在这里，
    // 现场是"第二个请求一个都没发出去"，看着像页面坏了）。
    await send('DOM.setFileInputFiles', { nodeId: q.result.nodeId, files: [] })
    await sleep(150)
    await send('DOM.setFileInputFiles', { nodeId: q.result.nodeId, files: [filePath] })
    // 再显式补一发 change —— CDP 设完之后浏览器不保证一定派发（实测第一次派发、
    // 第二次不派发）。显式派发等价于用户真的选了这个文件，el-upload 读的就是
    // e.target.files。多派一次最多多发一个请求，而本探针的请求是零写库的。
    await evaluate(`(() => {
      const i = document.querySelector('.el-upload__input')
      if (!i) return false
      i.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    })()`)
    return 'ok'
  }

  const before = captured.length
  const setFileA = await setFile(PROBE_FILE)
  await sleep(2500)
  const toastNoCourse = await evaluate(`(() => {
    const msgs = [...document.querySelectorAll('.el-message')].map(e => e.textContent.trim())
    return msgs[msgs.length - 1] || ''
  })()`)
  say('')
  say('--- 3. 反例：选了文件但没选课程 ---')
  say(`  发出的 /scores/import 请求数 = ${captured.length - before}  (期望 0)`)
  say(`  置入文件 -> ${setFileA}`)
  say(`  页面提示 = ${JSON.stringify(toastNoCourse)}`)

  // ---------------------------------------------------------------- 选课程, 再选文件
  // 关掉可能残留的下拉
  await evaluate(`document.body.click()`)
  await sleep(400)
  await openSelect(COURSE_SEL)
  const course = await pickOption(COURSE_SEL, 0)
  await sleep(1200)
  say('')
  say('--- 4. 选课程后重新选文件 ---')
  say(`  课程 = ${JSON.stringify(course)}`)

  const before2 = captured.length
  const setFileB = await setFile(PROBE_FILE)
  say(`  置入文件 -> ${setFileB}`)
  let waited = 0
  while (captured.length === before2 && waited < 20000) { await sleep(500); waited += 500 }
  await sleep(1500)

  const toastImport = await evaluate(`(() => {
    const msgs = [...document.querySelectorAll('.el-message')].map(e => e.textContent.trim())
    return msgs[msgs.length - 1] || ''
  })()`)

  const last = captured[captured.length - 1]
  say('')
  say('--- 5. 页面**真正发出**的那个请求（这一层才是 W19 的判据）---')
  say(`  发出去的 /scores/import 请求数 = ${captured.length}`)
  if (!last) {
    say('  !!! 一个 /scores/import 请求都没发出去')
  } else {
    say(`  ${last.method} ${last.url}`)
    say(`  Content-Type: ${last.contentType}`)
    const isMultipart = /multipart\/form-data/i.test(last.contentType)
    say(`  => 是 multipart/form-data: ${isMultipart ? '是' : '否 !!! （旧病根就是这个）'}`)
    const parts = [...last.postData.matchAll(/name="([^"]+)"/g)].map((x) => x[1])
    say(`  请求体里的部件名 = ${JSON.stringify(parts)}`)
    const val = (n) => { const m = last.postData.match(new RegExp('name="' + n + '"[\\s\\S]{0,80}?\\r?\\n\\r?\\n([^\\r\\n]*)')); return m ? m[1] : null }
    say(`  file     = ${parts.includes('file') ? '有（文件名 ' + (last.postData.match(/filename="([^"]*)"/) || [])[1] + '）' : '没有 !!!'}`)
    say(`  courseId = ${JSON.stringify(val('courseId'))}`)
    say(`  className= ${JSON.stringify(val('className'))}`)
    const rec = [...responses.values()].pop()
    say(`  后端返回 HTTP ${rec ? rec.status : '?'}`)
    say(`  响应体 = ${JSON.stringify(rec && rec.body ? rec.body : '（没取到）')}`)
  }
  say(`  页面提示 = ${JSON.stringify(toastImport)}`)

  writeFileSync('probe-w19-teacher-ui.txt', lines.join('\n'), 'utf8')
  say('')
  say('=> 结果已写入 probe-w19-teacher-ui.txt')
  cleanup()
  process.exit(0)
}

main().catch((e) => { console.log(`FAIL  脚本异常: ${e.message}`); cleanup(); process.exit(1) })
