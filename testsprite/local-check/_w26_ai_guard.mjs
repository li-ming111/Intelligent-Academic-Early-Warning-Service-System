// W26：给 AI 诚实性闸打靶。
//
// ⚠️ 直接 import **真文件**（不是把逻辑抄一遍）—— 抄一遍测的是副本，不是被测代码。
//    本仓栽过"判据比实现旧一版 ⇒ 假阴性"，也栽过"判据比预期宽/窄"，所以两侧都打。
import { aiGuard, isAiPlaceholder, findAiPlaceholder, AI_UNCONFIGURED_MESSAGE }
  from '../../academic-warning-frontend/src/utils/ai.js'

let pass = 0, fail = 0
function check(label, got, expect) {
  const ok = got === expect
  ok ? pass++ : fail++
  console.log(`  [${ok ? 'OK ' : 'BAD'}] ${label.padEnd(56)} got=${got} expect=${expect}`)
}
function throws(fn) {
  try { fn(); return false } catch (e) { return true }
}

console.log('='.repeat(78))
console.log('A. 正对照 —— 三条占位串 + AI_UNAVAILABLE 原文，命中就必须 throw')
console.log('='.repeat(78))
const POSITIVE = [
  ['analyze-warning 的占位串',        { warningId: 1, aiAnalysis: '预警分析中，请稍后重试' }],
  ['analyze-class-scores 的占位串',   { classId: 3, aiAnalysis: '班级分析中，请稍后重试' }],
  ['analyze-warning-trend 的占位串',  { counselorId: 5, aiAnalysis: '预警趋势分析中，请稍后重试' }],
  ['AI_UNAVAILABLE 原文（/ai/chat）',  '没有可用的 AI 回答：通义千问未配置有效的 API Key（环境变量 DASHSCOPE_KEY），或本次调用失败。请配置后再使用该功能。'],
  ['埋在两层的数组里',                { data: { list: [{ aiAnalysis: '预警分析中，请稍后重试' }] } }],
  ['首尾有空白',                      { aiAnalysis: '  预警分析中，请稍后重试  ' }],
]
for (const [label, resp] of POSITIVE) {
  check(label, throws(() => aiGuard(resp)), true)
}

console.log()
console.log('='.repeat(78))
console.log('B. 负对照 —— 真结果 / 空值 / 长得像但不等于，必须**不** throw')
console.log('='.repeat(78))
const NEGATIVE = [
  ['一段正常的分析文本',       { aiAnalysis: '该生本学期成绩下滑明显，建议加强高数练习。' }],
  ['aiAnalysis 是 null',       { classId: 3, aiAnalysis: null }],
  ['规则引擎的预测结果',       { riskLevel: '正常', riskScore: 0, riskFactors: [] }],
  ['只有"预警分析中"没有后半句', '预警分析中'],
  ['含相似词但不是整句',       '本次预警分析中未发现异常，请稍后重试其他课程。'],
  ['空对象',                   {}],
  ['null / undefined',         null],
]
for (const [label, resp] of NEGATIVE) {
  check(label, throws(() => aiGuard(resp)), false)
}

console.log()
console.log('='.repeat(78))
console.log('C. 闸放行时必须是**原样**返回（不能悄悄改形状）')
console.log('='.repeat(78))
const real = { riskLevel: '正常', riskScore: 0, riskFactors: [] }
check('返回值 === 入参（同一引用）', aiGuard(real) === real, true)

const err = (() => { try { aiGuard({ aiAnalysis: '预警分析中，请稍后重试' }) } catch (e) { return e } })()
check('error.code', err?.code, 'AI_UNAVAILABLE')
check('error.message 是人话', err?.message === AI_UNCONFIGURED_MESSAGE, true)
check('error.placeholder 留了痕', typeof err?.placeholder === 'string', true)

console.log()
console.log('='.repeat(78))
console.log('D. 单串判据 isAiPlaceholder 自己也要打靶')
console.log('='.repeat(78))
check('精确等于占位串', isAiPlaceholder('预警分析中，请稍后重试'), true)
check('少一个字 -> false', isAiPlaceholder('预警分析中，请稍后重'), false)
check('非字符串 -> false', isAiPlaceholder(null), false)

console.log()
console.log(`通过 ${pass} / 失败 ${fail}`)
if (fail > 0) { console.log('❌ 打靶未过，闸不可信，不许接线'); process.exit(1) }
console.log('✅ 打靶全过：闸扫得到 / 也停得住，可以接线')
