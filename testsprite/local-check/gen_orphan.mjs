// 生成"悬空外键"检查 SQL。
// 两件事都要做，因为**(声明过的外键只覆盖一部分)**：
//   A. 有 FOREIGN KEY 约束的列 —— 理论上不会有悬空（除非约束被 disable），查一遍当基线；
//   B. **名字像外键但没有约束**的列 —— 上次那 3 处悬空（notifications/communication_logs 的
//      warning_id）全属于这一类，DB 不会替我们拦，只能自己比。
// 对 teacher_id / counselor_id 这种"一个列名可能指向多张表"的，只要**在任一候选表里能对上就不算悬空**
// （宁可漏报也不冤枉），候选表写死在 CAND 里。
import { writeFileSync } from 'node:fs'

const CAND = {
  student_id:    ['student_profile'],
  teacher_id:    ['teacher_profile', 'users'],
  counselor_id:  ['counselor_profiles', 'users'],
  user_id:       ['users'], receiver_id: ['users'], sender_id: ['users'],
  college_id:    ['colleges'], major_id: ['majors'],
  class_id:      ['classes'], course_id: ['courses'],
  module_id:     ['elective_modules'], plan_id: ['assistance_plans'],
  warning_id:    ['academic_warning'], score_id: ['scores'], rule_id: ['warning_rules'],
  permission_id: ['permissions'], role_id: ['roles'],
}
// 明确不查的列，写清理由，免得看报告的人以为是漏了
const SKIP = { entity_id: '审计日志的多态外键，指向哪张表取决于 entity_type', staff_id: '教工号，库里没有对应主键表' }

const cols = process.argv.slice(2).filter((c) => !SKIP[c])
const parts = []
for (const spec of cols) {
  const [tbl, col] = spec.split(':')
  const cands = CAND[col]
  if (!cands) continue
  const notIn = cands.map((t) => `SELECT id FROM \`${t}\``).join(' UNION ')
  parts.push(
    `SELECT '${tbl}.${col}' AS col, COUNT(*) AS dangling, COUNT(${col}) AS notnull` +
    ` FROM \`${tbl}\` WHERE \`${col}\` IS NOT NULL AND \`${col}\` NOT IN (${notIn})`
  )
}
writeFileSync('_orphan.sql', parts.join('\nUNION ALL\n') + ';\n')
console.log(`生成 ${parts.length} 条检查`)
