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

/** 48 个未声明页面 + 4 个只被这些页面引用的旧 tab-bar。 */
const FILES = [
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

const md = `# 已删除的 48 个未声明页面 + 4 个旧 tab-bar

> 本文件在**删除之前**生成并提交。生成时的 HEAD = \`${head}\`。
> 删除动作本身是另一个 commit，所以这些文件在 git 历史里仍然可取回：
>
> \`\`\`
> git show ${head}:academic-warning-xcx/src/pages/admin/logs.vue
> \`\`\`
>
> 取回后核对 sha256（下表第三列）可确认拿到的是原件而非同名文件。

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
