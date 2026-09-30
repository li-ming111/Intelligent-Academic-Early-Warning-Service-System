// 查「<style> 块里用了 SCSS 语法，却没写 lang="scss"」—— 一个**构建期不会报错**的坑。
//
// 为什么会专门写个脚本查它（2026-09-29 实际踩到）：
//   pages/auth/login.vue 与 register.vue 的 <style> 一直没写 lang="scss"，
//   但原先里面只有十六进制字面量，所以看不出问题。
//   把颜色换成 $brand-auth / $color-bg 这类 SCSS 变量之后：
//     · npm run build:h5 / build:mp-weixin —— **照过，0 错误**
//     · 五端逐页冒烟 —— **照过**（判的是"有真数据、没 console.error"）
//     · 浏览器里 —— 登录页整片变白：Sass 没参与，`background: $brand-auth`
//       被当成非法值**静默丢弃**，白底 + 白色标题 = 品牌名和登录按钮都看不见。
//   也就是说：编译门、数据门都拦不住它，只有像素能。那就把像素这一层的代价
//   换成一次静态检查 —— 这条规则是纯字符串匹配，没有误判空间。
//
// 用法: node scripts/check-style-lang.mjs
// 退出码: 0 干净 / 1 有命中
import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = fileURLToPath(new URL('../src', import.meta.url))

const files = []
;(function walk(d) {
  for (const e of readdirSync(d, { withFileTypes: true })) {
    const p = join(d, e.name)
    if (e.isDirectory()) walk(p)
    else if (e.name.endsWith('.vue')) files.push(p)
  }
})(ROOT)

/** 去掉注释，免得把解释性文字里的 `$brand-auth` 当成真实引用。 */
const stripComments = css => css.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

/** SCSS 专有语法：变量引用、@include/@extend/@mixin/@function、嵌套 &。 */
const SCSS_ONLY = [
  [/\$[A-Za-z_][\w-]*/, 'SCSS 变量 $x'],
  [/@(include|extend|mixin|function|use|forward)\b/, '@include/@extend 等 SCSS 指令'],
  [/&#?[\w-]/, '嵌套 & 选择器']
]

const bad = []
for (const f of files) {
  const s = readFileSync(f, 'utf8')
  const rel = relative(ROOT, f).replace(/\\/g, '/')
  // 逐个 <style ...> ... </style>
  for (const m of s.matchAll(/<style([^>]*)>([\s\S]*?)<\/style>/g)) {
    const attrs = m[1]
    const body = stripComments(m[2])
    const isScss = /lang\s*=\s*["'](scss|sass)["']/.test(attrs)
    if (isScss) continue
    for (const [re, label] of SCSS_ONLY) {
      if (re.test(body)) {
        bad.push({ rel, label, line: s.slice(0, m.index).split('\n').length })
        break
      }
    }
  }
}

if (!bad.length) {
  console.log(`✓ ${files.length} 个 .vue 全部干净：没有「用了 SCSS 语法却没声明 lang="scss"」的 <style> 块`)
  process.exit(0)
}
console.log(`✗ 命中 ${bad.length} 处：`)
for (const b of bad) {
  console.log(`   src/${b.rel}:${b.line}  含 ${b.label}，但 <style> 没有 lang="scss"`)
  console.log(`      → Sass 不会参与，这类声明的值会被浏览器**静默丢弃**，构建与冒烟都发现不了`)
}
process.exit(1)
