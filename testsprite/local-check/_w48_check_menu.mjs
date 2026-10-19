// W48 辅助探针：确认 teacher/student 的「我的」页真的渲染出了新加的菜单项。
//
// 为什么要单开一支：xcx-page-smoke.mjs 每页只 dump **前 26 行**正文，
// 而「常用功能」菜单在教师「我的」页里位于卡片区之后 —— 26 行截断正好切在
// 它前面。于是"菜单没渲染"与"菜单渲染了但 dump 截掉了"在冒烟报告里长得一样
// （W48 踩到）。这一支直接对 DOM 做**存在性**查询，不受截断影响。
//
// 用法: node _w48_check_menu.mjs <role>   # role ∈ teacher|student
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://127.0.0.1:5180'

const role = process.argv[2]
const CFG = {
  teacher: { user: '10001', pass: '123456', hash: '/pages/teacher/profile',
             want: ['常用功能', '我的课表', '课程管理', '班级管理', '班级分析', '学分预测', '反馈管理'] },
  student: { user: '2023020616', pass: '123456', hash: '/pages/student/profile',
             want: ['常用功能', '我的课表', '成绩查询', '预警记录'] }
}[role]
if (!CFG) { console.error('role 必须是 teacher|student'); process.exit(2) }

const PORT = 9700 + Math.floor(Math.random() * 200)
const profile = mkdtempSync(join(tmpdir(), 'cdxm-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank'
], { stdio: 'ignore' })

const sleep = ms => new Promise(r => setTimeout(r, ms))
const cleanup = () => { try { chrome.kill() } catch {} try { rmSync(profile, { recursive: true, force: true }) } catch {} }

const main = async () => {
  let target = null
  for (let i = 0; i < 80; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      target = list.find(t => t.type === 'page'); if (target) break
    } catch {}
    await sleep(250)
  }
  if (!target) { console.log('FAIL 调试端口未就绪'); cleanup(); process.exit(1) }

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
  let id = 0
  const pending = new Map()
  ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id) } }
  const send = (method, params = {}) => new Promise(res => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async expr => (await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })).result?.result?.value

  await send('Page.enable'); await send('Runtime.enable')
  await send('Page.navigate', { url: APP })

  const waitFor = async (expr, t = 45000) => {
    const d = Date.now() + t
    while (Date.now() < d) { try { if (await evaluate(expr)) return true } catch {} await sleep(400) }
    return false
  }

  // 登录（判据与 xcx-page-smoke 同：token 非空 + hash 落在该角色）
  await waitFor(`document.querySelectorAll('uni-input').length >= 3 && (document.querySelector('.captcha-code')?.textContent || '').trim().length > 0`)
  await evaluate(`(() => {
    const byPh = ph => { const w = Array.from(document.querySelectorAll('uni-input')).find(u => (u.querySelector('.uni-input-placeholder')?.textContent || '').trim() === ph); return w ? w.querySelector('input') : null }
    const setVal = (el, v) => { if (!el) return false; Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set.call(el, v); el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); return true }
    setVal(byPh('请输入学号或工号'), ${JSON.stringify(CFG.user)})
    setVal(byPh('请输入密码'), ${JSON.stringify(CFG.pass)})
    setVal(byPh('输入验证码'), (document.querySelector('.captcha-code')?.textContent || '').trim())
    document.querySelector('.btn')?.click(); return true
  })()`)
  const landed = await waitFor(`(localStorage.getItem('token')?1:0)===1 && (location.hash||'').includes('/pages/${role}/')`, 30000)
  if (!landed) { console.log(`FAIL 登录未落地（role=${role}）`); cleanup(); process.exit(1) }

  await evaluate(`location.hash = ${JSON.stringify('#' + CFG.hash)}`)
  await waitFor(`document.body.innerText.includes('常用功能')`, 20000)
  await sleep(2500)

  // 只在菜单容器内查（不是页面里任意处出现同名文字）—— 免得被别处的文案蒙混
  const inMenu = await evaluate(`(() => {
    const heads = Array.from(document.querySelectorAll('*')).filter(e => e.children.length === 0 && (e.textContent||'').trim() === '常用功能')
    if (!heads.length) return null
    let card = heads[0]
    for (let i = 0; i < 4 && card.parentElement; i++) card = card.parentElement
    return (card.innerText || '')
  })()`)
  const menuText = String(inMenu || '')
  console.log(`role=${role} 菜单容器内容判据 ${inMenu === null ? '**未找到「常用功能」块**' : '已定位'}`)
  let fail = 0
  for (const w of CFG.want) {
    const hit = menuText.includes(w)
    console.log(`  ${hit ? '✔' : '✘'} 菜单里${hit ? '有' : '缺'}「${w}」`)
    if (!hit) fail++
  }
  // 负向对照：菜单里不该出现底栏 tab 的重复入口（页面顶部 hero 区有"我的班级"等，但菜单块内不该有）
  const negative = ['退出登录']
  for (const n of negative) {
    const hit = menuText.includes(n)
    console.log(`  ${hit ? '✘' : '✔'} 负向对照：菜单块内${hit ? '不该有却出现了' : '没有'}「${n}」`)
    if (hit) fail++
  }
  console.log(fail ? `==== ${role} 菜单检查：${fail} 项不通过 ====` : `==== ${role} 菜单检查：全过 ====`)
  cleanup(); process.exit(fail ? 1 : 0)
}
main().catch(e => { console.log(`FAIL ${e.message}`); cleanup(); process.exit(1) })
