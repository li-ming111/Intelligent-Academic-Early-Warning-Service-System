/**
 * pages.json ↔ 实际页面文件 的一致性核对（P9 验收项之一）。
 *
 * 为什么要脚本化：uni-app 对"指向不存在的页面"不会报错，只会在运行时白屏；
 * 而这一版 pages.json 是从 70 条坏路径里收窄过来的，手工数条数不可靠。
 *
 * 用法：node scripts/check-pages.mjs      （在 academic-warning-xcx 目录下执行）
 * 退出码：0 = 完全一致；1 = 有缺失或有未登记的页面
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const srcDir = path.join(here, '..', 'src')
const pagesJson = path.join(srcDir, 'pages.json')

// JSON5 容错：pages.json 里现在没有注释/尾逗号，但保留了注释剥离以防将来加注释
const raw = fs.readFileSync(pagesJson, 'utf8')
const cfg = JSON.parse(raw.replace(/^\s*\/\/.*$/gm, ''))

const declared = []
for (const p of cfg.pages || []) declared.push(p.path)
for (const sp of cfg.subPackages || []) {
  for (const p of sp.pages || []) declared.push(`${sp.root}/${p.path}`)
}

/** 递归收集 src/pages 下所有 .vue（相对 src/ 的 posix 路径，去掉扩展名） */
function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.name.startsWith('.')) continue
    const full = path.join(dir, e.name)
    if (e.isDirectory()) walk(full, out)
    else if (e.name.endsWith('.vue')) {
      out.push(path.relative(srcDir, full).split(path.sep).join('/').replace(/\.vue$/, ''))
    }
  }
  return out
}

const actual = walk(path.join(srcDir, 'pages'))

const declaredSet = new Set(declared)
const actualSet = new Set(actual)

// tabBar 的 pagePath 也必须在 pages 里（first page 必须是 tabBar 页之一）
const tabBarPaths = ((cfg.tabBar && cfg.tabBar.list) || []).map(t => t.pagePath)

const missing = declared.filter(d => !actualSet.has(d))
// 已知的历史遗留：旧版本没登记的坏页，P9 会删；先报出来但不当作回归失败
const orphan = actual.filter(a => !declaredSet.has(a)).sort()

const dup = declared.filter((d, i) => declared.indexOf(d) !== i)

const lines = []
lines.push(`declared(pages.json 条目) = ${declared.length}`)
lines.push(`actual(src/pages/**/*.vue)  = ${actual.length}`)
lines.push(`tabBar 入口                = ${tabBarPaths.length}`)
lines.push('')
lines.push(`--- 声明了但文件不存在 (${missing.length}) ---`)
missing.forEach(m => lines.push('  ' + m))
lines.push('')
lines.push(`--- 文件存在但未声明 (${orphan.length}) ---`)
orphan.forEach(o => lines.push('  ' + o))
if (dup.length) {
  lines.push('')
  lines.push(`--- pages.json 里重复的路径 (${dup.length}) ---`)
  ;[...new Set(dup)].forEach(d => lines.push('  ' + d))
}
const badTab = tabBarPaths.filter(t => !declaredSet.has(t))
if (badTab.length) {
  lines.push('')
  lines.push(`--- tabBar 指向了未声明的页面 (${badTab.length}) ---`)
  badTab.forEach(t => lines.push('  ' + t))
}

console.log(lines.join('\n'))

const ok = missing.length === 0 && dup.length === 0 && badTab.length === 0
console.log('')
console.log(ok ? '==== PASS：没有悬空声明 ====' : '==== FAIL：pages.json 有悬空/重复声明 ====')
process.exit(ok ? 0 : 1)
