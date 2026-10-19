// W48 定点探针：三张**新加的**课表/排课页渲染的是不是**库里真那几行**。
//
// 为什么要单开一支：smoke-pages.mjs 的判据是「无未捕获异常 + 无 5xx + 正文≥30 字」，
// 一个渲染成「暂无排课记录」空态的新页**照样全绿** —— 它证明的是"页面没崩"，
// 不是"页面有数据"。这三张页本轮才接上路由，正是最可能"路由通、数据空"的地方。
//
// 判据取「只有真数据才可能出现的教室/课程名」字面量，并配**跨租户负向对照**：
// 教师只该看到自己的排课、学生只该看到自己选了的课 —— 别处的行出现了就是错的。
//
// 地面真值（2026-10-18 查活库，见对话记录）：
//   course_plans 10 行，学期全是 2025-2026-2。
//   teacher_id: 29(于凤海)×3 = plan1/4/10、19(王老师)=plan2、22(李老师)=plan3/5、
//               18(刘教授)=plan6、28(赵振江)=plan7/8/9
//   教室：plan1 A301 / plan2 A302 / plan3 A303 / plan4 实训楼B101 / plan5 实训楼B102 /
//        plan6 A304 / plan7 A401 / plan8 A402 / plan9 艺术楼C201 / plan10 A403
//   学生 3（司明 student_profile.id=3）选了 course_id 1..6 ⇒ 对应 plan 1..6，
//        教室恰为 A301/A302/A303/实训楼B101/实训楼B102/A304（**不含**艺术楼C201/A401/A402/A403）
//
// 用法: node probe-w48-timetable.mjs [student|teacher|admin]
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'

const ONLY = process.argv[2]

const CASES = {
  student: {
    user: '2023020616', pass: '123456', hash: '#/student/timetable',
    // 司明选了 plan1..6：这三条教室分别只出现在他被选中的课里
    want: ['教学楼A302', '实训楼B101', '高等数学'],
    // 没选的课（plan7/8/9/10）的教室绝不能出现在他的课表上
    forbid: ['艺术楼C201', '教学楼A401', '人工智能导论'],
  },
  teacher: {
    // teacher001 = users.id 18 = 刘教授 ⇒ 活库里恰好只有 plan6 一条
    user: 'teacher001', pass: '123456', hash: '#/teacher/timetable',
    want: ['教学楼A304', '操作系统'],
    // 于凤海(plan1)与赵振江(plan9)的排课不该出现在刘教授页面上
    forbid: ['教学楼A301', '艺术楼C201'],
  },
  // ⭐ 同一个页面、同一份代码、只换登录身份 —— 这是「按身份过滤」这条闸的**打靶**：
  //    于凤海(users.id=29) 有 plan1/4/10，刘教授(18) 只有 plan6。
  //    真过滤 ⇒ 两人看到的教室必然不同；若闸坏了（谁登都看全量），
  //    两边的 want/forbid 会同时成立 ⇒ 至少一边报红。负向对照因此是承重的。
  teacher2: {
    user: '10001', pass: '123456', hash: '#/teacher/timetable',
    want: ['教学楼A301', '实训楼B101'],
    forbid: ['教学楼A304', '艺术楼C201'],
  },
  admin: {
    user: 'admin', pass: '123456', hash: '#/admin/course-plans',
    // 管理员看全量：总条数 + 只有真数据才有的教室/课程
    want: ['共 10 条', '艺术楼C201', '人工智能导论', '教学楼A403'],
    forbid: [],
  },
}

const roles = ONLY ? [ONLY] : Object.keys(CASES)
for (const r of roles) if (!CASES[r]) { console.error(`未知角色 ${r}`); process.exit(2) }

const sleep = ms => new Promise(r => setTimeout(r, ms))

async function run(role) {
  const C = CASES[role]
  const PORT = 9800 + Math.floor(Math.random() * 150)
  const profile = mkdtempSync(join(tmpdir(), 'cdpw48-'))
  const chrome = spawn(CHROME, [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
  ], { stdio: 'ignore' })
  const cleanup = () => { try { chrome.kill() } catch {} try { rmSync(profile, { recursive: true, force: true }) } catch {} }

  let target = null
  for (let i = 0; i < 80; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      target = list.find(t => t.type === 'page'); if (target) break
    } catch {}
    await sleep(250)
  }
  if (!target) { cleanup(); throw new Error('Chrome 调试端口未就绪') }

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
  let id = 0
  const pending = new Map()
  ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id) } }
  const send = (method, params = {}) => new Promise(res => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async expr => (await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })).result?.result?.value

  await send('Page.enable'); await send('Runtime.enable')
  await send('Page.navigate', { url: `${APP}/login` })

  const waitFor = async (expr, t = 45000) => {
    const d = Date.now() + t
    while (Date.now() < d) { try { if (await evaluate(expr)) return true } catch {} await sleep(400) }
    return false
  }

  // 验证码是服务端签发的，必须等它变成 4 位码再读（同 smoke-pages.mjs 的理由）
  await waitFor(`/^[A-Z0-9]{4}$/.test((document.querySelector('.lr-captcha-btn')?.textContent||'').trim())`)
  await evaluate(`(() => {
    const setVal = (sel, val) => { const el = document.querySelector(sel); if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set.call(el, val)
      el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); return true }
    const cap = (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
    setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(C.user)})
    setVal('input[placeholder="请输入密码"]', ${JSON.stringify(C.pass)})
    setVal('input[placeholder="验证码"]', cap)
    document.querySelector('.lr-submit')?.click(); return true
  })()`)
  const landed = await waitFor(`location.pathname !== '/login' && !!localStorage.getItem('token')`, 30000)
  if (!landed) { cleanup(); throw new Error(`登录未落地（${C.user}）`) }

  await send('Page.navigate', { url: `${APP}/${C.hash.replace(/^#\//, '')}` })
  // 等表格/空态真的落地，别用固定 sleep 把"慢"读成"空"
  await waitFor(`document.body.innerText.includes('课表') || document.body.innerText.includes('排课')`, 20000)
  await sleep(3000)

  const body = (await evaluate(`document.body.innerText`)) || ''
  const norm = String(body).replace(/\s+/g, ' ')
  cleanup()

  let fail = 0
  console.log(`\n--- ${role}  ${C.hash}  (正文 ${norm.length} 字) ---`)
  for (const w of C.want) {
    const hit = norm.includes(w)
    console.log(`  ${hit ? '✔' : '✘'} 正向：正文里${hit ? '有' : '缺'}「${w}」`)
    if (!hit) fail++
  }
  for (const f of C.forbid) {
    const hit = norm.includes(f)
    console.log(`  ${hit ? '✘' : '✔'} 负向：${hit ? '不该出现却出现了' : '没有出现'}「${f}」`)
    if (hit) fail++
  }
  return fail
}

let total = 0
for (const r of roles) {
  try { total += await run(r) }
  catch (e) { console.log(`\n--- ${r} FAIL ${e.message}`); total++ }
}
console.log(total ? `\n==== W48 课表页：${total} 项不通过 ====` : `\n==== W48 课表页：全过 ====`)
process.exit(total ? 1 : 0)
