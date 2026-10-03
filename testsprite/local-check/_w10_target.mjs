// W10 打靶：把 `PUT /teachers/scores/{scoreId}` 的归属闸拆掉，确认用例**真的会红**。
//
// 判据不是"跑完是红的"，而是"**恰好那几条**红、其余仍绿"（拆得准不准）。
//
// ⚠️ 两个坑（本轮亲历）：
//   ① 判据在 deleteScore 与 updateScore 里**逐字相同**（本轮就是照 DELETE 镜像的）
//      ⇒ 只锚 `!record.getTeacherId()...` 会命中 2 处，连 DELETE 的闸一起拆掉，
//      那样红了也分不清是谁的功劳。必须带上方法后那句话才唯一。
//   ② 文件是 **CRLF** 行尾，用 `\n` 拼的字符串**一个都匹配不上**（报"0 处"）
//      ⇒ 一律用正则 + `\r?\n`。
//
// 用法: node _w10_target.mjs break     # 拆闸
//       node _w10_target.mjs restore   # 还原（跑完必须执行）
import { readFileSync, writeFileSync } from 'node:fs'

const FILE = 'D:/ABS/academic-microservices/teacher-service/src/main/java/com/academic/controller/TeacherController.java'
const TAIL = 'return ApiResponse.error\\(403, "无权修改其他教师录入的成绩"\\)'
const BROKEN_RE = new RegExp('(\\s*&&\\s*)false /\\* SABOTAGE \\*/\\) \\{(\\r?\\n\\s*' + TAIL + ')')
const ORIG_RE = new RegExp('(\\s*&&\\s*)!record\\.getTeacherId\\(\\)\\.equals\\(requestUserId\\) && !isAdmin\\(request\\)\\) \\{(\\r?\\n\\s*' + TAIL + ')')
const RESTORED = '$1!record.getTeacherId().equals(requestUserId) && !isAdmin(request)) {$2'

const mode = process.argv[2]
const src = readFileSync(FILE, 'utf8')

if (mode === 'break') {
  const n = (src.match(new RegExp(ORIG_RE.source, 'g')) || []).length
  if (n !== 1) { console.error(`预期恰有 1 处待拆，实际 ${n} 处 —— 先看清再动手`); process.exit(2) }
  writeFileSync(FILE, src.replace(ORIG_RE, '$1false /* SABOTAGE */) {$2'))
  console.log('已拆闸')
} else if (mode === 'restore') {
  const n = (src.match(/\* SABOTAGE \*/g) || []).length
  if (n !== 1) { console.error(`预期恰有 1 处 SABOTAGE，实际 ${n} 处`); process.exit(2) }
  writeFileSync(FILE, src.replace(BROKEN_RE, RESTORED))
  console.log('已还原')
} else {
  console.error('用法: break | restore'); process.exit(2)
}
