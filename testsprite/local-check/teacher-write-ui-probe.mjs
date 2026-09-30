// 教师端成绩页**写路径**验证 —— 这次是从**小程序的界面**上点，不是直接打接口。
//
// 为什么还要单独跑一遍（接口层的往返已经在 teacher-write-probe.mjs 里验过了）：
// 接口层验的是"后端接到这个 body 能落库"，验不到"我的页面发出的 body 是不是这个形状"。
// `submitScore` 发的是 `{score, remark, modifiedBy}`、`submitCreate` 发的是
// `{teacherId, scores:[{studentId, studentName, className, courseId, score, remark, semester}]}` ——
// 少一个字段、`semester` 传成数字、`studentId` 误传学号，接口层那个脚本全都发现不了，
// 但页面一点就报错。所以这一轮的价值就在"页面自己发出的那个 body"。
//
// ⚠️ 会真改库。跑之前必须先快照（本目录上一级 db_backups/）：
//     mysqldump ... academic_warning_system score_record scores score_audit_log \
//       > db_backups/teacher_ui_write_probe_YYYYMMDD_before.sql
//   跑完执行 db_backups/teacher_ui_write_probe_20260929_rollback.sql + verify.sql。
//
// 设计上的两个自保措施：
//   1. **不碰任何原有数据**。增/改/删三步全部作用在脚本自己新建的那一行上
//      （司明 + 高等数学 + 第8学期，全库无此行），跑完它自己也删掉了，库里不留痕。
//      原有一行（score_record#277）一个字节都不动 —— 这样回滚只需要"确认没有残留"。
//   2. **写库之前先断言**：学生/学期两个 picker 选完、分数填完后，
//      「保存成绩」按钮必须不再是禁用态（那个 class 绑定的是 canCreate）。
//      选不中 picker 就停在写库之前退出，不会先写了再发现参数不对。
//
// 用法: node teacher-write-ui-probe.mjs [用户名] [密码]
// 输出: teacher-write-ui-probe.txt（UTF-8，避免控制台中文乱码）
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || '10001'
const PASS = process.argv[3] || '123456'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.XCX_BASE || 'http://127.0.0.1:5180'
const PORT = 9700 + Math.floor(Math.random() * 100)

/** 三步用的值。remark 是回滚脚本的定位条件，必须独特且在真实数据里不可能出现。 */
const STUDENT = '司明'          // 学号 2023020616，profile id 3
const SEMESTER = '第8学期'      // 全库现有学期只到第6学期，第8学期必定是新行
const CREATE_SCORE = 77
const CREATE_REMARK = 'UI写路径验证-临时'
const UPDATE_SCORE = 88
const UPDATE_REMARK = 'UI改分验证-临时'

const profile = mkdtempSync(join(tmpdir(), 'cdtui-'))
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

let failures = 0
const ok = m => { say(`  ✅ ${m}`) }
const fail = m => { failures++; say(`  ❌ ${m}`) }
const info = m => { say(`     ${m}`) }

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
  /** 页面自己 console.error/warn 的内容 —— picker 里为什么一条候选都没有，线索多半在这里。 */
  let logs = []
  ws.onmessage = e => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Runtime.consoleAPICalled' && ['error', 'warning'].includes(m.params.type)) {
      const text = (m.params.args || []).map(a => a.value ?? a.description ?? '').join(' ')
      if (text) logs.push(`[${m.params.type}] ${text.slice(0, 300)}`)
    }
  }
  const send = (method, params = {}) =>
    new Promise(res => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async expr => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) {
      // exceptionDetails.text 只有一句泛化的 "Uncaught"，真正有用的是 exception.description
      // （带行号与错误类型），排查页面里的 TypeError 全靠它
      const d = r.result.exceptionDetails
      throw new Error(`页面里抛异常: ${d.exception?.description || d.text}`)
    }
    return r.result?.result?.value
  }
  const waitFor = async (expr, timeoutMs = 30000) => {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      try { if (await evaluate(expr)) return true } catch {}
      await sleep(350)
    }
    return false
  }

  await send('Page.enable')
  await send('Runtime.enable')
  await send('Page.navigate', { url: APP })

  // ---------- 登录 ----------
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
  if (!await waitFor(`(location.hash || '').includes('/pages/teacher/')`, 30000)) {
    say('FAIL 登录未跳到教师端'); cleanup(); process.exit(1)
  }
  say(`已登录 ${await evaluate(`localStorage.getItem('userName')`)}`)

  // ---------- 成绩页 ----------
  await evaluate(`location.hash = '#/pages/teacher/scores'`)
  if (!await waitFor(`document.body.innerText.includes('成绩流水')`, 25000)) {
    say('FAIL 成绩页没渲染出来'); cleanup(); process.exit(1)
  }
  await sleep(2500)

  const before = await evaluate(`(() => {
    const rows = Array.from(document.querySelectorAll('.srow'))
    return {
      count: rows.length,
      siming: rows.filter(r => r.innerText.includes('司明')).map(r => r.innerText.replace(/\\n/g, ' | ')),
      hasTestRow: rows.some(r => r.innerText.includes(${JSON.stringify(CREATE_REMARK)}))
    }
  })()`)
  say('')
  say(`改库前的成绩流水：${before.count} 行；司明 ${before.siming.length} 行`)
  for (const s of before.siming) info(`| ${s}`)
  // 前置：上一次没跑干净的话，这里的断言会失败，说明该先回滚再重跑
  if (before.hasTestRow) {
    say(`FAIL 列表里已经存在「${CREATE_REMARK}」的行 —— 上一次验证没回滚，先跑回滚脚本`)
    writeFileSync('teacher-write-ui-probe.txt', lines.join('\n') + '\n', 'utf8')
    cleanup(); process.exit(2)
  }

  // ---------- 第一步：录入（增） ----------
  say('')
  say('==== 增：录入一条新成绩 ====')

  const opened = await evaluate(`(() => {
    const b = Array.from(document.querySelectorAll('.bar-head__btn')).find(x => x.innerText.includes('录入成绩'))
    if (b) b.click()
    return !!b
  })()`)
  if (!opened || !await waitFor(`document.body.innerText.includes('沿用最近学期') && !!document.querySelector('uni-picker')`, 8000)) {
    fail('录入 sheet 没打开'); writeFileSync('teacher-write-ui-probe.txt', lines.join('\n') + '\n', 'utf8'); cleanup(); process.exit(1)
  }
  ok('点「录入成绩」打开了录入 sheet')

  // 打开后的第一件事：数一数两个 picker 各有多少候选。
  // 学生 picker 的候选来自 roster（selectCourse → loadRoster → by-class 去重），
  // 候选为 0 就说明那次花名册请求没成 —— 这时绝对不能再往下走（会写进去一条 studentId=undefined 的行）。
  const pre = await evaluate(`(() => {
    const ps = Array.from(document.querySelectorAll('uni-picker'))
    return {
      perPicker: ps.map(p => ({ text: (p.innerText || '').trim().slice(0, 20), n: p.querySelectorAll('.uni-picker-item').length })),
      inputs: document.querySelectorAll('uni-input').length,
      textareas: document.querySelectorAll('uni-textarea').length
    }
  })()`)
  info(`两个 picker 的候选数：${JSON.stringify(pre.perPicker)}`)
  info(`sheet 里 uni-input=${pre.inputs} 个 / uni-textarea=${pre.textareas} 个`)
  if (logs.length) {
    info('页面报错：')
    for (const l of logs.slice(0, 6)) info(`  ! ${l}`)
  }
  if (!pre.perPicker.some(p => p.n > 1)) {
    say('')
    fail('学生 picker 一个候选都没有（花名册没加载上）—— **就此停手，没有写库**')
    say('     这不是页面 bug 的结论：先看上面有没有 by-class 的报错行；')
    say('     花名册是 selectCourse 里 await 的，正常情况下打开 sheet 前就该到位。')
    if (logs.length) for (const l of logs.slice(0, 10)) say(`     ! ${l}`)
    writeFileSync('teacher-write-ui-probe.txt', lines.join('\n') + '\n', 'utf8')
    cleanup(); process.exit(2)
  }

  // 学生 picker：选 司明。uni-app H5 的 mode=selector 渲染成每个 picker 各自一份
  // uni-selector-select 列表（不是滚轮）。
  //
  // ⚠️ 分三步、每步一个 evaluate：把「点开 → 点条目 → 点完成」塞进同一次 evaluate 里
  //    会在第 3 步炸 —— 点完条目后弹层重渲染，`.uni-picker-action-confirm` 当场取不到
  //    （实测 TypeError: Cannot read properties of null）。分步之后每步都重新查 DOM。
  const pickOption = async (anchor, option) => {
    const opened = await evaluate(`(() => {
      const ps = Array.from(document.querySelectorAll('uni-picker'))
      const p = ps.find(x => (x.innerText || '').trim() === ${JSON.stringify(anchor)})
      if (!p) return { err: 'anchor-not-found', seen: ps.map(x => (x.innerText || '').trim().slice(0, 24)) }
      p.click()
      return { ok: true }
    })()`)
    if (opened.err) return opened
    await sleep(1200)

    // ⚠️ 候选要**在整个文档**里找，不能在 uni-picker 里找：
    //    关闭态下每个 picker 自带一份隐藏的内联候选表（学生 24 条 / 学期 9 条），
    //    一旦点开，内联那份被卸载（实测 n 从 24 掉到 0），弹层那份挂在 picker 元素**之外**
    //    （疑似 teleport 到应用根节点）。所以在 picker 里查会查空，必须文档级查。
    const clicked = await evaluate(`(() => {
      const items = Array.from(document.querySelectorAll('.uni-picker-item'))
      const matches = items.filter(el => el.innerText.trim() === ${JSON.stringify(option)})
      if (!matches.length) {
        return {
          err: 'option-not-found',
          total: items.length,
          seen: items.map(el => el.innerText.trim()).slice(0, 30)
        }
      }
      // 取最后一个：内联那份在文档前部，弹层那份在后面
      matches[matches.length - 1].click()
      return { ok: true, total: items.length }
    })()`)
    if (clicked.err) return clicked
    await sleep(700)

    // 有些版本点条目就直接生效，有些要再点一次「完成」—— 先看显示值变没变再决定
    const after = await evaluate(`(() => {
      const shown = Array.from(document.querySelectorAll('uni-picker')).map(x => (x.innerText || '').trim().slice(0, 30))
      if (shown.some(t => t.includes(${JSON.stringify(option)}))) return { shown, confirmed: false }
      const btns = Array.from(document.querySelectorAll('.uni-picker-action-confirm'))
      if (!btns.length) return { shown, err: 'no-confirm-btn' }
      btns[btns.length - 1].click()
      return { shown, confirmed: true }
    })()`)
    await sleep(700)
    const finalShown = await evaluate(`Array.from(document.querySelectorAll('uni-picker')).map(x => (x.innerText || '').trim().slice(0, 30))`)
    return { ok: true, after, finalShown }
  }

  const pickStudent = await pickOption('选择一名学生', `${STUDENT}（2023020616）`)
  if (pickStudent.err) fail(`选学生失败：${JSON.stringify(pickStudent)}`)
  else if (!pickStudent.finalShown.some(t => t.includes(STUDENT))) {
    fail(`学生 picker 不认这次选择，显示值仍是 ${JSON.stringify(pickStudent.finalShown)}`)
  } else {
    ok(`学生 picker 显示为「${pickStudent.finalShown.find(t => t.includes(STUDENT))}」— 花名册带学号，说明取的是 by-class 那份`)
  }

  // 学期 picker：选 第8学期（不选的话默认「沿用最近学期」= 覆盖第5学期那条，那就动了原有数据）
  const pickSem = await pickOption('沿用最近学期', SEMESTER)
  if (pickSem.err) fail(`选学期失败：${JSON.stringify(pickSem)}`)
  else if (!pickSem.finalShown.some(t => t.includes(SEMESTER))) {
    fail(`学期 picker 不认这次选择，显示值仍是 ${JSON.stringify(pickSem.finalShown)}`)
  } else {
    ok(`学期 picker 显示为「${SEMESTER}」（刻意错开第5学期，避免覆盖司明原有那条）`)
  }

  // 总分 + 评语：都在录入 sheet 的 .form 里，用「沿用最近学期」那个 picker 往上找根节点，
  // 免得跟（可能还留在 DOM 里的）改分 sheet 的 input 抢选择器
  const filled = await evaluate(`(() => {
    const anchor = Array.from(document.querySelectorAll('uni-picker'))
      .find(p => (p.innerText || '').includes(${JSON.stringify(SEMESTER)}))
    const form = anchor && anchor.closest('.form')
    if (!form) return { err: '找不到录入 sheet 的 .form' }
    const setNative = (el, val) => {
      if (!el) return false
      const proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement : window.HTMLInputElement
      Object.getOwnPropertyDescriptor(proto.prototype, 'value').set.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true }))
      el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    const a = setNative(form.querySelector('uni-input input'), String(${CREATE_SCORE}))
    const b = setNative(form.querySelector('uni-textarea textarea'), ${JSON.stringify(CREATE_REMARK)})
    return { a, b }
  })()`)
  if (filled.err) fail(`填分/评语失败：${filled.err}`)
  else if (!filled.a || !filled.b) fail(`填分/评语没落到控件上 ${JSON.stringify(filled)}`)
  else ok(`填了总评 ${CREATE_SCORE}、评语「${CREATE_REMARK}」`)
  await sleep(600)

  // 写库之前的闸门：按钮不禁用 = 页面的 canCreate 认为参数齐了
  const gate = await evaluate(`(() => {
    const btn = Array.from(document.querySelectorAll('.btn-primary')).find(b => b.innerText.includes('保存成绩'))
    if (!btn) return { err: '找不到保存成绩按钮' }
    return { text: btn.innerText.trim(), disabled: String(btn.className).includes('disabled') }
  })()`)
  if (gate.err) { fail(gate.err) } else if (gate.disabled) {
    fail(`「保存成绩」仍是禁用态（${JSON.stringify(gate)}）—— picker 或分数的状态没生效到页面，**就此停手，没有写库**`)
    writeFileSync('teacher-write-ui-probe.txt', lines.join('\n') + '\n', 'utf8')
    cleanup(); process.exit(2)
  } else ok(`「保存成绩」按钮已启用（${JSON.stringify(gate.text)}）→ 参数齐备，可以写库`)

  await evaluate(`(() => {
    const btn = Array.from(document.querySelectorAll('.btn-primary')).find(b => b.innerText.includes('保存成绩'))
    if (btn) btn.click()
    return true
  })()`)

  const created = await waitFor(`(() => {
    const rows = Array.from(document.querySelectorAll('.srow'))
    return rows.some(r => r.innerText.includes(${JSON.stringify(STUDENT)}) && r.innerText.includes(${JSON.stringify(CREATE_REMARK)}))
  })()`, 20000)
  if (created) {
    ok('保存后列表里出现了这一行（页面自己重新拉了一次流水）')
    const row = await evaluate(`(() => {
      const r = Array.from(document.querySelectorAll('.srow'))
        .find(x => x.innerText.includes(${JSON.stringify(CREATE_REMARK)}))
      return r ? r.innerText.replace(/\\n/g, ' | ') : null
    })()`)
    info(`| ${row}`)
    if (String(row).includes(String(CREATE_SCORE))) ok(`行内分数显示为 ${CREATE_SCORE}`)
    else fail(`行内没看到分数 ${CREATE_SCORE}：${row}`)
    if (String(row).includes(SEMESTER.replace('第', '').replace('学期', '') + '秋') || String(row).includes('第8')) {
      // 学期标签是 scoreSemesterLabel 换算出来的（第8学期 → 某年某季），这里只确认它认得第8学期、没退化成「未标学期」
      ok(`行内学期标签没退化成「未标学期」`)
    } else if (String(row).includes('未标学期')) {
      fail('学期标签退化成「未标学期」—— scoreSemesterLabel 认不出第8学期')
    }
  } else {
    fail('保存后列表里没找到这一行（可能写接口失败，看是否有 toast 提示）')
  }

  // ---------- 第二步：改分 ----------
  say('')
  say('==== 改：把刚写进去那条改成 88 ====')
  const editOpened = await evaluate(`(() => {
    const r = Array.from(document.querySelectorAll('.srow'))
      .find(x => x.innerText.includes(${JSON.stringify(CREATE_REMARK)}))
    if (!r) return false
    r.click()
    return true
  })()`)
  if (!editOpened || !await waitFor(`!!document.querySelector('.form__danger')`, 8000)) {
    fail('改分 sheet 没打开')
  } else {
    // 作用域锁在「含 .form__danger 的那个 .form」上：录入 sheet 的表单可能还留在 DOM 里，
    // 两边都有 .form__input / .form__textarea，不锁根节点会填到另一个表单里去。
    const cur = await evaluate(`(() => {
      const form = document.querySelector('.form__danger')?.closest('.form')
      if (!form) return { err: 'not-found' }
      return {
        input: form.querySelector('.form__input input')?.value,
        remark: form.querySelector('.form__textarea textarea')?.value,
        facts: Array.from(form.querySelectorAll('.form__fact')).map(f => f.innerText.replace(/\\n/g, ': '))
      }
    })()`)
    info(`sheet 里读到的现值：总评=${cur.input} 评语=${JSON.stringify(cur.remark)}`)
    info(`详情：${(cur.facts || []).join(' / ')}`)
    if (String(cur.input) === String(CREATE_SCORE) && cur.remark === CREATE_REMARK) {
      ok('改分 sheet 回填的就是刚写进去的值（列表 → 详情的字段映射对上了）')
    } else {
      fail(`回填值不对，期望 ${CREATE_SCORE} / ${JSON.stringify(CREATE_REMARK)}`)
    }

    await evaluate(`(() => {
      const form = document.querySelector('.form__danger')?.closest('.form')
      if (!form) return false
      const setNative = (el, val) => {
        if (!el) return false
        const proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement : window.HTMLInputElement
        Object.getOwnPropertyDescriptor(proto.prototype, 'value').set.call(el, val)
        el.dispatchEvent(new Event('input', { bubbles: true }))
        el.dispatchEvent(new Event('change', { bubbles: true }))
        return true
      }
      setNative(form.querySelector('.form__input input'), '${UPDATE_SCORE}')
      setNative(form.querySelector('.form__textarea textarea'), ${JSON.stringify(UPDATE_REMARK)})
      return true
    })()`)
    await sleep(500)
    const saveGate = await evaluate(`(() => {
      const btn = Array.from(document.querySelectorAll('.btn-primary')).find(b => b.innerText.includes('保存修改'))
      return btn ? { disabled: String(btn.className).includes('disabled') } : { err: 'no-btn' }
    })()`)
    if (saveGate.disabled) fail('「保存修改」是禁用态，改分没有提交')
    else {
      ok('「保存修改」已启用 → 提交')
      await evaluate(`(() => {
        const btn = Array.from(document.querySelectorAll('.btn-primary')).find(b => b.innerText.includes('保存修改'))
        if (btn) btn.click()
        return true
      })()`)
      const updated = await waitFor(`(() => {
        const rows = Array.from(document.querySelectorAll('.srow'))
        return rows.some(r => r.innerText.includes(${JSON.stringify(UPDATE_REMARK)}))
      })()`, 20000)
      if (updated) {
        ok('列表里出现了改后的评语')
        const row = await evaluate(`(() => {
          const r = Array.from(document.querySelectorAll('.srow')).find(x => x.innerText.includes(${JSON.stringify(UPDATE_REMARK)}))
          return r ? r.innerText.replace(/\\n/g, ' | ') : null
        })()`)
        info(`| ${row}`)
        if (String(row).includes(String(UPDATE_SCORE))) ok(`分数已变成 ${UPDATE_SCORE}`)
        else fail(`分数没变成 ${UPDATE_SCORE}：${row}`)
      } else {
        fail('改分后列表里没有新评语（写接口可能失败了）')
      }
    }
  }

  // ---------- 第三步：删除 ----------
  say('')
  say('==== 删：把这条删掉（顺带把自己留下的痕迹清干净） ====')
  const delOpened = await evaluate(`(() => {
    const r = Array.from(document.querySelectorAll('.srow')).find(x => x.innerText.includes(${JSON.stringify(UPDATE_REMARK)}))
    if (!r) return false
    r.click()
    return true
  })()`)
  if (!delOpened || !await waitFor(`!!document.querySelector('.form__danger')`, 8000)) {
    fail('删除前没能打开那条的 sheet')
  } else {
    await evaluate(`document.querySelector('.form__danger').click()`)
    const modalUp = await waitFor(`!!document.querySelector('uni-modal') && document.querySelector('uni-modal').innerText.includes('删除')`, 6000)
    if (!modalUp) {
      fail('点「删除这条成绩记录」后没弹出二次确认框')
    } else {
      const modalText = await evaluate(`document.querySelector('uni-modal').innerText.replace(/\\n/g, ' | ')`)
      info(`确认框：${modalText}`)
      ok('删除有二次确认（危险操作该有的那道闸）')
      const confirmed = await evaluate(`(() => {
        const btns = Array.from(document.querySelectorAll('uni-modal .uni-modal__btn, uni-modal uni-view, uni-modal div'))
          .filter(el => el.innerText && el.innerText.trim() === '删除' && el.children.length === 0)
        if (!btns.length) return { err: '没找到「删除」按钮', all: document.querySelector('uni-modal').innerText }
        btns[btns.length - 1].click()
        return { clicked: true }
      })()`)
      if (confirmed.err) fail(`点确认失败：${confirmed.err} / ${JSON.stringify(confirmed.all)}`)
      else {
        const gone = await waitFor(`!(() => {
          const rows = Array.from(document.querySelectorAll('.srow'))
          return rows.some(r => r.innerText.includes(${JSON.stringify(UPDATE_REMARK)}))
        })()`, 20000)
        if (gone) ok('删除后这一行从列表里消失了')
        else fail('删除后列表里还有这一行')
      }
    }
  }

  // ---------- 收尾核对 ----------
  say('')
  say('==== 收尾：原有数据有没有被碰到 ====')
  const after = await evaluate(`(() => {
    const rows = Array.from(document.querySelectorAll('.srow'))
    return {
      count: rows.length,
      siming: rows.filter(r => r.innerText.includes('司明')).map(r => r.innerText.replace(/\\n/g, ' | ')),
      residue: rows.some(r => r.innerText.includes('验证-临时'))
    }
  })()`)
  say(`改库后的成绩流水：${after.count} 行；司明 ${after.siming.length} 行`)
  for (const s of after.siming) info(`| ${s}`)
  if (after.count === before.count && after.siming.join('~') === before.siming.join('~')) {
    ok(`行数与司明的三行都与改库前逐字相同（${before.count} 行）`)
  } else {
    fail(`与改库前不一致：${before.count} → ${after.count} 行`)
  }
  if (after.residue) fail('列表里还留着「验证-临时」的行，需要回滚')
  else ok('页面上没有留下「验证-临时」的残留行')

  if (logs.length) {
    say('')
    say(`页面 console.error/warning 共 ${logs.length} 条：`)
    for (const l of logs.slice(0, 20)) say(`  ! ${l}`)
  }

  say('')
  say(failures ? `==== FAIL：${failures} 项不通过 ====` : '==== PASS：增 + 改 + 删 三步都从界面上点通了 ====')
  say('提示：现在去跑 db_backups/teacher_ui_write_probe_20260929_rollback.sql，再跑 verify.sql 复核。')

  writeFileSync('teacher-write-ui-probe.txt', lines.join('\n') + '\n', 'utf8')
  console.log('已写出 teacher-write-ui-probe.txt')
  cleanup()
  process.exit(failures ? 1 : 0)
}

main().catch(e => { say(`FAIL 脚本异常: ${e.message}`); writeFileSync('teacher-write-ui-probe.txt', lines.join('\n') + '\n', 'utf8'); cleanup(); process.exit(1) })
