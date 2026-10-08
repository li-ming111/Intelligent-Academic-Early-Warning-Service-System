/**
 * W26 探针：`GET /ai/analyze-scores/{studentId}` 到底是哪种坏法。
 *
 * 读代码得到的两个候选（见 AIController.java:215-232）：
 *   (甲) **解包 bug** —— Feign 返回 `ApiResponse<Object>`，代码把它整个塞进
 *        `request.put("scores", scores)`，而 `AIServiceImpl.analyzeScore` 直接
 *        `(List<Map>) request.get("scores")` ⇒ ClassCastException ⇒ 外层 catch
 *        ⇒ `ApiResponse.error("class com.academic.common.dto.ApiResponse cannot be cast …")`。
 *   (乙) **身份头缺失** —— ai-service 的 FeignConfig 只转发 `Authorization`，
 *        不转发 `X-User-Id`/`X-Role`；student-service 的 GatewayAuthFilter 靠这两个头
 *        建立认证上下文，缺了就落到 `.anyRequest().authenticated()` ⇒ 403。
 *        Feign 抛异常被内层 catch 吞掉 ⇒ `scores` 键根本不存在 ⇒
 *        analyzeScore 返回 `{totalCourses:0, suggestion:"暂无成绩数据，请先录入成绩"}`。
 *
 * 两种坏法**判据不同、可见形状也不同**，所以必须分开量：
 *   - 甲 ⇒ HTTP 200 + code 500 + 消息里含 `cannot be cast`
 *   - 乙 ⇒ HTTP 200 + code 200 + `totalCourses === 0` + `"暂无成绩数据"`
 *   - 真好的 ⇒ code 200 且 totalCourses > 0
 *
 * 只读，不写库。用法：`node _w26_ai_scores_probe.mjs [uid]`
 */
import crypto from 'node:crypto'

const UID = Number(process.argv[2] || 3)
const GW = 'http://localhost:8076'
// 第三参可指向**备用实例**（改了代码、又不动用户那个 8071 的进程时用）
const AI = `http://localhost:${process.argv[3] || '8071'}`
const SECRET = 'academic-jwt-secret-key-for-academic-warning-system-2026'

const b64url = (b) => Buffer.from(b).toString('base64url')
function mintToken (userId, username, role) {
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const now = Math.floor(Date.now() / 1000)
  const payload = b64url(JSON.stringify({ userId, username, role, sub: username, iat: now, exp: now + 3600 }))
  const sig = crypto.createHmac('sha256', SECRET).update(`${header}.${payload}`).digest('base64url')
  return `${header}.${payload}.${sig}`
}

const token = mintToken(UID, `probe${UID}`, 1)
const tokenAdmin = mintToken(1, 'admin', 4)

async function req (url, headers = {}) {
  const res = await fetch(url, { headers })
  let body
  const text = await res.text()
  try { body = JSON.parse(text) } catch { body = text.slice(0, 300) }
  return { status: res.status, body }
}

const out = []
const log = (k, v) => { out.push([k, v]); console.log(`${k}\n    ${v}`) }

console.log(`\n===== W26 analyze-scores 探针（uid=${UID}）=====\n`)

// --- 0. 前置事实：这个学生**确实有**成绩吗（走网关，用他自己的 token）---
const sc = await req(`${GW}/api/students/scores/${UID}`, { Authorization: `Bearer ${token}` })
const n = Array.isArray(sc.body?.data) ? sc.body.data.length : null
log('0. 学生本人读自己的成绩 /api/students/scores',
  `HTTP ${sc.status} / code ${sc.body?.code} / data.length=${n}`)
if (!(n > 0)) {
  console.log('\n⚠️ 这个 uid 读不到成绩 —— 后面的判据不成立，换一个 uid。\n')
  process.exit(1)
}

// --- 1. 走网关打 analyze-scores（学生给自己分析）---
const viaGw = await req(`${GW}/api/ai/analyze-scores/${UID}`, { Authorization: `Bearer ${token}` })
log('1. 经网关 GET /api/ai/analyze-scores/{uid}',
  `HTTP ${viaGw.status}\n    body=${JSON.stringify(viaGw.body).slice(0, 400)}`)

// --- 2. 直连 ai-service 8071：带身份头 ---
const direct = await req(`${AI}/ai/analyze-scores/${UID}`,
  { 'X-User-Id': String(UID), 'X-Role': '1', Authorization: `Bearer ${token}` })
log('2. 直连 ai-service（带 X-User-Id/X-Role）',
  `HTTP ${direct.status}\n    body=${JSON.stringify(direct.body).slice(0, 400)}`)

// --- 3. 直连 ai-service 8071：不带身份头（模拟 Feign 的真实处境）---
const bare = await req(`${AI}/ai/analyze-scores/${UID}`, { Authorization: `Bearer ${token}` })
log('3. 直连 ai-service（**不带** X-User-Id/X-Role，模拟 Feign）',
  `HTTP ${bare.status}\n    body=${JSON.stringify(bare.body).slice(0, 400)}`)

// --- 4. 直接验下游有没有被 Feign 打成 403：不带身份头打 student-service ---
const dsc = await req(`http://localhost:8079/students/scores/${UID}`, { Authorization: `Bearer ${token}` })
log('4. 直连 student-service 不带身份头（Feign 会打成的样子）',
  `HTTP ${dsc.status}\n    body=${JSON.stringify(dsc.body).slice(0, 300)}`)

// --- 5. 用一个**有权限**的角色再看一遍，排除"学生不许看别人的" ---
const adminView = await req(`${GW}/api/ai/analyze-scores/${UID}`, { Authorization: `Bearer ${tokenAdmin}` })
log('5. 经网关用 admin token 打同一个端点（排除归属闸干扰）',
  `HTTP ${adminView.status}\n    body=${JSON.stringify(adminView.body).slice(0, 400)}`)

// --- 6. 学生 A 分析学生 B（越权）：下游应拒 ⇒ 而"拒"**不能**被说成"暂无成绩数据" ---
const teacherTok = mintToken(18, 'teacher001', 2)
const cross = await req(`${AI}/ai/analyze-scores/${UID}`, { 'X-User-Id': '30', 'X-Role': '1' })
log('6. 直连 ai（X-User-Id=30 学生 A 去看学生 27）',
  `HTTP ${cross.status}\n    body=${JSON.stringify(cross.body).slice(0, 400)}`)

// --- 7. 教师（role 2）打同一端点：student-service 的归属闸只放本人/管理员 ---
const asTeacher = await req(`${AI}/ai/analyze-scores/${UID}`, { 'X-User-Id': '18', 'X-Role': '2' })
log('7. 直连 ai（X-Role=2 教师）',
  `HTTP ${asTeacher.status}\n    body=${JSON.stringify(asTeacher.body).slice(0, 400)}`)

// ---------- 判据 ----------
console.log('\n----- 判定 -----')
const pick = (r) => JSON.stringify(r.body)
const msg = (s) => (typeof s === 'string' ? s : (s?.message || s?.msg || ''))
const bodies = [viaGw, direct, bare, adminView, cross, asTeacher].map(pick).join(' | ')
const hasCast = /cannot be cast|ClassCastException/i.test(bodies)
const zeroCourses = [viaGw, direct, bare, adminView]
  .some(r => r.body?.data && r.body.data.totalCourses === 0)
const anyPositive = [viaGw, direct, bare, adminView]
  .some(r => r.body?.data && r.body.data.totalCourses > 0)
// ⭐ 合成判据：**"取不到"绝不能被说成"确实没有"**
const LIE = '暂无成绩数据，请先录入成绩'
const lieCases = [cross, asTeacher].filter(r => (r.body?.data?.suggestion || '') === LIE)
if (lieCases.length) {
  console.log(`🔴 ${lieCases.length} 个越权/受限场景把「取不到」说成了「${LIE}」`)
} else if (cross.body?.data?.suggestion?.includes('读取不到')) {
  console.log('✅ 越权场景如实说「读取不到成绩数据…」，没有冒充「暂无成绩」')
}

if (anyPositive) console.log('✅ 有入口拿到了真统计（totalCourses > 0）—— 端点可用')
if (hasCast) console.log('🔴 命中**解包 bug**：消息里有 cannot be cast ⇒ 甲')
if (zeroCourses && !anyPositive) console.log('🔴 命中 `totalCourses = 0` + 「暂无成绩数据」⇒ 乙（或下游没取到）')
if (!hasCast && !zeroCourses && !anyPositive) console.log('⚠️ 两种都不是，看上面 body 自行判断')
console.log(`\n（下游直连 HTTP ${dsc.status}：403 即证明 Feign 那条路必然拿不到数据）\n`)
