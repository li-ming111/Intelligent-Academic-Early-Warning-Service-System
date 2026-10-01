// 交叉检查：<template> 里用到的标识符，是否在 <script setup> 里**有定义**。
// 用法: node testsprite/local-check/check-template-bindings.mjs <file.vue> [...]   或
//       node testsprite/local-check/check-template-bindings.mjs $(ls academic-warning-frontend/src/views/*.vue)
//
// 为什么需要它：Vue 不会因为"模板引用了 script 里不存在的名字"而报错 ——
// 它会安静地编译成 _ctx.classes（运行时 undefined），页面表现为
// "下拉框永远空"，控制台干净。`vite build` 也照样成功（已实测）。
// 本仓已出现过这条链（页面按裸数组读 → 永远空），所以要用静态交叉核对兜住。
//
// 判定规则（比"脚本文本里出现过这个词"强得多 —— **那个写法是假阴性机器**：
// 拿一份"只在 script 里把 classes 改名、模板照旧引用"的坏文件喂它，它判 PASS）：
//   模板标识符必须能对上 script 里的 `const|let|var|function|class NAME`（含一行多声明
//   `const a = ref(), b = ref()`）、`import ... NAME`、`defineProps/defineEmits` 的键、
//   或模板内的局部名（v-for 的循环变量、`#default="{ row }"` 作用域插槽的解构、箭头函数形参）。
//   模板一侧只取插值与属性表达式里的标识符，并剥掉对象字面量的键（`{ active: ... }`）与字符串。
import { readFileSync } from 'node:fs'

const GLOBALS = new Set([
  'true', 'false', 'null', 'undefined', 'NaN', 'Infinity',
  'if', 'else', 'in', 'of', 'return', 'typeof', 'new', 'void', 'this',
  'Math', 'String', 'Number', 'Boolean', 'Object', 'Array', 'JSON', 'Date', 'RegExp',
  'parseInt', 'parseFloat', 'isNaN', 'console', 'window', 'document', 'setTimeout', 'setInterval',
  'uni', 'wx', '$event', '$refs', '$slots', '$attrs', 'arguments',
  // 模板里常见、由 Vue 注入的
  'key', 'ref', 'class', 'style', 'slot', 'is',
])

/** 按顶层逗号切分（忽略括号/方括号/花括号里的逗号与字符串里的逗号）。*/
function splitTopLevel(s) {
  const out = []
  let depth = 0, quote = null, cur = ''
  for (const ch of s) {
    if (quote) {
      cur += ch
      if (ch === quote) quote = null
      continue
    }
    if (ch === "'" || ch === '"' || ch === '`') { quote = ch; cur += ch; continue }
    if (ch === '(' || ch === '[' || ch === '{') depth++
    if (ch === ')' || ch === ']' || ch === '}') depth--
    if (ch === ',' && depth === 0) { out.push(cur); cur = ''; continue }
    cur += ch
  }
  out.push(cur)
  return out
}

let bad = 0
for (const file of process.argv.slice(2)) {
  const src = readFileSync(file, 'utf8')
  const tplMatch = src.match(/<template>([\s\S]*)<\/template>/)
  const scriptMatch = src.match(/<script[\s\S]*?>([\s\S]*?)<\/script>/)
  if (!tplMatch || !scriptMatch) { console.log('SKIP ' + file); continue }
  const tpl = tplMatch[1]
  const script = scriptMatch[1]

  // ---- script 侧：所有"被定义的名字" ----
  const declared = new Set()
  for (const m of script.matchAll(/\b(?:function|class)\s+([A-Za-z_$][\w$]*)/g)) declared.add(m[1])
  // const/let/var 支持"一行多个声明"：`const loading = ref(false), errorMessage = ref('')`
  for (const m of script.matchAll(/\b(?:const|let|var)\s+([^\n;]*)/g)) {
    for (const part of splitTopLevel(m[1])) {
      const name = (part.split('=')[0] || '').trim().replace(/^[[{]/, '').trim()
      if (/^[A-Za-z_$][\w$]*$/.test(name)) declared.add(name)
    }
  }
  for (const m of script.matchAll(/import\s+([A-Za-z_$][\w$]*)\s*(?:,|from)/g)) declared.add(m[1])
  for (const m of script.matchAll(/import\s*\{([^}]*)\}/g)) {
    for (const part of m[1].split(',')) {
      const name = part.trim().split(/\s+as\s+/).pop().trim()
      if (name) declared.add(name)
    }
  }
  // 解构：const { a, b } = ... / const [a, b] = ...
  for (const m of script.matchAll(/\b(?:const|let|var)\s*[[{]([^\]}]*)[\]}]\s*=/g)) {
    for (const part of m[1].split(',')) {
      const name = part.trim().replace(/^[.\s]*/, '').split(/[:\s]/).pop()
      if (name) declared.add(name)
    }
  }

  // ---- 模板侧：表达式里出现的名字 + 模板局部名（v-for / 插槽作用域） ----
  const exprs = []
  for (const m of tpl.matchAll(/\{\{([\s\S]*?)\}\}/g)) exprs.push(m[1])
  for (const m of tpl.matchAll(/\s(?::|v-bind:|@|v-on:|v-if|v-else-if|v-else|v-for|v-model|v-show|#|v-slot)[\w.:-]*="([^"]*)"/g)) exprs.push(m[1])

  // 作用域插槽里的解构名（`#default="{ row }"`）是**声明**，不是引用：
  // 待办列表页几十个文件都这么写，不认它就会刷出一片假红。
  for (const m of tpl.matchAll(/(?:#|v-slot)[\w.:-]*="([^"]*)"/g)) {
    for (const n of m[1].matchAll(/(?<!\.)\b([A-Za-z_$][\w$]*)\b/g)) declared.add(n[1])
  }
  // defineProps/defineEmits 的**键**在模板里按名字直接可用
  for (const m of script.matchAll(/define(?:Props|Emits)\s*\(\s*\{([\s\S]*?)\}\s*\)/g)) {
    for (const n of m[1].matchAll(/(?:^|[,{\s])([A-Za-z_$][\w$]*)\s*:/g)) declared.add(n[1])
  }

  const used = new Set()
  for (const e of exprs) {
    // 模板内的**局部**名字先声明掉，否则会被当成"script 里没有"的假红：
    //   v-for 的循环变量：v-for="(a, b) in list" / v-for="{ id } in list"
    //   v-for 的迭代源：v-for="c in classes" 里的 classes 是使用，不是声明，故只取 in 左边
    for (const m of e.matchAll(/\(?([^()]*?)\)?\s+(?:in|of)\s+/g)) {
      for (const part of m[1].split(',')) {
        const name = part.trim().replace(/[{}]/g, '').split(/[:\s]/).pop().trim()
        if (/^[A-Za-z_$][\w$]*$/.test(name)) declared.add(name)
      }
    }
    // 箭头函数形参：@change="(e) => ..." / "row => ..."
    for (const m of e.matchAll(/(?:\(([^()]*)\)|([A-Za-z_$][\w$]*))\s*=>/g)) {
      for (const part of (m[1] || m[2] || '').split(',')) {
        const name = part.trim()
        if (/^[A-Za-z_$][\w$]*$/.test(name)) declared.add(name)
      }
    }
    let cleaned = e.replace(/'[^']*'/g, ' ').replace(/"[^"]*"/g, ' ').replace(/`[^`]*`/g, ' ')
    // 对象字面量的键（`{ active: ... }` / `, placeholder: ...`）不是标识符引用
    cleaned = cleaned.replace(/([{,]\s*)([A-Za-z_$][\w$]*)\s*:/g, '$1')
    for (const m of cleaned.matchAll(/(?<!\.)(?<!\$)\b([A-Za-z_$][\w$]*)\b/g)) used.add(m[1])
  }

  const unknown = [...used].filter((n) => !GLOBALS.has(n) && !declared.has(n))
  if (unknown.length) {
    bad++
    console.log('MISSING IN SCRIPT ' + file)
    console.log('   ' + unknown.join(', '))
  } else {
    console.log('OK ' + file + '  (' + used.size + ' 个模板标识符全部有定义)')
  }
}
process.exit(bad ? 1 : 0)
