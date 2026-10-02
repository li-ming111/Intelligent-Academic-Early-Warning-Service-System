#!/usr/bin/env node
/**
 * 小程序端「预警状态三态」核对器（W1.6，2026-10-02）
 *
 * 为什么不能直接 `import` 那个文件：`utils/warning.js` 里有一句
 * `import { WARNING_STATUS_TEXT } from '@/constants/dict.js'`，`@/` 是 uni-app 的构建别名，
 * 裸 node 解析不了。
 *
 * 做法：把**真实源码**原样拷进临时目录，只把 `@/` 改成相对路径，其余一字不动，
 * 然后真跑。这样测的是仓库里那份文件，不是这里重写的一份副本。
 *
 * 跑法：`node testsprite/local-check/check-xcx-warning-status.mjs`
 * 期望输出最后一行 `全部通过 ✓`，退出码 0。
 *
 * ⚠️ 里面每一条断言都对应一个**曾经真的错过**的取值（status=2 被翻成"待处理"/
 * "已处理"、被算进 handled 桶、被涂成绿色）。别把断言当形式。
 */
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

const SRC = 'D:/ABS/academic-warning-xcx/src'
const STAGE = fs.mkdtempSync(path.join(os.tmpdir(), 'xcx-warning-status-'))

/** 这几个 util 相互之间没有 import，dict.js 也没有 import —— 所以只有一处别名要换。 */
const FILES = [
  ['utils/score.js', 'score.js'],
  ['utils/format.js', 'format.js'],
  ['utils/response.js', 'response.js'],
  ['constants/dict.js', 'dict.js'],
  ['utils/warning.js', 'warning.js']
]

for (const [from, to] of FILES) {
  const src = path.join(SRC, from)
  if (!fs.existsSync(src)) {
    console.error(`✗ 源文件不存在：${src}`)
    process.exit(1)
  }
  let text = fs.readFileSync(src, 'utf8')
  if (to === 'warning.js') {
    const before = text
    text = text.replace("from '@/constants/dict.js'", "from './dict.js'")
    if (text === before) {
      console.error("✗ warning.js 里没找到 `from '@/constants/dict.js'` —— 别名写法变了，核对器要跟着改")
      process.exit(1)
    }
  }
  fs.writeFileSync(path.join(STAGE, to), text)
}
fs.writeFileSync(path.join(STAGE, 'package.json'), '{"type":"module"}\n')

const {
  WARNING_STATUS,
  warningStatus,
  warningStatusText,
  warningStatusKey,
  warningStatusTagClass,
  normalizeWarning,
  normalizeWarnings,
  countWarnings
} = await import(pathToFileURL(path.join(STAGE, 'warning.js')).href)

let pass = 0
const fails = []
function eq(actual, expected, label) {
  const a = JSON.stringify(actual)
  const e = JSON.stringify(expected)
  if (a === e) { pass++; return }
  fails.push(`${label}\n      期望 ${e}\n      实际 ${a}`)
}

// ---------- 1. 数字分支三态 ----------
eq(warningStatus(0), 0, 'warningStatus(0)')
eq(warningStatus(1), 1, 'warningStatus(1)')
eq(warningStatus(2), 2, 'warningStatus(2) —— 扩三态前这里恒为 0')

// ---------- 2. 数字字符串 ----------
eq(warningStatus('0'), 0, "warningStatus('0')")
eq(warningStatus('1'), 1, "warningStatus('1')")
eq(warningStatus('2'), 2, "warningStatus('2')")

// ---------- 3. 中文文案（teacher-service 那条路回的就是中文）----------
eq(warningStatus('待处理'), 0, '中文「待处理」')
eq(warningStatus('已处理'), 1, '中文「已处理」')
eq(warningStatus('已解除'), 2, '中文「已解除」—— 若先判 "已" 会被吃成 1')

// ---------- 4. 文案 / 样式键 / CSS 类 ----------
eq(warningStatusText(2), '已解除', 'warningStatusText(2)')
eq(warningStatusKey(2), 'dispelled', 'warningStatusKey(2)')
eq(warningStatusKey(1), 'handled', 'warningStatusKey(1)')
eq(warningStatusKey(0), 'pending', 'warningStatusKey(0)')
eq(warningStatusTagClass(0), 'tag--warning', '标签类 pending')
eq(warningStatusTagClass(1), 'tag--success', '标签类 handled')
eq(warningStatusTagClass(2), 'tag--info', '标签类 dispelled —— 不能与 handled 同色')

// ---------- 5. 认不出的值回落待处理（宁可多显示，别让预警凭空消失）----------
eq(warningStatus(null), 0, 'null → 待处理')
eq(warningStatus(undefined), 0, 'undefined → 待处理')
eq(warningStatus(''), 0, '空串 → 待处理')
eq(warningStatus(99), 0, '99 → 待处理')
eq(warningStatus(-1), 0, '-1 → 待处理')

// ---------- 6. normalizeWarning 的三个判定字段 ----------
const w2 = normalizeWarning({ id: 7, status: 2, warningLevel: 3, studentId: 4 })
eq(w2.status, 2, 'normalize: status')
eq(w2.isPending, false, 'normalize: isPending(status=2) 必须 false')
eq(w2.isHandled, false, 'normalize: isHandled(status=2) 必须 false')
eq(w2.isDispelled, true, 'normalize: isDispelled(status=2) 必须 true')
eq(w2.statusText, '已解除', 'normalize: statusText')
eq(w2.statusKey, 'dispelled', 'normalize: statusKey')
eq(w2.warningLevel, 3, 'normalize: 原字段 warningLevel 必须保留（模板还在用）')
eq(w2.studentId, 4, 'normalize: 原字段 studentId 必须保留')

// ---------- 7. countWarnings 三桶相加 = total ----------
const list = [
  { id: 1, status: 0, warningLevel: 1 },
  { id: 2, status: 0, warningLevel: 3 },
  { id: 3, status: 1, warningLevel: 2 },
  { id: 4, status: 2, warningLevel: 1 },
  { id: 5, status: 2, warningLevel: 3 },
  { id: 6, status: '已解除', warningLevel: 2 } // 中文分支也要进 dispelled 桶
]
const c = countWarnings(list)
eq(c.total, 6, 'countWarnings.total')
eq(c.pending, 2, 'countWarnings.pending')
eq(c.handled, 1, 'countWarnings.handled —— 扩三态前这里是 3（把两条已解除并进来了）')
eq(c.dispelled, 3, 'countWarnings.dispelled —— 扩三态前这个键根本不存在')
eq(c.pending + c.handled + c.dispelled, c.total, '三桶之和 === total（页面上那个等式的依据）')
eq(c.level1, 2, 'countWarnings.level1')
eq(c.level2, 2, 'countWarnings.level2')
eq(c.level3, 2, 'countWarnings.level3')
eq(c.severe, 4, 'countWarnings.severe（level>=2）')

// ---------- 8. normalizeWarnings 吃分页载荷 ----------
eq(normalizeWarnings({ list }).length, 6, 'normalizeWarnings({list})')
eq(normalizeWarnings({ records: list }).length, 6, 'normalizeWarnings({records})')
eq(normalizeWarnings(null).length, 0, 'normalizeWarnings(null)')

// ---------- 9. 常量本身 ----------
eq(WARNING_STATUS, { PENDING: 0, HANDLED: 1, DISPELLED: 2 }, 'WARNING_STATUS 三态')

fs.rmSync(STAGE, { recursive: true, force: true })

console.log(`\n通过 ${pass} 条，失败 ${fails.length} 条`)
if (fails.length) {
  console.log('\n失败明细：')
  for (const f of fails) console.log('  ✗ ' + f)
  process.exit(1)
}
console.log('全部通过 ✓')
