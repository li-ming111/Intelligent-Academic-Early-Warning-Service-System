// 「上一批 6 页 + admin 后端」的实机核对（2026-09-30）。
//
// 背景：commit 86de89f 那一批只过了 `mvn compile` 与 `vite build`，**没在浏览器里点过**。
// 那批的性质全是「接口有数据、页面却是空的」——正因如此，只编译通过完全证明不了什么：
// 死分支不报错、控制台干净、页面只是永远空态。所以这次逐页用真浏览器看渲染。
//
// 断言口径（本目录统一）：**接口真值 == 页面真实渲染出的东西**。
// 每页都先自己用同一套参数打一遍接口当基准，再去 DOM 里数数；两边对不上就是没修好。
// 只断言"页面有东西"会被写死的假数据骗过；只断言"接口有数据"则证明不了渲染。
//
// 登录方式：直接用 /api/auth/login 拿 token，再按 Login.vue 写过的同一组 key 注入
// localStorage（token/userId/username/name/role），跳过验证码。注入前先 localStorage.clear()，
// 这样"页面首屏读到的东西"里一定没有上一轮的残留。
//
// 用法: node earlier-batch-pages.mjs     （需要 vite:5173 + 网关:8076 + 8070/8074/8079/8090）
// 只读，不写库。退出码 0 = 每一页都与接口对得上。
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { captchaFields } from './_captcha.mjs'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const GATEWAY = 'http://localhost:8076'
const PORT = 9500 + Math.floor(Math.random() * 90)
const profile = mkdtempSync(join(tmpdir(), 'cdpbatch-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const fails = []
let closeBrowser = async () => { try { chrome.kill() } catch {} }

// ---------- 接口侧 ----------
const loginApi = async (username, password = '123456') => {
  const r = await fetch(`${GATEWAY}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, ...(await captchaFields(GATEWAY)) }),
  })
  const j = await r.json()
  if (!j?.data?.token) throw new Error(`登录失败(${username}): ${JSON.stringify(j).slice(0, 160)}`)
  return j.data
}
const api = async (path, token) => {
  const r = await fetch(`${GATEWAY}${path}`, { headers: { Authorization: 'Bearer ' + token } })
  return r.json().catch(() => null)
}

const check = (label, actual, expected) => {
  const ok = String(actual) === String(expected)
  if (!ok) fails.push(`${label}: 页面=${JSON.stringify(actual)} 接口=${JSON.stringify(expected)}`)
  console.log(`  ${ok ? '✓' : '✗'} ${label}: 页面 ${JSON.stringify(actual)} / 接口 ${JSON.stringify(expected)}`)
  return ok
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
  let jsErrors = []
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params.exceptionDetails
      jsErrors.push((d?.exception?.description || d?.text || '').split('\n')[0].slice(0, 200))
    }
  }
  const send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) return { __err: r.result.exceptionDetails.exception?.description }
    return r.result?.result?.value
  }

  await send('Page.enable'); await send('Runtime.enable')
  closeBrowser = async () => { try { await send('Browser.close') } catch {} }

  // 注入式登录：keys 与 Login.vue:194-200 完全一致
  const uiLogin = async (username) => {
    const d = await loginApi(username)
    await send('Page.navigate', { url: `${APP}/login` })
    await sleep(800)
    const r = await evaluate(`(() => {
      localStorage.clear()
      localStorage.setItem('token', ${JSON.stringify(d.token)})
      localStorage.setItem('userId', String(${JSON.stringify(String(d.userId))}))
      localStorage.setItem('username', ${JSON.stringify(username)})
      localStorage.setItem('name', ${JSON.stringify(d.name || username)})
      localStorage.setItem('role', String(${JSON.stringify(String(d.role))}))
      return { userId: localStorage.getItem('userId'), role: localStorage.getItem('role') }
    })()`)
    return { ...d, injected: r }
  }
  const nav = async (path, waitMs = 5000) => {
    jsErrors = []
    await send('Page.navigate', { url: `${APP}${path}` })
    await sleep(waitMs)
    return path
  }

  // ============ 1) 辅导员侧边栏姓名（CounselorLayout.vue） ============
  console.log('\n===== 1) 辅导员侧边栏姓名 /counselor/dashboard =====')
  {
    const d = await uiLogin('1000000001')
    const dash = await api(`/api/counselors/dashboard/${d.userId}`, d.token)
    const apiName = dash?.data?.counselor?.name
    const apiStudents = dash?.data?.studentCount
    console.log(`  接口: counselor.name=${JSON.stringify(apiName)}  studentCount=${apiStudents}`)
    // 关键：清掉 userName 后再进页面 —— 首屏兜底是「辅导员」，
    // 页面上出现真名就只可能是 loadCounselorInfo() 那次接口调用写的。
    await evaluate(`(() => { localStorage.removeItem('userName'); return localStorage.getItem('userName') })()`)
    await nav('/counselor/dashboard', 6000)
    const v = await evaluate(`(() => {
      const el = document.querySelector('.user-name')
      return { text: el ? el.innerText.trim() : null, body: document.body.innerText.replace(/\\s+/g,' ').slice(0,120) }
    })()`)
    console.log(`  页面: .user-name=${JSON.stringify(v?.text)}`)
    if (!apiName) fails.push('接口没给出 counselor.name，基准取不到')
    else {
      check('侧边栏姓名 == 接口 counselor.name', v?.text, apiName)
      if (v?.text === '辅导员') fails.push('侧边栏显示的是兜底文案「辅导员」—— 接口那条路径没生效')
    }
    const ls = await evaluate(`localStorage.getItem('userName')`)
    check('接口调用后写入 localStorage.userName', ls, apiName)
  }

  // ============ 2) 学生端未读角标（StudentLayout.vue / Messages.vue） ============
  console.log('\n===== 2) 学生未读角标 /student/dashboard + /student/messages =====')
  {
    const d = await uiLogin('2023020616')
    const un = await api(`/api/students/messages/${d.userId}/unread-count`, d.token)
    const unread = un?.data?.unreadCount
    console.log(`  接口: unreadCount=${unread}`)
    await nav('/student/dashboard', 6000)
    const v = await evaluate(`(() => {
      const b = document.querySelector('.notify-badge .el-badge__content')
      return { hasBadge: !!document.querySelector('.notify-badge'), text: b ? b.innerText.trim() : null }
    })()`)
    console.log(`  页面(布局角标): ${JSON.stringify(v)}`)
    check('布局角标数字 == 接口 unreadCount', v?.text, unread)
    if (!unread) fails.push('接口 unreadCount 为 0/空，本页断言失去意义（这条用例需要学生有未读）')

    await nav('/student/messages', 6000)
    const m = await evaluate(`(() => {
      const b = document.querySelector('.mgh-badge')
      return { text: b ? b.innerText.trim() : null, rows: document.querySelectorAll('.el-table__body tbody tr').length }
    })()`)
    console.log(`  页面(消息页): ${JSON.stringify(m)}`)
    check('消息页「N条未读」== 接口 unreadCount', m?.text, `${unread}条未读`)
  }

  // ============ 3) 教师待办（TeacherDashboard.vue） ============
  console.log('\n===== 3) 教师待办 /teacher/dashboard （账号 10001 / users.id 29） =====')
  {
    const d = await uiLogin('10001')
    const uid = d.userId
    const dash = await api(`/api/teachers/dashboard/${uid}`, d.token)
    const warnRes = await api(`/api/teachers/warnings/${uid}`, d.token)
    const todoRes = await api(`/api/teachers/todos/${uid}`, d.token)
    const wlist = Array.isArray(warnRes?.data) ? warnRes.data : []
    const tlist = Array.isArray(todoRes?.data) ? todoRes.data : []
    const pending = wlist.filter((w) => w.status === '待处理' || w.status === 0).length
    const hasSevere = wlist.some((w) => w.warningLevel === '严重')
    const fb = Number(tlist.find((t) => t?.type === 'feedback')?.count) || 0
    const cr = Number(tlist.find((t) => t?.type === 'class_request')?.count) || 0
    const expectItems = (pending > 0 ? 1 : 0) + (hasSevere ? 1 : 0) + (fb > 0 ? 1 : 0) + (cr > 0 ? 1 : 0)
    console.log(`  接口: 预警 ${wlist.length} 条(待处理 ${pending} / 有严重 ${hasSevere})  todos.feedback=${fb} todos.class_request=${cr}   ⇒ 期望待办 ${expectItems} 项`)
    console.log(`  接口原话: teacherName=${JSON.stringify(dash?.data?.teacherName)}  todos=${JSON.stringify(tlist)}`)
    await nav('/teacher/dashboard', 8000)
    const v = await evaluate(`(() => {
      const items = [...document.querySelectorAll('.tdct-item')].map(el => ({
        title: el.querySelector('.tdct-title')?.innerText.trim() || '',
        desc: el.querySelector('.tdct-desc')?.innerText.trim() || '',
      }))
      // ⚠️ .tdc-badge 在页面上有**两个**（班级数卡写「N个」，待办卡写「N项」），
      // querySelector 取到的是第一个 —— 上一版脚本就是这么把自己骗过的。
      // 必须按标题锚定到「待办事项」那一张卡再取角标。
      const head = [...document.querySelectorAll('.tdc-head')].find(h => h.innerText.includes('待办事项'))
      return {
        items,
        badge: head?.querySelector('.tdc-badge')?.innerText.trim() || null,
        allBadges: [...document.querySelectorAll('.tdc-badge')].map(e => e.innerText.trim()),
        hasNone: !!document.querySelector('.tdc-none'),
        greeting: document.querySelector('.tdh-greeting')?.innerText.trim() || null,
      }
    })()`)
    console.log(`  页面所有 .tdc-badge: ${JSON.stringify(v?.allBadges)}（待办卡那个才是基准）`)
    console.log(`  页面: badge=${JSON.stringify(v?.badge)}  空态=${v?.hasNone}  问候=${JSON.stringify(v?.greeting)}`)
    console.log(`  页面待办: ${JSON.stringify(v?.items)}`)
    check('待办条数 == 按接口口径算出的条数', v?.items?.length, expectItems)
    check('角标「N项」== 渲染出的条数', v?.badge, v?.items?.length ? `${v.items.length}项` : null)
    if (fb > 0) {
      const hit = v?.items?.find((i) => i.title === '学生反馈待回复')
      check('「学生反馈待回复」出现且描述带接口给的条数', hit?.desc, `${fb}条反馈等待回复`)
    }
    if (pending > 0) {
      const hit = v?.items?.find((i) => i.title === '预警待处理')
      check('「预警待处理」出现且描述带接口给的条数', hit?.desc, `${pending}条预警需要您处理`)
    }
    if (showGreeting(dash)) check('问候语带接口 teacherName', (v?.greeting || '').includes(dash.data.teacherName) ? dash.data.teacherName : v?.greeting, dash.data.teacherName)
  }

  // ============ 4) 管理员权限页用户列表（AdminPermissions.vue） ============
  console.log('\n===== 4) 管理员用户列表 /admin/permissions =====')
  {
    const d = await uiLogin('admin')
    const page = await api(`/api/admin/users/page?page=1&size=20`, d.token)
    const total = page?.data?.total
    const items = Array.isArray(page?.data?.items) ? page.data.items.length : -1
    console.log(`  接口: /admin/users/page?page=1&size=20 → total=${total} items=${items}`)
    await nav('/admin/permissions', 7000)
    const v = await evaluate(`(() => {
      // ⚠️ 这页有**两张** el-table（角色表 + 用户表），页签切换并不销毁 DOM，
      // 直接数 document 下所有行会把角色表也算进来。按表头文字锚定到用户表。
      const tables = [...document.querySelectorAll('.el-table')]
      const userTable = tables.find(t => t.innerText.includes('用户名'))
      const roleTable = tables.find(t => t.innerText.includes('角色名称'))
      const rowsOf = (t) => t ? t.querySelectorAll('.el-table__body tbody tr').length : null
      return {
        countText: document.querySelector('.user-count')?.innerText.trim() || null,
        userRows: rowsOf(userTable),
        roleRows: rowsOf(roleTable),
        allRows: document.querySelectorAll('.el-table__body tbody tr').length,
      }
    })()`)
    console.log(`  页面: ${JSON.stringify(v)}`)
    check('「共 N 个用户」== 接口 total', v?.countText, `共 ${total} 个用户`)
    check('用户表行数 == 接口本页 items 条数（首次加载 size=20）', v?.userRows, items === -1 ? 'n/a' : Math.min(items, total))
  }

  // ============ 5) 管理员数据备份页（AdminDataExport.vue） ============
  console.log('\n===== 5) 备份列表 /admin/data-export → 「数据备份」页签 =====')
  {
    const d = await uiLogin('admin')
    const bk = await api(`/api/admin/backups/list`, d.token)
    const note = bk?.data?.note
    const bkItems = Array.isArray(bk?.data?.items) ? bk.data.items.length : -1
    console.log(`  接口: implemented=${bk?.data?.implemented} total=${bk?.data?.total} items=${bkItems}`)
    console.log(`  接口原话 note=${JSON.stringify(note)}`)
    await nav('/admin/data-export', 6000)
    // 切到「数据备份」那个 el-tab
    const clicked = await evaluate(`(() => {
      const t = [...document.querySelectorAll('.el-tabs__item')].find(e => e.innerText.trim() === '数据备份')
      if (!t) return false
      t.click(); return true
    })()`)
    if (!clicked) { fails.push('找不到「数据备份」页签'); console.log('  ✗ 找不到页签') }
    await sleep(5000)
    const v = await evaluate(`(() => {
      // ⚠️ el-tab-pane 切换时**不销毁**其它页签的 DOM，整页正文里还有「数据导出」那 10 行。
      // 必须把范围收进「备份操作」那个 pane，否则会把别的页签的行数当成备份列表。
      const panes = [...document.querySelectorAll('.el-tab-pane')]
      const pane = panes.find(p => p.innerText.includes('备份操作'))
      const paneText = pane ? pane.innerText.replace(/\\s+/g, ' ') : ''
      return {
        paneFound: !!pane,
        hasEmpty: /暂无备份记录/.test(paneText),
        rows: pane ? pane.querySelectorAll('.el-table__body tbody tr').length : null,
        paneText: paneText.slice(0, 500),
      }
    })()`)
    console.log(`  页面(仅「备份操作」pane): 空态=${v?.hasEmpty} 行数=${v?.rows} paneFound=${v?.paneFound}`)
    if (!v?.paneFound) fails.push('找不到「备份操作」页签内容容器 —— 断言范围无法界定')
    if (bkItems === 0) {
      if (v?.hasEmpty && v?.rows === 0) console.log('  ✓ 接口 0 条 / 页面走空态（说明这个探针能读出"空"）')
      else { fails.push(`接口 0 条，页面却没走空态: ${JSON.stringify({ hasEmpty: v?.hasEmpty, rows: v?.rows })}`); console.log('  ✗ 对不上') }
    }
    // 后端如实说了"未实现"，页面必须把这句话原样透出（不能只写"暂无备份记录"让人以为点了就有）
    if (note) {
      const shown = (v?.paneText || '').includes(note)
      if (!shown) fails.push('后端 note 没有原样显示在页面上')
      console.log(`  ${shown ? '✓' : '✗'} 后端 note 原样显示在页面`)
    }
    // 反面：页面是否还在宣称"系统自动每日备份"
    const claimsAuto = /系统自动每日备份/.test(v?.paneText || '')
    console.log(`  ⚠️ 页面是否仍宣称「系统自动每日备份」: ${claimsAuto ? '是（与后端 implemented=false 直接冲突）' : '否'}`)
    if (claimsAuto) fails.push('页面宣称「系统自动每日备份，备份时间为凌晨02:00」，而后端明说没有运行时备份机制')
  }

  console.log(`\nJS 异常: ${jsErrors.length ? JSON.stringify(jsErrors) : '无'}`)
  if (jsErrors.length) fails.push(`页面有 JS 异常: ${jsErrors[0]}`)
  console.log(fails.length ? `\n结果: FAIL (${fails.length})\n - ${fails.join('\n - ')}` : '\n结果: PASS')
  await closeBrowser()
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
  process.exit(fails.length ? 1 : 0)
}

const showGreeting = (dash) => !!(dash?.data?.teacherName)

main().catch(async (e) => {
  console.error('脚本异常:', e.message)
  await closeBrowser()
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
  process.exit(2)
})
