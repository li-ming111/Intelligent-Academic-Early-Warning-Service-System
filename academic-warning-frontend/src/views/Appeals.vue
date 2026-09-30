<template>
  <div class="appeals-page">
    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card total">
        <div class="stat-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg></div>
        <div class="stat-body"><div class="stat-value">{{ appeals.length }}</div><div class="stat-label">全部申诉</div></div>
      </div>
      <div class="stat-card pending">
        <div class="stat-icon">⏳</div>
        <div class="stat-body"><div class="stat-value">{{ pendingCount }}</div><div class="stat-label">待处理</div></div>
      </div>
      <div class="stat-card approved">
        <div class="stat-icon">✔</div>
        <div class="stat-body"><div class="stat-value">{{ approvedCount }}</div><div class="stat-label">已通过</div></div>
      </div>
      <div class="stat-card rejected">
        <div class="stat-icon">❌</div>
        <div class="stat-body"><div class="stat-value">{{ rejectedCount }}</div><div class="stat-label">已驳回</div></div>
      </div>
    </div>

    <!-- 申诉列表 -->
    <div class="table-card">
      <div class="table-header">
        <span>成绩申诉列表</span>
        <el-button type="primary" size="small" @click="openSubmit">
          <span style="margin-right:4px">+</span> 提交申诉
        </el-button>
      </div>
      <el-table :data="appeals" stripe empty-text="暂无申诉记录" style="width: 100%">
        <el-table-column prop="id" label="编号" width="70" align="center" />
        <el-table-column prop="courseName" label="科目" min-width="120" show-overflow-tooltip />
        <el-table-column prop="reason" label="申诉原因" min-width="130">
          <template #default="{ row }">
            {{ reasonLabel(row.reason) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)" size="small" effect="dark">{{ statusName(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="170">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
              <el-button v-if="row.status === 0" link type="danger" size="small" @click="withdraw(row.id)">撤销</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 提交申诉弹窗 -->
    <el-dialog v-model="submitVisible" title="提交成绩申诉" width="480px">
      <el-form label-width="80px">
        <el-form-item label="申诉科目" required>
          <el-select v-model="form.courseId" placeholder="选择科目" style="width:100%" filterable>
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="申诉原因" required>
          <el-select v-model="form.reason" placeholder="选择原因" style="width:100%">
            <el-option v-for="r in reasons" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="详细说明" required>
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="请详细描述申诉理由..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" @click="doSubmit">确认提交</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="申诉详情" width="450px">
      <div v-if="detail" class="detail-body">
        <div class="detail-item"><span class="dl">编号</span><span class="dv">#{{ detail.id }}</span></div>
        <div class="detail-item"><span class="dl">科目</span><span class="dv">{{ detail.courseName || '未知' }}</span></div>
        <div class="detail-item"><span class="dl">原因</span><span class="dv">{{ reasonLabel(detail.reason) }}</span></div>
        <!-- 学生自己写的正文。加列（2026-09-30）之前的申诉没有这段文字，那就整块不显示，
             不要渲染成空的"详细说明："，看着像数据丢了 -->
        <div v-if="detail.description" class="detail-item detail-desc">
          <span class="dl">详细说明</span><span class="dv">{{ detail.description }}</span>
        </div>
        <div class="detail-item"><span class="dl">状态</span><el-tag :type="statusColor(detail.status)" size="small">{{ statusName(detail.status) }}</el-tag></div>
        <div class="detail-item"><span class="dl">提交时间</span><span class="dv">{{ fmt(detail.createdAt) }}</span></div>
        <div v-if="detail.reply" class="detail-reply">
          <div class="reply-label">📩 教师回复</div>
          <div class="reply-text">{{ detail.reply }}</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { studentAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'

const appeals = ref([])
const submitVisible = ref(false)
const detailVisible = ref(false)
const detail = ref(null)
const form = ref({ reason: '', description: '', courseId: '' })
const courses = ref([])

const reasons = [
  { label: '成绩录入错误', value: 'entry_error' },
  { label: '缓考未同步', value: 'makeup_not_sync' },
  { label: '阅卷评分有误', value: 'grading_issue' },
  { label: '数据异常', value: 'data_error' },
  { label: '其他原因', value: 'other' }
]

const pendingCount = computed(() => appeals.value.filter(a => a.status === 0 || a.status === 'pending').length)
const approvedCount = computed(() => appeals.value.filter(a => a.status === 1 || a.status === 'approved').length)
const rejectedCount = computed(() => appeals.value.filter(a => a.status === 2 || a.status === 'rejected').length)

function reasonLabel(r) {
  const found = reasons.find(x => x.value === r)
  return found ? found.label : (r || '-')
}
function statusName(s) {
  if (s === 0 || s === 'pending') return '待处理'
  if (s === 1 || s === 'approved') return '已通过'
  if (s === 2 || s === 'rejected') return '已驳回'
  return s || '-'
}
function statusColor(s) {
  if (s === 0 || s === 'pending') return 'warning'
  if (s === 1 || s === 'approved') return 'success'
  if (s === 2 || s === 'rejected') return 'danger'
  return 'info'
}
function fmt(d) {
  if (!d) return '-'
  return new Date(d).toLocaleString('zh-CN')
}

onMounted(load)

async function load() {
  try {
    const uid = getUserId()
    const sid = localStorage.getItem('studentId') || uid
    if (!sid) return
    if (courses.value.length === 0) await fetchCourses()
    const res = await studentAPI.getAppeals(sid)
    let raw = []
    if (Array.isArray(res)) raw = res
    else if (Array.isArray(res?.data)) raw = res.data
    else if (Array.isArray(res?.data?.data)) raw = res.data.data
    const courseMap = {}
    courses.value.forEach(c => { courseMap[c.id] = c.name })
    appeals.value = raw.map(a => ({
      ...a,
      courseName: courseMap[a.courseId || a.course_id] || '未知科目'
    }))
  } catch (e) { console.error('加载申诉失败', e) }
}

async function openSubmit() {
  form.value = { reason: '', description: '', courseId: '' }
  submitVisible.value = true
  if (courses.value.length === 0) await fetchCourses()
}

async function fetchCourses() {
  try {
    const sid = localStorage.getItem('studentId') || getUserId()
    if (!sid) return
    const res = await studentAPI.getScores(sid)
    let raw = []
    if (Array.isArray(res)) raw = res
    else if (Array.isArray(res?.data)) raw = res.data
    else if (Array.isArray(res?.data?.data)) raw = res.data.data
    const seen = new Set()
    const list = []
    raw.forEach(s => {
      const cid = s.courseId || s.course_id
      const cname = s.courseName || s.course_name || s.course || '未知科目'
      if (cid && !seen.has(cid)) {
        seen.add(cid)
        list.push({ id: cid, name: cname })
      }
    })
    courses.value = list
  } catch (e) { console.error('获取课程失败', e) }
}

async function doSubmit() {
  if (!form.value.courseId || !form.value.reason || !form.value.description) { ElMessage.warning('请完善申诉信息'); return }
  try {
    const sid = localStorage.getItem('studentId') || getUserId()
    await studentAPI.submitAppeal({ ...form.value, studentId: sid, courseId: Number(form.value.courseId) })
    ElMessage.success('申诉已提交')
    submitVisible.value = false
    await load()
  } catch (e) { ElMessage.error('提交失败') }
}

async function openDetail(row) {
  try {
    const res = await studentAPI.getAppealDetail(row.id)
    detail.value = res?.data || res || row
  } catch (e) { detail.value = row }
  detailVisible.value = true
}

async function withdraw(id) {
  try {
    await ElMessageBox.confirm('确定撤销该申诉吗？', '提示', { type: 'warning' })
    const sid = localStorage.getItem('studentId') || getUserId()
    await studentAPI.withdrawAppeal(id, sid)
    ElMessage.success('已撤销')
    await load()
  } catch (e) { /* cancelled */ }
}
</script>

<style scoped>
.appeals-page { padding: 20px; min-height: 100vh; background: #f5f7fb; }
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 16px; }
.stat-card { background: #fff; border-radius: 12px; padding: 18px; display: flex; align-items: center; gap: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
.stat-icon { font-size: 28px; }
.stat-body { flex: 1; }
.stat-value { font-size: 26px; font-weight: 700; color: #18181b; }
.stat-label { font-size: 12px; color: #71717a; margin-top: 2px; }
.stat-card.pending .stat-value { color: #f59e0b; }
.stat-card.approved .stat-value { color: #16a34a; }
.stat-card.rejected .stat-value { color: #ef4444; }

.table-card { background: #fff; border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); margin-bottom: 16px; }
.table-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; font-size: 15px; font-weight: 600; color: #18181b; }

.detail-body { padding: 8px 0; }
.detail-item { display: flex; align-items: center; margin-bottom: 12px; gap: 12px; }
.detail-item .dl { width: 70px; color: #71717a; flex-shrink: 0; }
.detail-item .dv { flex: 1; color: #18181b; }
/* 正文可能多段（学生自己敲的回车），要对齐顶部并把换行原样显示 */
.detail-desc { align-items: flex-start; }
.detail-desc .dv { white-space: pre-wrap; word-break: break-word; line-height: 1.6; }
.detail-reply { background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 8px; padding: 12px; margin-top: 10px; }
.reply-label { font-size: 13px; color: #16a34a; margin-bottom: 6px; font-weight: 600; }
.reply-text { color: #3f3f46; line-height: 1.5; white-space: pre-wrap; }

@media (max-width: 768px) { .stat-row { grid-template-columns: 1fr 1fr; } }
</style>
