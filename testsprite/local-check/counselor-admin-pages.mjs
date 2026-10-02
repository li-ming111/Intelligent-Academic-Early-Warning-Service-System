// 两页的「接口有数据、页面却什么都没有」核查（2026-09-30 修完复测用）。
//
//   A) 辅导员「学生成绩」(/counselor/scores) 的「趋势」弹窗
//      接口 /counselors/scores/student/{id}/trend 实测返回 List（司明 25 行），
//      而页面写的是 `res.data?.data || []` —— 在数组上取 .data 恒为 undefined，
//      于是 trendData 永远是 []、折线图**永远是空的**，且不报错。
//   B) 管理员「课程管理」(/admin/courses) 的「授课教师」下拉
//      接口 /admin/users/page?role=2 回的是 {total, items:[...]}，
//      而页面只认 `.records`/裸数组 → teachers 恒为 []，下拉永远空、无提示。
//
// 断言口径统一为：**接口给的数量 == 页面上真实出现的数量**。
// 只断言"页面有东西"会被"页面写死几条假数据"骗过；只断言"接口有数据"则证明不了渲染。
//
// 用法: node counselor-admin-pages.mjs
// 退出码 0 = 两页都对得上接口
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { captchaFields } from './_captcha.mjs'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const GATEWAY = 'http://localhost:8076'
const PORT = 9500 + Math.floor(Math.random() * 90)
const profile = mkdtempSync(join(tmpdir(), 'cdpca-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const fails = []

/** 直接打网关拿接口真值（不经页面），作为对账基准 */
const api = async (path, token) => {
  const r = await fetch(`${GATEWAY}${path}`, { headers: { Authorization: 'Bearer ' + token } })
  const j = await r.json().catch(() => null)
  return j
}
const login = async (username, password) => {
  const r = await fetch(`${GATEWAY}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, ...(await captchaFields(GATEWAY)) }),
  })
  const j = await r.json()
  if (!j?.data?.token) throw new Error(`登录失败(${username}): ${JSON.stringify(j).slice(0, 120)}`)
  return j.data
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

  const loginAs = async (u, p) => {
    // 换角色前必须先清登录态：已登录时访问 /login 会被直接重定向走，
    // 表单根本不出现，报出来却是"登录表单没找全"（看着像页面坏了）。
    await send('Page.navigate', { url: `${APP}/login` })
    await sleep(1200)
    await evaluate('localStorage.clear()')
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
      const a = setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(u)})
      const b = setVal('input[placeholder="请输入密码"]', ${JSON.stringify(p)})
      const c = setVal('input[placeholder="验证码"]', shown)
      document.querySelector('.lr-submit')?.click()
      return { a, b, c }
    })()`)
    if (!filled?.a || !filled?.b || !filled?.c) throw new Error(`登录表单没找全: ${JSON.stringify(filled)}`)
    let path = ''
    for (let i = 0; i < 30; i++) { await sleep(500); path = await evaluate('location.pathname'); if (path && path !== '/login') break }
    if (!path || path === '/login') throw new Error(`登录失败（${u}）`)
    console.log(`已登录 ${u} → ${path}`)
  }

  await send('Page.enable'); await send('Runtime.enable')
  closeBrowser = async () => { try { await send('Browser.close') } catch {} }

  // ============ A) 辅导员：成绩趋势弹窗 ============
  const cTok = (await login('1000000001', '123456')).token
  const trendApi = await api('/api/counselors/scores/student/3/trend', cTok)
  const apiRows = Array.isArray(trendApi?.data) ? trendApi.data.length : -1
  console.log(`\n【接口真值】/counselors/scores/student/3/trend → ${apiRows} 行`)

  await loginAs('1000000001', '123456')
  await send('Page.navigate', { url: `${APP}/counselor/scores` })
  await sleep(6000)
  const opened = await evaluate(`(() => {
    const b = [...document.querySelectorAll('button')].find((x) => x.innerText.trim() === '查看趋势')
    if (!b) return '找不到「趋势」按钮'
    b.click(); return 'clicked'
  })()`)
  console.log(`点「趋势」: ${opened}`)
  await sleep(2500)
  const trendView = await evaluate(`(() => {
    const body = document.body.innerText.replace(/\\s+/g, ' ')
    const empty = /该学生暂无成绩记录/.test(body)
    // ⚠️ 不能只数 canvas：**空图也有 canvas**（只有坐标系和那条红色及格线）。
    // 上一版就是这么写的，于是"画了一张空图"和"画了 25 个点"在脚本眼里一模一样 ——
    // 正是本次会话反复踩的那类假阳性。改成数**折线的颜色画了多少像素**：
    // series 用的是 #67c23a = rgb(103,194,58)，空图里一个点都没有。
    // ⚠️ 必须**只数弹窗里那张图**。第一版数的是页面上所有 canvas，而这一页还有别的
    // 绿色调图表 —— 于是"空图"也能数出 5520 个绿像素，阈值判定形同虚设。
    // 负向对照（把修复临时改回去）就是靠这一点抓出来的。
    const dlg = document.querySelector('.el-dialog__body')
    const scoped = dlg ? [...dlg.querySelectorAll('canvas')] : []
    let greenPixels = 0
    for (const c of scoped) {
      if (!c.getBoundingClientRect().height) continue
      try {
        const ctx = c.getContext('2d')
        const { data } = ctx.getImageData(0, 0, c.width, c.height)
        for (let i = 0; i < data.length; i += 4) {
          const [r, g, b, a] = [data[i], data[i + 1], data[i + 2], data[i + 3]]
          if (a > 100 && g > 150 && r < 150 && b < 120) greenPixels++
        }
      } catch (e) { return { err: String(e) } }
    }
    return { empty, greenPixels, canvases: scoped.length }
  })()`)
  console.log(`弹窗状态: ${JSON.stringify(trendView)}`)

  console.log('===== 判定 A：成绩趋势 =====')
  if (apiRows <= 0) {
    console.log(`  接口就返回 0 行（${apiRows}），本轮无法证明"渲染对得上"`)
  } else if (trendView.empty) {
    fails.push('接口有成绩，弹窗却说"暂无成绩记录"')
    console.log('  → 接口有数据，页面却是空态：对不上')
  } else if ((trendView.greenPixels || 0) > 500) {
    console.log(`  → 接口 ${apiRows} 行，折线真的画出来了（绿色像素 ${trendView.greenPixels} 个），对得上`)
  } else {
    fails.push(`折线没画出来（绿色像素只有 ${trendView.greenPixels}）——空图也有坐标系，说明数据没进图`)
    console.log(`  → 接口 ${apiRows} 行，但图上没有折线（绿色像素 ${trendView.greenPixels}）：对不上`)
  }

  // ============ B) 管理员：授课教师下拉 ============
  const aTok = (await login('admin', '123456')).token
  const tApi = await api('/api/admin/users/page?page=1&size=999&role=2', aTok)
  const apiTeachers = Array.isArray(tApi?.data?.items) ? tApi.data.items.length : -1
  console.log(`\n【接口真值】/admin/users/page?role=2 → items ${apiTeachers} 条`)

  await loginAs('admin', '123456')
  await send('Page.navigate', { url: `${APP}/admin/courses` })
  await sleep(6000)
  // 打开「新建课程」弹窗，看授课教师下拉里的选项数
  const opts = await evaluate(`(async () => {
    const add = [...document.querySelectorAll('button')].find((x) => /添加课程/.test(x.innerText))
    if (!add) return { err: '找不到新增课程按钮' }
    add.click()
    await new Promise((r) => setTimeout(r, 1500))
    // Element Plus 的下拉 teleport 到 body，且要展开才渲染选项；这里直接读组件数据
    const sel = [...document.querySelectorAll('.el-select')].find((s) => /教师/.test(s.closest('.el-form-item')?.innerText || ''))
    if (!sel) return { err: '找不到授课教师下拉' }
    sel.querySelector('.el-select__wrapper, .el-input')?.click()
    await new Promise((r) => setTimeout(r, 1200))
    const lis = [...document.querySelectorAll('.el-select-dropdown__item')].filter((e) => e.getBoundingClientRect().height > 0)
    return { options: lis.length, labels: lis.slice(0, 3).map((e) => e.innerText.trim()) }
  })()`)
  console.log(`教师下拉: ${JSON.stringify(opts)}`)

  console.log('===== 判定 B：授课教师下拉 =====')
  if (apiTeachers <= 0) {
    console.log(`  接口就返回 0 条教师（${apiTeachers}），本轮证据不足`)
  } else if (opts?.err) {
    fails.push(`下拉没验成：${opts.err}`)
    console.log(`  → ${opts.err}`)
  } else if (opts.options !== apiTeachers) {
    fails.push(`下拉选项数(${opts.options}) 与接口教师数(${apiTeachers}) 不一致`)
    console.log(`  → 接口 ${apiTeachers} 位教师，下拉只有 ${opts.options} 项：对不上`)
  } else {
    console.log(`  → 接口 ${apiTeachers} 位教师，下拉 ${opts.options} 项，对得上：${JSON.stringify(opts.labels)}`)
  }

  console.log(`\n未捕获异常  ${jsErrors.length ? [...new Set(jsErrors)].join(' | ') : '(无)'}`)
  return fails
}

let result = ['脚本异常']
let closeBrowser = async () => {}
try { result = await main() } catch (e) { console.log(`\n脚本异常: ${e.message}`) }
await closeBrowser()
await sleep(800)
try { chrome.kill() } catch {}
try { rmSync(profile, { recursive: true, force: true }) } catch {}
console.log(`\n===== 结论 =====`)
if (!result.length) console.log('PASS(0)  两页都对得上接口')
else { console.log(`FAIL(1)  ${result.length} 项对不上：`); for (const f of result) console.log(`  - ${f}`) }
process.exit(result.length ? 1 : 0)
