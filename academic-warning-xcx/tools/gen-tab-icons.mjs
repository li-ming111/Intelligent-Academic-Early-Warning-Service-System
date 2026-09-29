// 生成原生 tabBar 的图标 PNG。
//
// 背景：`pages.json` 的 tabBar 是**原生**的，iconPath 只吃图片文件（png/jpg），
// 吃不了 `app-icon` 用的 SVG data URI。而工程里原有的 8 个 tab 图标是上一轮
// 生成脚本留下的占位图 —— 内容是灰底圆圈里一个字母（H/E/W/P），跟业务无关。
//
// 这里把 `constants/icons.js` 里那套 24x24 描边路径光栅化成 81x81 PNG，
// 与 app-icon 共用同一份几何，所以图标长得跟页面内的一模一样。
//
// 为什么用 Chrome 而不是装个 sharp/svg2png：光栅化需要完整的 SVG 渲染器
// （圆角矩形、圆弧、linecap 都得对），为一共 8 个图标引一个带原生依赖的包
// 不划算。本机已有 Chrome，用 CDP 截一张 81x81 的透明底图即可。
//
// 用法:
//   node tools/gen-tab-icons.mjs            # 写入 src/static/
//   node tools/gen-tab-icons.mjs --out /tmp # 换个输出目录
//
// 改了 icons.js 里的几何或下面的配色后重跑一次即可；这是纯静态资源，
// 不进构建流程（构建时跑 Chrome 会让 CI 依赖浏览器）。
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync, mkdirSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { iconSvg } from '../src/constants/icons.js'

const HERE = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = (() => {
  const i = process.argv.indexOf('--out')
  return i > -1 && process.argv[i + 1] ? resolve(process.argv[i + 1]) : join(HERE, '..', 'src', 'static')
})()

/** 四个学生 tab。icon 字段是 constants/icons.js 里的键。 */
const TABS = [
  { file: 'tab-home', icon: 'home' },
  { file: 'tab-edu', icon: 'chart' },
  { file: 'tab-warning', icon: 'alert' },
  { file: 'tab-help', icon: 'user' }
]

/**
 * 未选中 / 选中两态。选中色必须与 `pages.json` 的 `tabBar.selectedColor`
 * 一致，否则底栏会出现"图标绿了文字还没绿"的错位。
 */
const STATES = [
  { suffix: '', color: '#8E99A8' },
  { suffix: '-active', color: '#4CAF50' }
]

/** 微信官方建议 81x81。 */
const SIZE = 81

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const PORT = 9633

const sleep = ms => new Promise(r => setTimeout(r, ms))

const build = () => TABS.flatMap(t => STATES.map(s => ({
  name: `${t.file}${s.suffix}.png`,
  svg: iconSvg(t.icon, s.color)
})))

const main = async () => {
  const jobs = build()
  const missing = jobs.filter(j => !j.svg).map(j => j.name)
  if (missing.length) {
    console.error(`图标名写错了，icons.js 里找不到：${missing.join(', ')}`)
    process.exit(1)
  }

  const profile = mkdtempSync(join(tmpdir(), 'cdpicon-'))
  const chrome = spawn(CHROME, [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank'
  ], { stdio: 'ignore' })

  const cleanup = () => {
    try { chrome.kill() } catch {}
    try { rmSync(profile, { recursive: true, force: true }) } catch {}
  }

  try {
    let target = null
    for (let i = 0; i < 60; i++) {
      try {
        const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
        target = list.find(t => t.type === 'page')
        if (target) break
      } catch {}
      await sleep(250)
    }
    if (!target) throw new Error('Chrome 调试端口未就绪')

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

    await send('Page.enable')
    // 透明底：不设置的话截出来是白底，底栏会得到 8 个白方块。
    await send('Emulation.setDefaultBackgroundColorOverride', { color: { r: 0, g: 0, b: 0, a: 0 } })
    await send('Emulation.setDeviceMetricsOverride', {
      width: SIZE, height: SIZE, deviceScaleFactor: 1, mobile: false
    })

    mkdirSync(OUT_DIR, { recursive: true })

    let written = 0
    for (const job of jobs) {
      const html =
        '<!doctype html><meta charset="utf-8">' +
        '<style>html,body{margin:0;padding:0;background:transparent;overflow:hidden}' +
        'svg{display:block;width:' + SIZE + 'px;height:' + SIZE + 'px}</style>' + job.svg
      await send('Page.navigate', { url: 'data:text/html,' + encodeURIComponent(html) })
      // 等 SVG 真正画上去。data: URL 没有网络请求可等，只能给一帧的时间。
      await sleep(450)

      const shot = await send('Page.captureScreenshot', { format: 'png' })
      const data = shot.result?.data
      if (!data) throw new Error(`${job.name} 截图失败：CDP 未返回数据`)
      const buf = Buffer.from(data, 'base64')
      // 落盘前先自检：384 字节以下基本就是一张空图，写了等于没写。
      if (buf.length < 384) throw new Error(`${job.name} 只有 ${buf.length} 字节，像是空白图`)
      writeFileSync(join(OUT_DIR, job.name), buf)
      console.log(`  ${job.name}  ${buf.length} 字节`)
      written++
    }

    console.log(`\n已写入 ${written} 个图标 -> ${OUT_DIR}`)
  } finally {
    cleanup()
  }
}

main().catch(e => { console.error('生成失败:', e.message); process.exit(1) })
