// W10 打靶（学生侧）：把 StudentController 里三处归属判断分别改坏，确认
// StudentControllerOwnershipTest 的 15 条**该红的红、该绿的绿**。
//
// 为什么值得写：这一族最容易"绿得没有意义"——测试全绿可能只是因为
// 断言写成了"调用了就过"。必须证明这些用例能各自抓住一类真错。
//
// 三个靶，各对应一类真错：
//   space   —— isSelfProfile 改按 **users.id** 比（即守卫选错 id 空间）
//              ⇒ 期望：恰好 2 条红（"改自己的订阅放行" 与 "拿自己 users.id 提交必须被拒"）
//   nobody  —— isSelf 恒 false（守卫过宽：把本人也挡住）
//              ⇒ 期望：所有"本人放行"的用例红，拒绝类仍绿
//   noadmin —— isAdmin 恒 false（管理员旁路被打断）
//              ⇒ 期望：恰好管理员那条红
//
// ⚠️ 文件是 **CRLF**：一律用正则 + `\r?\n`，别用 '\n' 拼串（本轮在 teacher 侧已栽过）。
//
// 用法: node _w10_student_target.mjs <space|nobody|noadmin> break
//       node _w10_student_target.mjs <space|nobody|noadmin> restore
import { readFileSync, writeFileSync } from 'node:fs'

const FILE = 'D:/ABS/academic-microservices/student-service/src/main/java/com/academic/controller/StudentController.java'

// 每靶：[原串(正则), 破坏后, 还原回写]
const TARGETS = {
  space: {
    re: /&& pathProfileId\.equals\(studentService\.getProfileIdByUserId\(me\)\);/,
    broken: '&& me.equals(pathProfileId); /* SABOTAGE-SPACE */',
    back: '&& pathProfileId.equals(studentService.getProfileIdByUserId(me));',
  },
  nobody: {
    re: /return me != null && me\.equals\(userId\);/,
    broken: 'return false; /* SABOTAGE-NOBODY */',
    back: 'return me != null && me.equals(userId);',
  },
  noadmin: {
    re: /return "4"\.equals\(role\) \|\| "admin"\.equals\(role\);/,
    broken: 'return false; /* SABOTAGE-NOADMIN */',
    back: 'return "4".equals(role) || "admin".equals(role);',
  },
}

const [name, mode] = process.argv.slice(2)
const t = TARGETS[name]
if (!t) { console.error('靶名必须是: ' + Object.keys(TARGETS).join(' | ')); process.exit(2) }

let src = readFileSync(FILE, 'utf8')
const hits = (src.match(new RegExp(t.re.source, 'g')) || []).length

if (mode === 'break') {
  if (hits !== 1) { console.error(`[${name}] 预期恰有 1 处待拆，实际 ${hits} 处 —— 先看清再动手`); process.exit(2) }
  writeFileSync(FILE, src.replace(t.re, t.broken))
  console.log(`[${name}] 已拆`)
} else if (mode === 'restore') {
  const marks = (src.match(/\/\* SABOTAGE-/g) || []).length
  if (marks !== 1) { console.error(`[${name}] 预期恰有 1 处 SABOTAGE，实际 ${marks} 处`); process.exit(2) }
  // 注意：还原要匹配的是**破坏后**那串（带标记），不是 t.re —— 原来写成 replace(t.re, back) 是空操作。
  if (!src.includes(t.broken)) { console.error(`[${name}] 找不到破坏后的原串，不敢乱还原`); process.exit(2) }
  writeFileSync(FILE, src.split(t.broken).join(t.back))
  console.log(`[${name}] 已还原`)
} else {
  console.error('用法: <靶名> break|restore'); process.exit(2)
}
