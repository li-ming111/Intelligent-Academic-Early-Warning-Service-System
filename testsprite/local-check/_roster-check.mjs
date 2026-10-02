import { captchaFields } from './_captcha.mjs'
// 一次性排查：教师端「花名册」两个接口到底给不给数据。
// 用文件而不是命令行传中文，避免 Windows shell 把中文按 GBK 编进去、
// 到了服务端按 UTF-8 解出来对不上 className（实测命令行里 0 行、文件里 24 行）。
const BASE = process.env.API_BASE || 'http://localhost:8076'

const login = await (await fetch(`${BASE}/api/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: '10001', password: '123456', ...(await captchaFields(BASE)) })
})).json()
const token = login.data.token
const H = { Authorization: `Bearer ${token}` }
const get = async (url) => (await (await fetch(`${BASE}${url}`, { headers: H })).json())

const cls = await get('/api/teachers/class-management/my-classes?teacherId=29')
console.log('my-classes code =', cls.code)
for (const c of cls.data || []) console.log(`  id=${c.id} ${c.name} studentCount=${c.studentCount}`)

for (const name of (cls.data || []).filter(c => c.studentCount > 0).map(c => c.name)) {
  const r = await get(`/api/teachers/scores/by-class?class_name=${encodeURIComponent(name)}&teacher_id=29`)
  const rows = r.data || []
  const seen = new Map()
  for (const x of rows) if (!seen.has(String(x.studentId))) seen.set(String(x.studentId), x.studentName)
  console.log(`by-class "${name}" code=${r.code} rows=${rows.length} unique=${seen.size}`)
  console.log(`  含 profileId=3 ？${seen.has('3')}；含「司明」？${[...seen.values()].includes('司明')}`)
  console.log(`  rows[0] = ${JSON.stringify(rows[0])}`)
}

// 对照：课程流水（成绩页列表用的就是它）
const sc = await get('/api/teachers/scores?course_id=1')
const srows = sc.data || []
console.log(`/teachers/scores?course_id=1 code=${sc.code} rows=${srows.length}`)
console.log(`  司明的行 = ${JSON.stringify(srows.filter(x => x.studentName === '司明'))}`)
