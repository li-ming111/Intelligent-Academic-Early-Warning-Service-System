// 小程序学生端「我的申诉」卡片：到底有没有写出**是哪一次成绩**？
//
// 起因（2026-10-03，第四十三轮 W9）：修完 PC 端后复盘发现 **小程序一个检查都没跑**，
//   而本轮改了 `academic-warning-xcx/src/pages/student/appeals.vue`（卡片与详情改用
//   `scoreLabelOf(a)`）。`xcx-page-smoke.mjs` 那一条对申诉页的判据只是
//   `expect='申诉总数' / data='申诉原因'` —— 它证明"页面上有申诉列表"，**证明不了**
//   W9 那一列渲染出了成绩引用。本探针就是来补这条的。
//
// 它断言的是**卡片里渲染出来的字**（`.appeal-card__course` 的 textContent），
// 并且拿**后端接口真值**做期望值，不是我照着页面输出反推的：
//   A  接口层：经网关打 `/api/students/appeals/{uid}/list`，id=6 / id=8 两行的
//      scoreSemester/scoreYear/scoreTotal 必须与活库一致（顺带证明现役 8079 是新包）
//   B  渲染层：id=6 那张卡的标题必须**逐字等于** `高等数学 · 25秋 · 56.5分`
//              id=8 那张卡的标题必须**逐字等于** `大学英语 · 24春 · 51分`
//   C  负向对照（证伪力）：两张卡的标题必须**不相同**，且各自不含对方的分数
//      —— 少了 C，一个"把同一个串印到每张卡上"的渲染照样能骗过 B
//   D  张数：卡片张数 == 接口条数
//
// 用法: node probe-w9-xcx-appeal-score.mjs [学号] [密码]   退出码 0=一致, 1=不一致
//
// ⚠️ 跑之前 H5 dev server 要在 5180 上；跑完它会杀掉 chrome，
//    而 chrome.kill() 会把 vite 一起带走（见 memory「CDP 脚本 kill 浏览器会打死 vite」）
//    —— 需要接着跑别的探针就自己重启 `npm run dev:h5`。
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || '2023020616'
const PASS = process.argv[3] || '123456'
/** `--fallback`：追加 E 段（抹掉接口里的成绩字段，验降级与"不显示 null"）。 */
const FALLBACK = process.argv.includes('--fallback')
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://127.0.0.1:5180'
const PORT = 9800 + Math.floor(Math.random() * 90)
const profile = mkdtempSync(join(tmpdir(), 'cdpxcxw9-'))
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

// 期望值：来自活库 `scores` 的 id=157 / 152（student_profile.id=3 司明），
// 学期标签由仓库自带的 `semesterLabelOf(5,2025)` → `2025-2026-1` → `shortSemester` → `25秋` 推出。
const EXPECT = {
  6: '高等数学 · 25秋 · 56.5分',
  8: '大学英语 · 24春 · 51分',
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
  if (!target) throw new Error('Chrome 调试端口未就绪')
  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
  let id = 0
  const pending = new Map()
  const net4xx = []
  /** 页面自己发出的申诉列表请求 —— A 段断言的**就是这份真响应**（不自己重拼 URL）。 */
  const listReqs = []
  /** `--fallback` 用：响应阶段拦截，把 JSON 里的成绩字段**抹掉**再放回浏览器。 */
  let pausedCount = 0
  let fulfillCount = 0
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Fetch.requestPaused') {
      const rid = m.params.requestId
      pausedCount++
      ;(async () => {
        try {
          const rb = await send('Fetch.getResponseBody', { requestId: rid })
          const raw = rb.result?.base64Encoded
            ? Buffer.from(rb.result.body, 'base64').toString('utf8') : (rb.result?.body || '')
          const j = JSON.parse(raw)
          if (!Array.isArray(j.data)) throw new Error('载荷里没有 data 数组，认不出形状')
          for (const row of j.data) {
            delete row.scoreSemester; delete row.scoreYear
            delete row.scoreTotal; delete row.scoreGrade; delete row.scoreId
          }
          await send('Fetch.fulfillRequest', {
            requestId: rid, responseCode: 200,
            responseHeaders: [{ name: 'Content-Type', value: 'application/json;charset=UTF-8' }],
            body: Buffer.from(JSON.stringify(j)).toString('base64'),
          })
          fulfillCount++
          say(`  （已抹字段并回填：id=${JSON.stringify(j.data.map((r) => r.id))}）`)
        } catch (err) {
          say(`  ⚠️ 抹字段失败（${err.message}）—— 继续原样放行，本段结论不可信`)
          try { await send('Fetch.continueRequest', { requestId: rid }) } catch {}
        }
      })()
      return
    }
    if (m.method === 'Network.responseReceived') {
      const r = m.params.response
      if (/\/api\/students\/appeals\/[^/]+\/list/.test(r.url)) {
        listReqs.push({ requestId: m.params.requestId, status: r.status, url: r.url })
      }
      if (r.url.includes('/api/') && r.status >= 400) net4xx.push(`${r.status} ${r.url.replace(/^https?:\/\/[^/]+/, '').split('?')[0]}`)
    }
  }
  const send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    return r.result?.result?.value
  }

  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 414, height: 896, deviceScaleFactor: 2, mobile: true })
  await send('Page.navigate', { url: APP })

  const waitFor = async (expr, timeoutMs = 45000) => {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      try { if (await evaluate(expr)) return true } catch {}
      await sleep(400)
    }
    return false
  }

  // —— 登录 ——（验证码服务端签发，必须等它到位再读，否则读到空串 ⇒ 假「登录未落地」）
  await waitFor(`document.querySelectorAll('uni-input').length >= 3 && (document.querySelector('.captcha-code')?.textContent || '').trim().length > 0`)
  const filled = await evaluate(`(() => {
    const byPh = (ph) => {
      const wrap = Array.from(document.querySelectorAll('uni-input'))
        .find(u => (u.querySelector('.uni-input-placeholder')?.textContent || '').trim() === ph)
      return wrap ? wrap.querySelector('input') : null
    }
    const setVal = (el, val) => {
      if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true }))
      el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    const a = setVal(byPh('请输入学号或工号'), ${JSON.stringify(USER)})
    const b = setVal(byPh('请输入密码'), ${JSON.stringify(PASS)})
    const c = setVal(byPh('输入验证码'), (document.querySelector('.captcha-code')?.textContent || '').trim())
    document.querySelector('.btn')?.click()
    return { a, b, c }
  })()`)
  if (!filled?.a || !filled?.b || !filled?.c) throw new Error(`登录表单没找全: ${JSON.stringify(filled)}`)
  const landed = await waitFor(`(localStorage.getItem('token') ? 1 : 0) === 1 && (location.hash || '').includes('/pages/student/')`, 30000)
  if (!landed) throw new Error(`登录失败（${USER}）`)
  say(`已登录 ${USER}\n`)

  say('[A] 接口层（抓页面**自己**发出的那次 /appeals/{uid}/list 的真响应）')
  // 为什么不自己重拼 URL 再 fetch 一遍：第一版就是那么写的，结果拿到 HTTP 200 + code 500 + 0 条，
  // 而同一刻页面**明明渲染出了两张卡** —— 因为 uni-app 的 storage 与裸 localStorage 的读写形状不同，
  // 我拼出来的 uid/token 不是页面用的那个。抓页面真响应既绕开这个坑，判据也更硬：
  // A 段断言的就是**渲染层的输入**，B 段断言的是它的输出，两段对的是同一份字节。
  await evaluate(`location.hash = '#/pages/student/appeals'`)
  const arrived = await waitFor(`document.querySelectorAll('.appeal-card').length > 0`, 25000)
  if (!arrived) throw new Error('申诉卡片一张都没渲染出来（选择器可能变了，或页面没加载完）')
  // 卡片渲染完还要等一下，让 courseMap（来自 /students/scores）也回来 —— 课程名靠它反查
  await waitFor(`![...document.querySelectorAll('.appeal-card__course')].some(e => (e.textContent||'').includes('课程 #'))`, 12000)
  await sleep(800)

  if (!listReqs.length) throw new Error('没抓到 /appeals/{uid}/list 的响应 —— 页面取的可能是别的端点')
  const listReq = listReqs[listReqs.length - 1]
  const bodyRes = await send('Network.getResponseBody', { requestId: listReq.requestId })
  let body = null
  try { body = JSON.parse(bodyRes.result?.body || 'null') } catch {}
  const apiRows = (Array.isArray(body?.data) ? body.data : []).map((x) => ({
    id: x.id, courseId: x.courseId, scoreId: x.scoreId,
    sem: x.scoreSemester, year: x.scoreYear, total: x.scoreTotal,
  }))
  const byId = (n) => apiRows.find((r) => r.id === n)
  check('A1 页面那次请求 HTTP 200 且 body.code=200、条数>0',
        listReq.status === 200 && body?.code === 200 && apiRows.length > 0,
        `HTTP ${listReq.status} code=${body?.code} 条数=${apiRows.length} url=${listReq.url.replace(/^https?:\/\/[^/]+/, '')}`)
  check('A2 id=6 带出 sem=5 / year=2025 / total=56.5（现役服务是新包）',
        byId(6) && String(byId(6).sem) === '5' && Number(byId(6).year) === 2025 && Number(byId(6).total) === 56.5,
        `scoreId=${byId(6)?.scoreId} sem=${byId(6)?.sem} year=${byId(6)?.year} total=${byId(6)?.total}`)
  check('A3 id=8 带出 sem=4 / year=2024 / total=51',
        byId(8) && String(byId(8).sem) === '4' && Number(byId(8).year) === 2024 && Number(byId(8).total) === 51,
        `scoreId=${byId(8)?.scoreId} sem=${byId(8)?.sem} year=${byId(8)?.year} total=${byId(8)?.total}`)

  say('\n[B] 渲染层（/pages/student/appeals 卡片标题）')

  const cards = await evaluate(`[...document.querySelectorAll('.appeal-card')].map(c => ({
    title: (c.querySelector('.appeal-card__course')?.textContent || '').replace(/\\s+/g, ' ').trim(),
    text: (c.innerText || '').replace(/\\s+/g, ' ').trim(),
  }))`)
  const cardOf = (expectTitle) => cards.find((c) => c.title === expectTitle)
  const c6 = cardOf(EXPECT[6])
  const c8 = cardOf(EXPECT[8])

  check(`B1 id=6 卡片标题逐字等于「${EXPECT[6]}」`, !!c6,
        c6 ? c6.title : `实际标题：${JSON.stringify(cards.map((c) => c.title))}`)
  check(`B2 id=8 卡片标题逐字等于「${EXPECT[8]}」`, !!c8,
        c8 ? c8.title : `实际标题：${JSON.stringify(cards.map((c) => c.title))}`)

  say('\n[C] 负向对照（证伪力 —— 防"每张卡印同一个串"）')
  const titles = cards.map((c) => c.title)
  check('C1 两张卡的标题互不相同', new Set(titles).size === titles.length, JSON.stringify(titles))
  // 第一版这里写的是 `... && !!c6 && !c8` —— `c8` 是**卡片对象**不是字符串，`!c8` 只会
  // 在"大学英语那张卡不存在"时成立，等于把 B2 重判了一遍，方向还相反（C 段因此恒假红）。
  // 改成对**标题串**断言：唯一那条含 56.5 的标题必须就是 id=6 的期望标题。
  const t56 = titles.filter((t) => t.includes('56.5'))
  check('C2 含 56.5 的卡只有一张，且正是高等数学那张',
        t56.length === 1 && t56[0] === EXPECT[6],
        `含 56.5 的标题=${JSON.stringify(t56)}`)
  check('C3 大学英语那张卡里没有 56.5、也没有 25秋',
        !!c8 && !c8.title.includes('56.5') && !c8.title.includes('25秋'),
        c8 ? c8.title : '没找到')

  say('\n[D] 张数一致')
  check('D 卡片张数 == 接口条数', cards.length === apiRows.length, `卡片 ${cards.length} 张 vs 接口 ${apiRows.length} 条`)

  say('\n渲染出来的卡片标题：')
  cards.forEach((c, i) => say(`  ${i + 1}. ${c.title}`))

  // —— E 段（--fallback）：把接口里的成绩字段**抹掉**再渲染，验降级路径 ——
  // 这是 B 段的**反证**：如果抹掉字段后卡片**还是**印着 56.5，那说明那个串不来自接口，
  // B 段"标题等于…56.5分"就证明不了任何事（页面自己编的也能过）。同时它验的是
  // 计划里那条显式契约 —— 老行/课程级入口没有成绩字段时，退化成课程名，
  // **不显示空白、也不显示 null/undefined**。
  if (FALLBACK) {
    say('\n[E] 降级路径（抹掉接口里的成绩字段 —— B 段的反证 + "不显示 null" 契约）')
    // 只拦 **列表**这一个端点：同一页还会发 `/appeals/{uid}/statistics`，
    // 它的 data 是对象不是数组，一起拦下来会被上面那句 `认不出形状` 打回来（第一版就撞了）。
    await send('Fetch.enable', { patterns: [{ urlPattern: '*/api/students/appeals/*/list*', requestStage: 'Response' }] })
    // ⚠️ 第一版这里用 `location.hash = …` 切路由，卡片标题一点没变；第二版改用 `Page.navigate`
    //    到同一个 hash URL —— 那是**同文档导航**，压根没重新请求（requestPaused=0，E0 抓到了）。
    //    真正重新拉数据要**整页 reload**。并且无论用哪种，"拦截是否真的发生"都得先自证
    //    （pausedCount/fulfillCount），否则这一段的红绿都不算数。
    await send('Page.reload', { ignoreCache: true })
    await waitFor(`document.querySelectorAll('.appeal-card').length > 0`, 30000)
    await sleep(2500)
    say(`  拦截统计：requestPaused=${pausedCount} fulfill=${fulfillCount}`)
    if (!fulfillCount) {
      check('E0 拦截真的生效了（fulfill 次数 > 0）', false,
            `requestPaused=${pausedCount} fulfill=${fulfillCount} —— 抹字段没发生，E 段的红绿都不算数`)
    } else {
      check('E0 拦截真的生效了（fulfill 次数 > 0）', true, `fulfill=${fulfillCount}`)
    }
    const stripped = await evaluate(`[...document.querySelectorAll('.appeal-card__course')].map(e => (e.textContent||'').replace(/\\s+/g,' ').trim())`)
    say(`  抹字段后的标题：${JSON.stringify(stripped)}`)
    check('E1 抹掉字段后降级成纯课程名（高等数学 / 大学英语）',
          stripped.length === 2 && stripped.includes('高等数学') && stripped.includes('大学英语'),
          JSON.stringify(stripped))
    check('E2 降级后不出现 56.5 / 25秋（否则 B 段的那个串根本不是接口给的）',
          !stripped.some((t) => t.includes('56.5') || t.includes('25秋') || t.includes('51分')),
          JSON.stringify(stripped))
    check('E3 降级后不出现 null / undefined / NaN / [object',
          !stripped.some((t) => /null|undefined|NaN|\[object/i.test(t)),
          JSON.stringify(stripped))
    await send('Fetch.disable')
  }

  if (net4xx.length) say(`\n期间 4xx/5xx：${JSON.stringify([...new Set(net4xx)])}`)

  say('\n================ 汇总 ================')
  say(fails.length ? `失败 ${fails.length} 项：${fails.join(' / ')}` : '全部通过')
  return fails.length ? 1 : 0
}

main().then((c) => { cleanup(); process.exit(c) })
  .catch((e) => { cleanup(); console.error('探针异常:', e.message); process.exit(2) })
