// P9 删除脚本 —— 把 `docs/removed-pages.md` 里登记的那 52 个文件从磁盘上拿掉。
//
// 两个约束：
//  1. **清单必须先存在**。删除前核对 docs/removed-pages.md 在 git 里已提交，
//     否则"备份清单"就是空话 —— 删完之后清单本身也可能一起没了。
//  2. **删除前逐文件重算 sha256 并与清单比对**。如果某个文件在清单生成之后被改过，
//     说明它可能已经不是清单描述的那份东西了，此时停下来让人看一眼，
//     不要闷头删。这一步同时也证明"清单里记的确实就是即将被删的"。
//
// 用法: node scripts/delete-removed-pages.mjs [--force]
//   --force 跳过 sha256 比对（仅在明知文件已被有意修改时用）
import { readFileSync, existsSync, rmSync, rmdirSync, readdirSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { dirname, join } from 'node:path'
import { execSync } from 'node:child_process'
import { FILES } from './gen-removed-manifest.mjs'

const MANIFEST = 'docs/removed-pages.md'
const force = process.argv.includes('--force')

const sha = buf => createHash('sha256').update(buf).digest('hex')

// —— 1. 清单必须已被提交 ——
if (!existsSync(MANIFEST)) {
  console.error(`✗ 找不到 ${MANIFEST}，先跑 gen-removed-manifest.mjs 并提交`)
  process.exit(1)
}
try {
  execSync(`git ls-files --error-unmatch -- ${MANIFEST}`, { stdio: 'ignore' })
} catch {
  console.error(`✗ ${MANIFEST} 还没被 git 跟踪/提交 —— 先提交清单，再删文件`)
  process.exit(1)
}

// —— 2. 从清单正文里取 sha256，逐文件比对 ——
const manifestText = readFileSync(MANIFEST, 'utf8')
const recorded = new Map()
for (const m of manifestText.matchAll(/^([0-9a-f]{64}) {2}(src\/\S+)$/gm)) {
  recorded.set(m[2], m[1])
}
if (recorded.size !== FILES.length) {
  console.error(`✗ 清单里解析出 ${recorded.size} 条指纹，脚本清单有 ${FILES.length} 条 —— 两者不同步，停下`)
  process.exit(1)
}

const mismatched = []
const absent = []
for (const f of FILES) {
  if (!existsSync(f)) { absent.push(f); continue }
  const actual = sha(readFileSync(f))
  if (actual !== recorded.get(f)) mismatched.push({ f, expected: recorded.get(f), actual })
}

if (absent.length) {
  console.log(`注意：清单里有 ${absent.length} 个文件已不在磁盘上（可能删过一轮），跳过：`)
  for (const f of absent) console.log(`   ${f}`)
}
if (mismatched.length && !force) {
  console.error(`✗ ${mismatched.length} 个文件的 sha256 与清单不符 —— 清单生成之后被改过。`)
  console.error('  这可能是有人有意修改，也可能是另一批改动误触。确认无误后加 --force 重跑。')
  for (const m of mismatched) console.error(`   ${m.f}\n     清单 ${m.expected}\n     实际 ${m.actual}`)
  process.exit(1)
}

// —— 3. 删文件，随后清掉变空的目录 ——
let n = 0
for (const f of FILES) {
  if (!existsSync(f)) continue
  rmSync(f)
  n += 1
}

/**
 * 删掉空目录：删走 `pages/admin/logs/index.vue` 后 `pages/admin/logs/` 只剩空壳。
 *
 * 必须**自底向上**。之前写错过一版：从 `src/pages` 往下走，但判据是"这个目录本身空不空"，
 * `src/pages` 显然非空、当场 return，一个都没删到 —— 47 个空壳全留在磁盘上。
 * 这里改成先递归进子目录，回来再看自己是不是空了。
 */
function pruneEmpty(dir) {
  let entries
  try { entries = readdirSync(dir, { withFileTypes: true }) } catch { return }
  for (const e of entries) {
    if (e.isDirectory()) pruneEmpty(join(dir, e.name))
  }
  let left
  try { left = readdirSync(dir) } catch { return }
  if (left.length === 0) rmdirSync(dir)
}

const roots = new Set(FILES.map(f => f.split('/').slice(0, 3).join('/')))  // src/pages/<role>
for (const r of roots) pruneEmpty(dirname(r))  // 从 src/pages 起递归，收敛到还没空的层级

console.log(`已删除 ${n} 个文件`)

// —— 4. 删完自查：目标必须真的不在了 ——
const leftover = FILES.filter(f => existsSync(f))
if (leftover.length) {
  console.error(`✗ 还有 ${leftover.length} 个没删掉：`)
  for (const f of leftover) console.error(`   ${f}`)
  process.exit(1)
}
console.log('自查通过：清单里的文件均不存在')
