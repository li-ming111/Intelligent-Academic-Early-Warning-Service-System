// 两端 `utils/score.js` 的**判定口径层**逐字比对。
//
// 计划里只要求"判定口径必须同步"，**展示适配层允许各端不同** ——
// 所以这里比的是一张固定名单，不是两份文件整体：
//   PENDING_TEXT / PASS_LINE / isPending / isPendingValue / toNumber
//   / warningLevel / warningLevelText
// 这几项一旦分叉，就是"同一份成绩在两端被判成不同等级"那类最难查的 bug。
//
// 比对方式：抠出声明体 → 去掉注释与空白 → 比字符串。
// 不做"语义等价"判断：等价判断会放过"少一个 `>=` 边界"这种真要命的差异。
//
// 用法: node score-caliber-diff.mjs       # 不同则 exit 1
import { readFileSync } from 'node:fs'

const XCX = 'D:/ABS/academic-warning-xcx/src/utils/score.js'
const PC = 'D:/ABS/academic-warning-frontend/src/utils/score.js'

/** 计划点名的判定口径层。展示适配（scoreClass/scoreText/calcGpa/warningLevelKey…）
 *  故意**不**在名单里 —— 各端词汇本来就不同，比了只会得到假差异。 */
const CALIBER = [
  'PENDING_TEXT', 'PASS_LINE',
  'isPending', 'isPendingValue', 'toNumber',
  'warningLevel', 'warningLevelText'
]

/** 去注释（先块后行，避免 `//` 落在块注释里）再压空白。 */
function normalize(src) {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/.*$/gm, '$1')
    .replace(/\s+/g, ' ')
    .trim()
}

/**
 * 抠出名字为 name 的顶层 `function`/`const` 声明体。找不到返回 null。
 *
 * 定界按**行**而不是按分号：本文件里 `export const PENDING_TEXT = '待录入'`
 * **没有结尾分号**，按 `;` 定界会一路吞掉后面好几个声明 —— 那会造出
 * "PENDING_TEXT 不一致"这种**假的** DIFF，比漏报更坏（会让人去改本来同步的东西）。
 */
function extract(source, name) {
  const re = new RegExp(`^(?:export\\s+)?(?:function|const)\\s+${name}\\b`, 'm')
  const m = re.exec(source)
  if (!m) return null
  const from = m.index
  const nl = source.indexOf('\n', from)
  const line = source.slice(from, nl === -1 ? source.length : nl)
  // 声明体在本行内（常量，或签名与 `{` 同行的函数）：整行就是全部
  if (!line.includes('{')) return line.replace(/;\s*$/, '').trim()
  // 跨行：从本行第一个 `{` 起配平括号
  const firstBrace = source.indexOf('{', from)
  let depth = 0
  for (let i = firstBrace; i < source.length; i++) {
    const ch = source[i]
    if (ch === '{') depth++
    else if (ch === '}') { depth--; if (depth === 0) return source.slice(from, i + 1) }
  }
  return null
}

const left = readFileSync(XCX, 'utf8')
const right = readFileSync(PC, 'utf8')

let bad = 0
console.log('判定口径层逐字比对（小程序 vs PC）')
console.log('─'.repeat(72))
for (const name of CALIBER) {
  const a = extract(left, name)
  const b = extract(right, name)
  if (a === null || b === null) {
    bad += 1
    console.log(`MISS ${name.padEnd(18)} ${a === null ? '小程序端缺失' : ''}${b === null ? ' PC 端缺失' : ''}`)
    continue
  }
  const na = normalize(a), nb = normalize(b)
  const same = na === nb
  if (!same) bad += 1
  console.log(`${same ? 'OK  ' : 'DIFF'} ${name.padEnd(18)} ${na.length} vs ${nb.length} 字符`)
  if (!same) {
    // 逐字符找第一个分歧点，直接指出来 —— 只报"不同"没法定位
    let i = 0
    while (i < na.length && i < nb.length && na[i] === nb[i]) i++
    console.log(`     首个分歧在第 ${i + 1} 字符处：`)
    console.log(`       小程序: …${na.slice(Math.max(0, i - 30), i + 50)}`)
    console.log(`       PC    : …${nb.slice(Math.max(0, i - 30), i + 50)}`)
  }
}
console.log('─'.repeat(72))
console.log(bad === 0
  ? `判定口径层一致（${CALIBER.length}/${CALIBER.length} 项逐字相同）`
  : `${bad}/${CALIBER.length} 项不一致 —— 判定口径必须同步，展示层才允许分叉`)

// 同时说明**不**在名单里的那些（避免读者以为"文件相同才合格"）
const show = ['scoreText', 'scoreClass', 'scoreState', 'warningLevelKey', 'warningLevelTagType', 'calcGpa']
const diverged = show.filter(n => {
  const a = extract(left, n), b = extract(right, n)
  if (a === null || b === null) return true
  return normalize(a) !== normalize(b)
})
console.log(`\n展示适配层（允许不同，此处仅登记）：${diverged.length} 项两端各异 —— ${diverged.join(' / ')}`)

process.exit(bad ? 1 : 0)
