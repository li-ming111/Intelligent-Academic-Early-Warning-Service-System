// 只做 SFC 语法 / 模板编译检查，**不打包**（内存占用极小，本机空闲内存 <500MB 时也能跑）。
// 用法: node testsprite/local-check/check-sfc.mjs <file.vue> [file2.vue ...]
//
// ⚠️ 它能查什么、不能查什么:
//   能: <script> 是合法 JS、<template> 能编译。
//   不能: 模板引用了 script 里**不存在**的名字 —— Vue 对此不报错(编译成 _ctx.xxx = undefined),
//         这个脚本也会判 OK。那类缺陷要用 check-template-bindings.mjs。
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

// 从本脚本位置向上找到前端工程（不写死 D:/ABS，换机器也能跑）
const here = dirname(fileURLToPath(import.meta.url))
const require = createRequire(pathToFileURL(resolve(here, '../../academic-warning-frontend/package.json')))
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')

let bad = 0
for (const file of process.argv.slice(2)) {
  const src = readFileSync(file, 'utf8')
  const { descriptor, errors } = parse(src, { filename: file })
  if (errors.length) {
    bad++
    console.log('PARSE FAIL ' + file)
    for (const e of errors) console.log('   ' + e.message)
    continue
  }
  try {
    if (descriptor.scriptSetup || descriptor.script) {
      compileScript(descriptor, { id: file })
    }
    const tpl = compileTemplate({
      source: descriptor.template.content,
      filename: file,
      id: file,
    })
    if (tpl.errors.length) {
      bad++
      console.log('TEMPLATE FAIL ' + file)
      for (const e of tpl.errors) console.log('   ' + (e.message || e))
      continue
    }
    console.log('OK ' + file)
  } catch (e) {
    bad++
    console.log('SCRIPT FAIL ' + file + '\n   ' + e.message)
  }
}
process.exit(bad ? 1 : 0)
