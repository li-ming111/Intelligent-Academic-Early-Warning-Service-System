<template>
  <div class="ap-page">
    <div class="ap-head">
      <div class="aph-left">
        <div class="aph-icon">📝</div>
        <div><h2>成绩申诉审核</h2><p>审核学生提交的成绩申诉，通过或驳回并给出意见</p></div>
      </div>
      <button class="aph-btn" @click="load">刷新</button>
    </div>

    <!-- 筛选 -->
    <div class="ap-filter">
      <el-select v-model="filterStatus" placeholder="处理状态" clearable size="small" style="width:140px" @change="load">
        <el-option label="待处理" value="0" />
        <el-option label="已通过" value="1" />
        <el-option label="已驳回" value="2" />
        <el-option label="已撤销" value="3" />
      </el-select>
      <span class="ap-count">共 {{ list.length }} 条</span>
    </div>

    <div v-if="loading" class="ap-empty">加载中…</div>
    <div v-else-if="!list.length" class="ap-empty">暂无申诉数据</div>

    <div v-else class="ap-list">
      <div v-for="a in list" :key="a.id" class="ap-card" :class="'st-' + a.status">
        <div class="apc-bar"></div>
        <div class="apc-body">
          <div class="apc-top">
            <span class="apc-name">{{ a.studentName || '未知学生' }}</span>
            <span class="apc-no">{{ a.studentNo || '--' }}</span>
            <span class="apc-sts" :class="'st-' + a.status">{{ statusLabel(a.status) }}</span>
          </div>
          <div class="apc-course">课程：{{ a.courseName || ('#' + (a.courseId || '--')) }}</div>
          <div class="apc-reason">申诉类型：{{ reasonLabel(a.reason) }}</div>
          <p class="apc-desc">{{ a.description || '（学生未填写详细说明）' }}</p>
          <div class="apc-meta">
            <span>提交：{{ fmtTime(a.createdAt) }}</span>
            <span v-if="a.updatedAt">更新：{{ fmtTime(a.updatedAt) }}</span>
          </div>
          <div v-if="a.reply" class="apc-reply">审核意见：{{ a.reply }}</div>
        </div>
        <button v-if="a.status === 0" class="apc-btn" @click="openReview(a)">审核</button>
        <span v-else class="apc-done">{{ statusLabel(a.status) }}</span>
      </div>
    </div>

    <!-- 审核弹窗 -->
    <el-dialog v-model="dialogVisible" title="审核申诉" width="460px" destroy-on-close>
      <div v-if="target" class="rv-info">
        <b>{{ target.studentName }}</b> · {{ target.courseName || ('#' + target.courseId) }}
        <p class="rv-desc">{{ target.description || '（无详细说明）' }}</p>
      </div>
      <el-radio-group v-model="reviewStatus" style="margin:8px 0 12px">
        <el-radio :value="1">通过</el-radio>
        <el-radio :value="2">驳回</el-radio>
      </el-radio-group>
      <el-input v-model="reviewReply" type="textarea" rows="3" placeholder="审核意见（会通知学生）" />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmReview">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminAPI } from '@/api/index'

const list = ref([])
const loading = ref(false)
const filterStatus = ref('')
const dialogVisible = ref(false)
const target = ref(null)
const reviewStatus = ref(1)
const reviewReply = ref('')
const submitting = ref(false)

// score_appeals.reason 是**代码列**（枚举），自由文本在 description。
// 与 student-service / PC 学生端 reasonLabel 保持同一张表。
const REASON_LABELS = {
  entry_error: '成绩录入错误',
  makeup_not_sync: '补考成绩未同步',
  grading_issue: '评分有误',
  data_error: '数据错误',
  other: '其他'
}
function reasonLabel(code) {
  if (!code) return '--'
  return REASON_LABELS[code] || code
}

// 0 待处理 / 1 已通过 / 2 已驳回 / 3 已撤销
function statusLabel(s) {
  return ({ 0: '待处理', 1: '已通过', 2: '已驳回', 3: '已撤销' })[Number(s)] || '未知'
}

function fmtTime(t) {
  if (!t) return '--'
  return String(t).replace('T', ' ').substring(0, 16)
}

async function load() {
  loading.value = true
  try {
    const res = await adminAPI.getAppeals(filterStatus.value)
    list.value = Array.isArray(res) ? res : []
  } catch (e) {
    console.error('加载申诉列表失败', e)
    ElMessage.error('加载申诉列表失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

function openReview(a) {
  target.value = a
  reviewStatus.value = 1
  reviewReply.value = ''
  dialogVisible.value = true
}

async function confirmReview() {
  if (!target.value) return
  submitting.value = true
  try {
    await adminAPI.reviewAppeal(target.value.id, {
      status: reviewStatus.value,
      reply: reviewReply.value
    })
    ElMessage.success(reviewStatus.value === 1 ? '已通过' : '已驳回')
    dialogVisible.value = false
    await load()
  } catch (e) {
    // 后端对非法 status / 非待处理行会回 HTTP 400，这里把原话带出来
    ElMessage.error(e?.response?.data?.message || '审核失败')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.ap-page { padding: 20px; min-height: 100vh; background: #f5f7fb; }
.ap-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.aph-left { display: flex; align-items: center; gap: 12px; }
.aph-icon { width: 44px; height: 44px; border-radius: 12px; background: #eff6ff; display: flex; align-items: center; justify-content: center; font-size: 22px; }
.aph-left h2 { margin: 0; font-size: 19px; color: #18181b; }
.aph-left p { margin: 2px 0 0; font-size: 13px; color: #71717a; }
.aph-btn { border: 1px solid #e4e4e7; background: #fff; border-radius: 8px; padding: 7px 16px; cursor: pointer; font-size: 13px; }
.aph-btn:hover { border-color: #3b82f6; color: #3b82f6; }

.ap-filter { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.ap-count { font-size: 13px; color: #71717a; }

.ap-empty { padding: 60px; text-align: center; color: #a1a1aa; background: #fff; border-radius: 12px; }

.ap-list { display: flex; flex-direction: column; gap: 12px; }
.ap-card { display: flex; align-items: stretch; background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.05); overflow: hidden; }
.apc-bar { width: 4px; background: #f59e0b; }
.ap-card.st-1 .apc-bar { background: #16a34a; }
.ap-card.st-2 .apc-bar { background: #dc2626; }
.ap-card.st-3 .apc-bar { background: #a1a1aa; }
.apc-body { flex: 1; padding: 16px 18px; min-width: 0; }
.apc-top { display: flex; align-items: center; gap: 10px; }
.apc-name { font-weight: 600; color: #18181b; }
.apc-no { font-size: 12px; color: #a1a1aa; }
.apc-sts { margin-left: auto; font-size: 12px; padding: 2px 10px; border-radius: 20px; background: #fef3c7; color: #b45309; }
.apc-sts.st-1 { background: #dcfce7; color: #15803d; }
.apc-sts.st-2 { background: #fee2e2; color: #b91c1c; }
.apc-sts.st-3 { background: #f4f4f5; color: #71717a; }
.apc-course, .apc-reason { margin-top: 6px; font-size: 13px; color: #52525b; }
.apc-desc { margin: 8px 0; font-size: 13px; color: #3f3f46; line-height: 1.6; white-space: pre-wrap; }
.apc-meta { display: flex; gap: 16px; font-size: 12px; color: #a1a1aa; }
.apc-reply { margin-top: 8px; padding: 8px 12px; background: #f8fafc; border-radius: 8px; font-size: 13px; color: #334155; }
.apc-btn { align-self: center; margin: 0 18px; border: none; background: #3b82f6; color: #fff; border-radius: 8px; padding: 8px 18px; cursor: pointer; font-size: 13px; }
.apc-btn:hover { background: #2563eb; }
.apc-done { align-self: center; margin: 0 18px; font-size: 13px; color: #a1a1aa; }

.rv-info { font-size: 14px; color: #18181b; }
.rv-desc { margin: 8px 0 0; font-size: 13px; color: #52525b; white-space: pre-wrap; }
</style>
