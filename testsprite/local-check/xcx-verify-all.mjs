// 小程序 H5 通道**三层走查一条命令跑完**，逐层汇总。
//
// 三层各补一截，缺一层就有盲区：
//   ① xcx-page-smoke.mjs    —— 逐页 `location.hash` 进去看**默认态**（49 条路径）
//   ② xcx-tab-walk.mjs      —— 逐 tab 切，补上"每页只看了 1/N 个面板"（30 个 tab）
//   ③ xcx-filter-walk.mjs   —— 逐筛选值点，补上"每页只看了一个筛选值"（77 个值）
//
// 每一层都自己写一份 `<层>-<角色>.txt`，本脚本只做**调度 + 汇总**：
// 解析各 txt 末尾的 `==== … ====` 行，拼成一张表。判定逻辑一行都不在这里，
// 免得"汇总脚本自己骗自己"。
//
// 前置：H5 dev server 在 127.0.0.1:5180（`npm run dev:h5`），后端五服务已起。
//
// 用法：
//   node xcx-verify-all.mjs            # 全部三层
//   node xcx-verify-all.mjs tab filter # 只跑指定层
import { spawn } from 'node:child_process'
import { copyFileSync, existsSync, readFileSync } from 'node:fs'
import os from 'node:os'

const BASE = process.env.XCX_BASE || 'http://127.0.0.1:5180'

/** 三层：脚本、产物前缀、覆盖的角色（guest 只有注册页免登录，仅 tab 层有）。 */
const LAYERS = {
  smoke: {
    script: 'xcx-page-smoke.mjs',
    out: r => `xcx-pages-${r}.txt`,
    unit: '页',
    roles: ['student', 'teacher', 'counselor', 'admin', 'college-admin']
  },
  tab: {
    script: 'xcx-tab-walk.mjs',
    out: r => `xcx-tabs-${r}.txt`,
    unit: 'tab',
    roles: ['student', 'teacher', 'counselor', 'admin', 'college-admin', 'guest']
  },
  filter: {
    script: 'xcx-filter-walk.mjs',
    out: r => `xcx-filters-${r}.txt`,
    unit: '筛选值',
    roles: ['student', 'teacher', 'counselor', 'admin', 'college-admin']
  }
}

const wanted = process.argv.slice(2).filter(a => !a.startsWith('--'))
const picked = wanted.length ? wanted : Object.keys(LAYERS)
for (const k of picked) {
  if (!LAYERS[k]) {
    console.error(`层名必须是 ${Object.keys(LAYERS).join(' | ')} 之一，收到: ${k}`)
    process.exit(2)
  }
}

const run = (cmd, args) => new Promise(res => {
  const p = spawn(cmd, args, { stdio: ['ignore', 'pipe', 'pipe'], shell: false })
  let out = ''
  p.stdout.on('data', d => { out += d })
  p.stderr.on('data', d => { out += d })
  p.on('close', code => res({ code, out }))
})

/**
 * 机器**内存饿死**的指纹。
 *
 * 踩过的坑：一次跑批时机器只剩 135 MB 空闲（页面文件峰值 10.6 GB），
 * vite dev server 活得好好的（curl 200），但 headless Chrome 拉不到动态模块，
 * 4 个页面报 `Failed to fetch dynamically imported module` —— 于是走查器
 * 报"4 页未通过、4 页有 console.error"。同一支脚本、同一份代码，
 * 等内存松了重跑就是 11/11 / 0 错误。
 *
 * 所以"页面挂了"和"机器没内存了"必须分开报：混为一谈会让人去查根本不存在的回归。
 */
const STARVED = /Failed to fetch dynamically imported module|ERR_INSUFFICIENT_RESOURCES|Out of memory|JS heap out of memory/

/**
 * 空闲物理内存（MB）。**用 Node 内置的 os.freemem()，不起子进程** ——
 * 早先版本 spawn 一个 powershell 去查，而机器正是紧到 Bash 都 `fork: Resource
 * temporarily unavailable` 的时候，子进程起不来 → 返回 null → 预检在最该生效的
 * 场景下静默失效。内置调用不依赖 fork。
 */
function freeMemMB() {
  try {
    const n = os.freemem() / (1024 * 1024)
    return Number.isFinite(n) ? Math.round(n) : null
  } catch { return null }
}

/** 从产物里抠出末行的汇总：`==== <role>: 12/12 个筛选值通过，0 项未通过，… ====` */
function summarize(file) {
  if (!existsSync(file)) return { line: '(没有产物文件)', err: null }
  const text = readFileSync(file, 'utf8')
  const m = [...text.matchAll(/^==== .* ====$/gm)].pop()
  if (!m) return { line: '(产物里没有汇总行)', err: null }
  // 三层的量词不同：smoke「N 页有」/ tab「N 个有」/ filter「N 项有」
  const err = /(\d+)\s*(?:项|页|个)有 console\.error/.exec(m[0])
  return { line: m[0].replace(/^==== | ====$/g, ''), err: err ? Number(err[1]) : null }
}

const main = async () => {
  // 前置检查：dev server 不通的话三层都会以"登录没落到首页"失败，报错很难读
  let up = false
  try { up = (await fetch(BASE, { signal: AbortSignal.timeout(4000) })).ok } catch {}
  console.log(`H5 通道 ${BASE} : ${up ? '可达' : '不可达'}`)
  if (!up) {
    console.log('先起它：cd D:\\ABS\\academic-warning-xcx && npm run dev:h5')
    process.exit(1)
  }

  // 内存余量预警：一台 16 GB 的机器上跑 headless Chrome（每个实例数个渲染进程）
  // 加 13 个 Spring 服务，余量不足时页面会以"资源不足"的方式假失败。
  const free = freeMemMB()
  if (free !== null && free < 500) {
    console.log(`⚠ 空闲物理内存只有 ${free} MB —— 跑了也会假失败（见 STARVED 注释）。`)
    console.log('  先关掉占内存的程序，或分批跑：node xcx-verify-all.mjs smoke')
  } else if (free !== null) {
    console.log(`空闲物理内存 ${free} MB`)
  }

  const table = []
  let anyFail = false
  let starvedRoles = []

  for (const layer of picked) {
    const L = LAYERS[layer]
    console.log(`\n############ ${layer}  (${L.script}) ############`)
    for (const role of L.roles) {
      const file = L.out(role)
      // 跑之前把上一次的产物留一份 —— 饿死的那一跑会把好证据盖掉，
      // 而"上次全绿的产物"本身就是答辩要用的东西，不该被一次假失败抹掉
      const bak = existsSync(file) ? file + '.prev' : null
      if (bak) copyFileSync(file, bak)

      const t0 = Date.now()
      const { code, out } = await run(process.execPath, [L.script, role])
      const secs = ((Date.now() - t0) / 1000).toFixed(0)
      const starved = STARVED.test(out)
      if (code !== 0) anyFail = true
      if (starved) starvedRoles.push(`${layer}/${role}`)

      // 饿死的那一跑不算数：产物回滚成上一次的，免得把假失败当结论
      let note = ''
      if (starved && bak) { copyFileSync(bak, file); note = '  (产物已回滚成上一版)' }
      const s = summarize(starved && bak ? bak : file)

      const tail = out.trim().split('\n').filter(Boolean).pop() || ''
      const tag = starved ? 'ENV ' : (code === 0 ? 'OK  ' : 'FAIL')
      const why = code === 0 ? '' : `  ← exit=${code}${tail.length < 90 ? ' | ' + tail : ''}`
      console.log(`  ${tag} ${role.padEnd(14)} ${String(secs).padStart(4)}s  ${s.line}${why}${note}`)
      table.push({ layer, role, code, secs: Number(secs), line: s.line, consoleErr: s.err, starved })
    }
  }

  if (starvedRoles.length) {
    console.log(`\n⚠ ${starvedRoles.length} 个角色是**机器内存不足**跑挂的，不是页面缺陷：`)
    console.log(`  ${starvedRoles.join(', ')}`)
    console.log('  这几种角色的产物已回滚成上一版。等内存松了单独重跑，例如：')
    console.log(`  node xcx-page-smoke.mjs student`)
  }

  console.log('\n================ 汇总 ================')
  const byLayer = {}
  for (const t of table) {
    byLayer[t.layer] ??= { pass: 0, total: 0, err: 0, secs: 0, starved: 0 }
    const b = byLayer[t.layer]
    b.secs += t.secs
    // 饿死的那一跑不进任何合计 —— 它证明的是机器状态，不是页面状态
    if (t.starved) { b.starved += 1; continue }
    if (t.consoleErr) b.err += t.consoleErr
    // 「12/12 个筛选值通过」→ 12 / 12
    const m = /(\d+)\/(\d+)/.exec(t.line)
    if (m) { b.pass += Number(m[1]); b.total += Number(m[2]) }
  }
  for (const layer of picked) {
    const b = byLayer[layer]
    console.log(`${layer.padEnd(7)} ${b.pass}/${b.total} ${LAYERS[layer].unit}通过` +
      `   用时 ${b.secs}s   console.error ${b.err} 条` +
      (b.starved ? `   ⚠ ${b.starved} 个角色内存饿死未计入` : '') +
      (b.pass === b.total && b.err === 0 ? '' : '   ← 有未通过项'))
  }
  const allPass = Object.values(byLayer).reduce((a, b) => a + b.pass, 0)
  const allTotal = Object.values(byLayer).reduce((a, b) => a + b.total, 0)
  const allErr = Object.values(byLayer).reduce((a, b) => a + b.err, 0)
  console.log(`总计     ${allPass}/${allTotal} 通过，console.error ${allErr} 条`)

  // 诚实边界：三层都没覆盖的地方，写在这里免得汇总表看起来像"全覆盖"
  console.log(`
覆盖不到的（三层共同的边界，别当成已验证）：
  · 非默认 tab / 非默认筛选值**里面**的那些控件（如学院管理员成绩页「成绩查询」
    面板里的班级筛选）—— tab 层只证到该 tab 打开了、有数据，没逐值走查它的筛选。
  · 表单内部的单选组（chip--on / opt--on）是**选值**不是导航，不在走查范围。
  · 登录页只作为前置条件被使用（注册页由 tab 层的 guest 覆盖）。
  · 写操作（提交/审批/处理/改分）一律**没有执行** —— 三层都是只读走查。`)

  // 产物清单（答辩时要拿的原始证据）
  console.log('\n产物：')
  for (const layer of picked) {
    for (const role of LAYERS[layer].roles) {
      const f = LAYERS[layer].out(role)
      console.log(`  ${existsSync(f) ? '有' : '缺'} ${f}`)
    }
  }

  process.exit(anyFail ? 1 : 0)
}

main().catch(e => { console.error('FAIL', e); process.exit(1) })
