<template>
  <div class="audit-page">
    <div class="audit-hero">
      <h1>审计日志</h1>
      <p>成绩修改记录追溯与变更统计</p>
    </div>

    <!-- 统计卡片 -->
    <div class="audit-stats">
      <div class="asc blue">
        <div class="asc-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg></div>
        <div class="asc-num">{{ totalLogs }}</div>
        <div class="asc-lbl">总修改记录</div>
      </div>
      <div class="asc green">
        <div class="asc-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg></div>
        <div class="asc-num">{{ upCount }}</div>
        <div class="asc-lbl">提高次数</div>
      </div>
      <div class="asc red">
        <div class="asc-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><polyline points="23 18 13.5 8.5 8.5 13.5 1 6"/><polyline points="17 18 23 18 23 12"/></svg></div>
        <div class="asc-num">{{ downCount }}</div>
        <div class="asc-lbl">降低次数</div>
      </div>
      <div class="asc purple">
        <div class="asc-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></div>
        <div class="asc-num">{{ avgDiff }}</div>
        <div class="asc-lbl">平均变化</div>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="audit-filter">
      <el-input v-model="searchName" placeholder="搜索姓名或学号" clearable style="width:180px" @change="onSearch" />
      <el-button @click="resetFilter" text>重置</el-button>
    </div>

    <!-- 表格 -->
    <div class="audit-table-card">
      <el-table :data="pagedLogs" stripe v-loading="loading">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="studentName" label="学生" width="90" />
        <el-table-column prop="studentNo" label="学号" width="120">
          <template #default="{ row }">{{ row.studentNo || '--' }}</template>
        </el-table-column>
        <el-table-column prop="courseName" label="课程" min-width="130" show-overflow-tooltip />
        <el-table-column label="成绩变更" min-width="180" align="center">
          <template #default="{ row }">
            <div class="score-change">
              <span class="sc-off">{{ row.oldScore }}</span>
              <svg viewBox="0 0 24 24" fill="none" stroke="#94a3b8" stroke-width="2" width="14" height="14"><polyline points="5 12 19 12"/><polyline points="12 5 19 12 12 19"/></svg>
              <span :class="row.difference >= 0 ? 'sc-new-up' : 'sc-new-down'">{{ row.newScore }}</span>
              <el-tag :type="row.difference > 0 ? 'success' : row.difference < 0 ? 'danger' : 'info'" size="small" class="sc-tag">
                {{ row.difference > 0 ? '+' : '' }}{{ row.difference }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="修改原因" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason || '--' }}</template>
        </el-table-column>
        <el-table-column prop="modifiedBy" label="操作人" width="100" />
        <el-table-column label="修改时间" width="170">
          <template #default="{ row }">{{ formatTime(row.modifiedAt) }}</template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && !filteredLogs.length" class="audit-empty">暂无成绩修改记录</div>
      <div class="audit-pager" v-if="filteredLogs.length > pageSize">
        <el-pagination v-model:current-page="currentPage" :page-size="pageSize" layout="total, prev, pager, next" :total="filteredLogs.length" background small />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { teacherAPI } from '@/api/index'
import { ElMessage } from 'element-plus'
import { getTeacherId } from '@/utils/userUtils'

const auditLogs = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const searchName = ref('')

const totalLogs = computed(() => auditLogs.value.length)
const upCount = computed(() => auditLogs.value.filter(l => (l.difference || 0) > 0).length)
const downCount = computed(() => auditLogs.value.filter(l => (l.difference || 0) < 0).length)
const avgDiff = computed(() => {
  if (!auditLogs.value.length) return '0'
  const sum = auditLogs.value.reduce((a, l) => a + (l.difference || 0), 0)
  return (sum / auditLogs.value.length).toFixed(1)
})

const filteredLogs = computed(() => {
  if (!searchName.value) return auditLogs.value
  const kw = searchName.value.toLowerCase()
  return auditLogs.value.filter(l =>
    (l.studentName || '').toLowerCase().includes(kw) || String(l.studentNo || '').includes(kw))
})

const pagedLogs = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredLogs.value.slice(start, start + pageSize.value)
})

function onSearch() { currentPage.value = 1 }
function resetFilter() { searchName.value = ''; currentPage.value = 1 }
function formatTime(v) { if (!v) return '--'; return String(v).replace('T', ' ').substring(0, 19) }

onMounted(async () => {
  loading.value = true
  try {
    const tid = getTeacherId()
    if (!tid) { console.warn('[TeacherAuditLog] 未获取到登录用户 ID，跳过加载'); return }
    const res = await teacherAPI.getScoreLogs(tid, 1, 500)
    // 2026-10-02（W4.15）：删掉恒假的 `res?.code === 200` 支（拦截器已解包）。
    if (Array.isArray(res)) auditLogs.value = res
    else if (res?.list) auditLogs.value = res.list
  } catch (e) { ElMessage.error('加载失败') }
  finally { loading.value = false }
})
</script>

<style scoped>
.audit-page { padding: 20px 24px; min-height: 100vh; background: #f5f7fb; }
.audit-hero { margin-bottom: 20px; }
.audit-hero h1 { margin: 0 0 4px; font-size: 22px; font-weight: 700; color: #1e293b; }
.audit-hero p { margin: 0; font-size: 13px; color: #94a3b8; }

.audit-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 16px; }
.asc { background: #fff; border-radius: 12px; padding: 18px 20px; display: flex; align-items: center; gap: 14px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
.asc-icon { width: 42px; height: 42px; border-radius: 10px; display: flex; align-items: center; justify-content: center; }
.asc.blue .asc-icon { background: #eff6ff; color: #2563eb; }
.asc.green .asc-icon { background: #f0fdf4; color: #16a34a; }
.asc.red .asc-icon { background: #fef2f2; color: #ef4444; }
.asc.purple .asc-icon { background: #f5f3ff; color: #7c3aed; }
.asc-num { font-size: 22px; font-weight: 700; color: #1e293b; }
.asc-lbl { font-size: 12px; color: #94a3b8; margin-top: 2px; }

.audit-filter { display: flex; gap: 12px; align-items: center; margin-bottom: 14px; }

.audit-table-card { background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
.audit-empty { text-align: center; padding: 60px 20px; color: #94a3b8; font-size: 13px; }
.audit-pager { display: flex; justify-content: center; padding: 12px 0; }

.score-change { display: flex; align-items: center; justify-content: center; gap: 6px; }
.sc-off { color: #94a3b8; font-size: 14px; }
.sc-new-up { color: #16a34a; font-weight: 700; font-size: 15px; }
.sc-new-down { color: #ef4444; font-weight: 700; font-size: 15px; }
.sc-tag { margin-left: 6px; }

@media (max-width: 900px) { .audit-stats { grid-template-columns: repeat(2, 1fr); } }
</style>
