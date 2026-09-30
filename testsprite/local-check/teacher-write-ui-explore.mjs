// 探路：教师端成绩页的两个 sheet（改分 / 录入）在 H5 里长什么样。
//
// 目的不是断言，是**把要驱动的 DOM 结构打出来**：
//   · 流水行怎么定位（按学生姓名 + 学期文案）
//   · 改分 sheet 里 总评 input / 备注 textarea / 保存按钮 的真实选择器
//   · 录入 sheet 里 uni-app 的 picker 在 H5 渲染成什么 —— 是原生 <select> 还是自绘弹层，
//     决定了"全班选一人 + 选学期"能不能被脚本驱动
//
// 用法: node teacher-write-ui-explore.mjs 10001 123456
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || '10001'
const PASS = process.argv[3] || '123456'
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.XCX_BASE || 'http://127.0.0.1:5180'
const PORT = 9700 + Math.floor(Math.random() * 100)

const profile = mkdtempSync(join(tmpdir(), 'cdtex-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank'
], { stdio: 'ignore' })

const sleep = ms => new Promise(r => setTimeout(r, ms))
const cleanup = () => {
  try { chrome.kill() } catch {}
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
}
const lines = []
const say = s => { lines.push(s); console.log(s) }

/**
 * DOM 结构摘要：标签名 + class + 文本（截断），去掉内联样式噪声。
 *
 * 写成「函数声明 + IIFE 调用」而不是箭头函数表达式：箭头函数体里
 * `SHAPE(c, depth+1)` 那种自引用在 `(${SHAPE})(...)` 注入页面后是未定义的名字，
 * 实测直接抛 Uncaught。函数声明在 IIFE 作用域里才能被递归调用。
 */
const shapeOf = sel =>
  `(() => {
    function shape(el, depth) {
      if (!el) return ''
      const pad = '  '.repeat(depth)
      const cls = (el.className && typeof el.className === 'string')
        ? '.' + el.className.trim().split(/\\s+/).join('.') : ''
      const own = Array.from(el.childNodes).filter(n => n.nodeType === 3)
        .map(n => n.textContent.trim()).join(' ').slice(0, 60)
      let out = pad + '<' + el.tagName.toLowerCase() + cls + '>' + (own ? ' \\u00ab' + own + '\\u00bb' : '') + '\\n'
      for (const c of Array.from(el.children).slice(0, 12)) {
        if (depth < 5) out += shape(c, depth + 1)
      }
      return out
    }
    const anchor = document.querySelector(${JSON.stringify(sel)})
    return shape((anchor && anchor.closest('.form')) || anchor, 0)
  })()`

const main = async () => {
  let target = null
  for (let i = 0; i < 80; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      target = list.find(t => t.type === 'page')
      if (target) break
    } catch {}
    await sleep(250)
  }
  if (!target) { say('FAIL 调试端口未就绪'); cleanup(); process.exit(1) }

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })

  let id = 0
  const pending = new Map()
  ws.onmessage = e => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id) }
  }
  const send = (method, params = {}) =>
    new Promise(res => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async expr => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) throw new Error(r.result.exceptionDetails.text)
    return r.result?.result?.value
  }
  const waitFor = async (expr, timeoutMs = 45000) => {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      try { if (await evaluate(expr)) return true } catch {}
      await sleep(400)
    }
    return false
  }

  await send('Page.enable')
  await send('Runtime.enable')
  await send('Page.navigate', { url: APP })

  // —— 登录 ——
  await waitFor(`document.querySelectorAll('uni-input').length >= 3`)
  await evaluate(`(() => {
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
    setVal(byPh('请输入学号或工号'), ${JSON.stringify(USER)})
    setVal(byPh('请输入密码'), ${JSON.stringify(PASS)})
    setVal(byPh('输入验证码'), (document.querySelector('.captcha-code')?.textContent || '').trim())
    document.querySelector('.btn')?.click()
    return true
  })()`)
  const landed = await waitFor(`(location.hash || '').includes('/pages/teacher/')`, 30000)
  say(`登录 landed=${landed}`)

  // —— 成绩页 ——
  await evaluate(`location.hash = '#/pages/teacher/scores'`)
  await waitFor(`document.body.innerText.includes('成绩流水')`, 25000)
  await sleep(2500)

  say('')
  say('==== 一、流水列表 ====')
  const rows = await evaluate(`(() => {
    const all = Array.from(document.querySelectorAll('.srow'))
    return {
      count: all.length,
      first: all.slice(0, 6).map(r => r.innerText.replace(/\\n/g, ' | ')),
      semings: all.filter(r => r.innerText.includes('司明')).map(r => r.innerText.replace(/\\n/g, ' | '))
    }
  })()`)
  say(`.srow 行数 = ${rows.count}`)
  say('前 6 行:')
  for (const r of rows.first) say(`  | ${r}`)
  say('含「司明」的行:')
  for (const r of rows.semings) say(`  | ${r}`)

  // —— 改分 sheet ——
  say('')
  say('==== 二、点第一行 → 改分 sheet ====')
  await evaluate(`document.querySelectorAll('.srow')[0]?.click()`)
  const sheetUp = await waitFor(`!!document.querySelector('.form__danger')`, 8000)
  say(`sheet 打开 = ${sheetUp}`)
  const edit = await evaluate(`(() => {
    if (!document.querySelector('.form__danger')) return { ok: false }
    const sheet = document.querySelector('.form__danger').closest('uni-view')?.parentElement
    const inputs = Array.from(document.querySelectorAll('uni-input input, input'))
      .filter(el => el.closest('.form__danger') || el.closest('uni-view'))
    return {
      text: document.body.innerText.split('\\n').slice(0, 0).join(''),
      formInputs: Array.from(document.querySelectorAll('uni-input')).map(u => ({
        wrapClass: u.className,
        ph: (u.querySelector('.uni-input-placeholder')?.textContent || '').trim(),
        val: u.querySelector('input')?.value
      })),
      textareas: Array.from(document.querySelectorAll('uni-textarea')).map(t => ({
        wrapClass: t.className,
        ph: (t.querySelector('.uni-textarea-placeholder')?.textContent || '').trim(),
        val: t.querySelector('textarea')?.value
      })),
      danger: document.querySelector('.form__danger')?.innerText.trim(),
      footerBtn: document.querySelector('.btn-primary')?.innerText.trim()
    }
  })()`)
  say(`uni-input: ${JSON.stringify(edit.formInputs)}`)
  say(`uni-textarea: ${JSON.stringify(edit.textareas)}`)
  say(`删除按钮: ${JSON.stringify(edit.danger)}  底栏按钮: ${JSON.stringify(edit.footerBtn)}`)
  const shape = await evaluate(shapeOf('.form__danger'))
  say('--- 改分 sheet 结构（从删除按钮往上找 .form） ---')
  for (const l of String(shape).split('\n')) say(`  ${l}`)

  // 关掉
  await evaluate(`(() => { const m = document.querySelector('uni-modal, .uni-mask, .wd-overlay'); return !!m })()`)
  await evaluate(`(() => {
    // detail-sheet 的关闭：点遮罩
    const mask = Array.from(document.querySelectorAll('uni-view, div'))
      .find(el => (el.className || '').toString().includes('overlay') || (el.className || '').toString().includes('mask'))
    if (mask) { mask.click(); return true }
    return false
  })()`)
  await sleep(800)

  // —— 录入 sheet ——
  say('')
  say('==== 三、录入 sheet 与 picker ====')
  const back = await evaluate(`(() => {
    const b = Array.from(document.querySelectorAll('.bar-head__btn')).find(x => x.innerText.includes('录入成绩'))
    if (b) { b.click(); return true }
    return false
  })()`)
  const createUp = await waitFor(`document.body.innerText.includes('沿用最近学期')`, 8000)
  say(`点「录入成绩」= ${back}，sheet 打开 = ${createUp}`)

  const pickers = await evaluate(`(() => {
    const ps = Array.from(document.querySelectorAll('uni-picker'))
    return ps.map(p => ({
      cls: p.className,
      inner: p.innerHTML.slice(0, 400),
      text: p.innerText.replace(/\\n/g, ' | ')
    }))
  })()`)
  say(`uni-picker 数量 = ${pickers.length}`)
  for (const p of pickers) say(`  ${JSON.stringify(p)}`)

  const shape2 = await evaluate(shapeOf('uni-picker'))
  say('--- 录入 sheet 结构（从第一个 picker 往上找 .form） ---')
  for (const l of String(shape2).split('\n')) say(`  ${l}`)

  // 点第一个 picker，看弹出什么
  say('')
  say('==== 四、点第一个 picker 后 DOM 新增了什么 ====')
  await evaluate(`(() => {
    window.__before = new Set(Array.from(document.querySelectorAll('*')))
    const p = document.querySelector('uni-picker'); if (p) p.click()
    return true
  })()`)
  await sleep(1200)
  const added = await evaluate(`(() => {
    const now = Array.from(document.querySelectorAll('*'))
    const fresh = now.filter(el => !window.__before.has(el) && el.nodeType === 1)
    return {
      count: fresh.length,
      tops: fresh.filter(el => el.parentElement && el.parentElement.tagName === 'BODY')
        .map(el => el.tagName.toLowerCase() + '.' + String(el.className)),
      samples: fresh.slice(0, 40).map(el => {
        const own = Array.from(el.childNodes).filter(n => n.nodeType === 3).map(n => n.textContent.trim()).join(' ').slice(0, 30)
        return el.tagName.toLowerCase() + '.' + String(el.className) + (own ? ' «' + own + '»' : '')
      }),
      selects: Array.from(document.querySelectorAll('select')).length,
      items: Array.from(document.querySelectorAll('.uni-picker-item, .uni-picker-content .uni-picker-item'))
        .map(el => el.innerText.trim()).slice(0, 12),
      actions: Array.from(document.querySelectorAll('.uni-picker-action, .uni-picker-header *'))
        .map(el => el.tagName.toLowerCase() + '.' + String(el.className) + ' «' + el.innerText.trim().slice(0, 12) + '»').slice(0, 12)
    }
  })()`)
  say(`新增节点数 = ${added.count}；<select> 数 = ${added.selects}`)
  say(`新增顶层节点: ${JSON.stringify(added.tops)}`)
  for (const s of added.samples) say(`  + ${s}`)
  say(`picker item 文本: ${JSON.stringify(added.items)}`)
  say(`picker 头部/动作: ${JSON.stringify(added.actions)}`)

  writeFileSync('teacher-write-ui-explore.txt', lines.join('\n') + '\n', 'utf8')
  console.log('已写出 teacher-write-ui-explore.txt')
  cleanup()
  process.exit(0)
}

main().catch(e => { say(`FAIL ${e.message}`); cleanup(); process.exit(1) })
