// 生成「待删页清单」—— 删之前先把每个文件的指纹落盘。
//
// 为什么要有这一步：P9 要删掉的这 48 个页面 + 4 个旧 tab-bar，
// 都是重设计第一版生成的、`pages.json` 里没声明的死文件。
// 它们不该留（uni-app 不认 `xxx/index`，留着只会让 check-pages 一直报 48 条未声明），
// 但"该删"不等于"可以不留痕迹地删" —— 万一某个页面的某段实现后面要用，
// 得有办法从 git 历史里定位到、并核对拿到的确实是原件。
//
// 所以清单记三样：路径、字节数、sha256。sha256 是给自己留的核对手段：
// 事后 `git show <删除前的commit>:<path> | sha256sum` 对得上，说明取回的是原件。
//
// 用法: node scripts/gen-removed-manifest.mjs
// 输出: docs/removed-pages.md（在删除**之前**生成并提交）
import { readFileSync, writeFileSync, statSync, existsSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { execSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'

/**
 * 48 个未声明页面 + 4 个只被这些页面引用的旧 tab-bar。
 * 导出给 `delete-removed-pages.mjs` 用 —— 删除脚本必须读同一份清单，
 * 否则「备份的」和「删掉的」有可能不是同一批文件。
 */
export const FILES = [
  // ---- pages/admin (15) ----
  'src/pages/admin/class-management/index.vue',
  'src/pages/admin/colleges/index.vue',
  'src/pages/admin/courses/index.vue',
  'src/pages/admin/data-export/index.vue',
  'src/pages/admin/departments.vue',
  'src/pages/admin/logs.vue',
  'src/pages/admin/majors/index.vue',
  'src/pages/admin/messages/index.vue',
  'src/pages/admin/permissions/index.vue',
  'src/pages/admin/reports.vue',
  'src/pages/admin/roles.vue',
  'src/pages/admin/rules/index.vue',
  'src/pages/admin/settings.vue',
  'src/pages/admin/statistics/index.vue',
  'src/pages/admin/warnings/index.vue',
  // ---- pages/college-admin (8) ----
  'src/pages/college-admin/analysis/index.vue',
  'src/pages/college-admin/classes/index.vue',
  'src/pages/college-admin/counselors/index.vue',
  'src/pages/college-admin/export/index.vue',
  'src/pages/college-admin/scores/index.vue',
  'src/pages/college-admin/students/index.vue',
  'src/pages/college-admin/teachers/index.vue',
  'src/pages/college-admin/warnings/index.vue',
  // ---- pages/counselor (7) ----
  'src/pages/counselor/analytics/index.vue',
  'src/pages/counselor/class-management/index.vue',
  'src/pages/counselor/courses/index.vue',
  'src/pages/counselor/credit-monitor/index.vue',
  'src/pages/counselor/notifications/index.vue',
  'src/pages/counselor/scores/index.vue',
  'src/pages/counselor/settings/index.vue',
  // ---- pages/index (1) ----
  'src/pages/index/index.vue',
  // ---- pages/student (10) ----
  'src/pages/student/analysis/index.vue',
  'src/pages/student/appeals/index.vue',
  'src/pages/student/assistance-feedback/index.vue',
  'src/pages/student/benchmark-analysis/index.vue',
  'src/pages/student/class-members/index.vue',
  'src/pages/student/class-ranking/index.vue',
  'src/pages/student/messages/index.vue',
  'src/pages/student/notification-center/index.vue',
  'src/pages/student/settings/index.vue',
  'src/pages/student/statistics/index.vue',
  // ---- pages/teacher (7) ----
  'src/pages/teacher/analysis/index.vue',
  'src/pages/teacher/audit-log/index.vue',
  'src/pages/teacher/class-management/index.vue',
  'src/pages/teacher/credit-prediction/index.vue',
  'src/pages/teacher/feedback-management/index.vue',
  'src/pages/teacher/messages/index.vue',
  'src/pages/teacher/settings/index.vue',
  // ---- 旧 tab-bar 组件 (4)：只被上面这些页面引用，删页后成为孤儿 ----
  'src/components/admin-tab-bar/admin-tab-bar.vue',
  'src/components/collegeadmin-tab-bar/collegeadmin-tab-bar.vue',
  'src/components/counselor-tab-bar/counselor-tab-bar.vue',
  'src/components/teacher-tab-bar/teacher-tab-bar.vue'
]

const sha = buf => createHash('sha256').update(buf).digest('hex')

// 被 delete-removed-pages.mjs import 时只取 FILES，不要把清单重写一遍
// （重写会把清单的 mtime 改掉，也会让"删除脚本读的是已提交的那份"变得不可信）。
const isMain = import.meta.url === pathToFileURL(process.argv[1]).href
if (!isMain) {
  // 被 import：到此为止
} else {

let head = ''
try { head = execSync('git rev-parse --short HEAD', { encoding: 'utf8' }).trim() } catch {}

const rows = []
let missing = []
for (const f of FILES) {
  if (!existsSync(f)) { missing.push(f); continue }
  const buf = readFileSync(f)
  const lines = buf.toString('utf8').split('\n').length
  rows.push({ f, bytes: statSync(f).size, lines, hash: sha(buf) })
}

// —— 逐个核对"到底能不能从 git 取回" ——
//
// 这里踩过一个坑值得写下来：本脚本**第一版**直接写了句
//   `git show <head>:academic-warning-xcx/src/pages/admin/logs.vue`
// 当作取回办法，但当时 `src/` 这次迁移根本**还没提交**，那些文件是未跟踪的
// 工作区文件 —— 删除未跟踪文件等于 git 里没有副本，那条命令必然报
// `path does not exist in HEAD`。清单是"删除前生成并提交"的，
// 而提交清单 ≠ 提交被清单描述的那些文件。所以取回性必须**实测**，不能靠推断。
//
// 做法：拿 HEAD 里迁移前的同名旧路径（去掉开头 `src/`）比一比。
const recoverable = []
const lossy = []
for (const r of rows) {
  const oldPath = 'academic-warning-xcx/' + r.f.slice(4)
  let buf
  try { buf = execSync('git show ' + head + ':' + oldPath, { maxBuffer: 1 << 24 }) } catch {
    lossy.push({ ...r, oldPath, kind: 'git 里没有同名旧文件' }); continue
  }
  const s = buf.toString('utf8')
  const identical = sha(buf) === r.hash || sha(Buffer.from(s.replace(/\r\n/g, '\n'), 'utf8')) === r.hash
  if (identical) recoverable.push({ ...r, oldPath })
  else {
    const brokenOld = /export default\s*\{\{/.test(s) || /\\text class=/.test(s)
    lossy.push({ ...r, oldPath, kind: brokenOld ? '旧副本也是坏文件' : '旧副本是干净完整 SFC' })
  }
}

const md = `# 已删除的 48 个未声明页面 + 4 个旧 tab-bar

> 本文件在**删除之前**生成并提交，生成时的 HEAD = \`${head}\`。

## ⚠️ 关于"能不能取回"—— 先说结论

**下表的 sha256 只记录了"删掉的是什么"，它不等于备份：这 52 个文件的内容取不回来。**

原因是重设计把源码从根目录搬进 \`src/\` 的这次迁移**一直没有提交**，
所以 \`src/\` 下的文件全部是未跟踪的工作区文件。删除未跟踪文件 = git 里没有任何副本。
本文件第一版里写的 \`git show ${head}:.../src/pages/admin/logs.vue\` 是**错的**，
那条命令必然报 \`path does not exist in HEAD\` —— 写的时候没核实，事后才验出来。

逐个核对后（对照 HEAD 中迁移前的同名旧路径）：

| 情况 | 数量 |
|---|---:|
| 与旧路径副本**逐字节相同** | ${recoverable.length} |
| 旧路径有同名文件但内容不同，那份**也是**坏文件 | ${lossy.filter(l => l.kind === '旧副本也是坏文件').length} |
| 旧路径有同名文件但内容不同，那份是**干净完整 SFC** | ${lossy.filter(l => l.kind === '旧副本是干净完整 SFC').length} |
| 旧路径也不存在 | ${lossy.filter(l => l.kind === 'git 里没有同名旧文件').length} |

### 确实还能取回的（${recoverable.length} 个）

路径是迁移**前**的 \`academic-warning-xcx/pages/...\`，**不是** \`src/pages/...\`：

\`\`\`
${recoverable.map(r => `git show ${head}:${r.oldPath}`).join('\n')}
\`\`\`

## 为什么删

重设计把 \`pages.json\` 的路径约定统一成扁平命名 \`pages/<role>/<name>\`，
这 48 个文件用的是旧约定 \`pages/<role>/<name>/index.vue\`（uni-app 不认目录 index），
且都不在 \`pages.json\` 里 —— 也就是**没有任何入口能到达它们**。
其中 34 个是同一个生成脚本的产物，脚本块被 \`{{\` 打穿（\`export default {{\`），
连 CSS 大括号也一起打穿，模板里还有 \`\\text class=\\"...\` 之类的垃圾。
留着它们只会让 \`scripts/check-pages.mjs\` 永远报「文件存在但未声明 (48)」，
掩盖真正新出现的未声明文件。

4 个旧 tab-bar 组件（\`admin-tab-bar\` 等）已 grep 确认**只被上述页面引用**，
删页后即成为孤儿，故一并删除；现行实现是唯一的 \`app-tab-bar\`。

## 清单（共 ${rows.length} 个文件）

| # | 路径 | 字节 | 行数 | sha256（前 16 位） |
|---:|---|---:|---:|---|
${rows.map((r, i) => `| ${i + 1} | \`${r.f}\` | ${r.bytes} | ${r.lines} | \`${r.hash.slice(0, 16)}\` |`).join('\n')}

## 完整 sha256

\`\`\`
${rows.map(r => `${r.hash}  ${r.f}`).join('\n')}
\`\`\`
`

writeFileSync('docs/removed-pages.md', md, 'utf8')
console.log(`写出 docs/removed-pages.md，共 ${rows.length} 个文件`)
if (missing.length) {
  console.log(`⚠️ 清单里有 ${missing.length} 个路径实际不存在（清单写错了或已被删）:`)
  for (const m of missing) console.log(`   ${m}`)
  process.exit(1)
}

}
