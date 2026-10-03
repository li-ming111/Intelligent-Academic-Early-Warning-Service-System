<template>
  <div class="appeal-container">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-number">{{ statistics.total || 0 }}</div>
          <div class="stat-label">累计申诉</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-number">{{ statistics.approved || 0 }}</div>
          <div class="stat-label">申诉通过</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-number">{{ statistics.pending || 0 }}</div>
          <div class="stat-label">待处理</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-number">{{ Number(successRate || 0).toFixed(1) }}%</div>
          <div class="stat-label">成功率</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 申诉提交表单和列表 -->
    <el-row :gutter="20" class="content-row">
      <!-- 左侧：申诉原因分类统计 -->
      <el-col :xs="24" :md="6">
        <el-card class="reason-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="title">申诉原因分布</span>
            </div>
          </template>
          <div v-if="reasonStats.length > 0" class="reason-list">
            <div v-for="item in reasonStats" :key="item.reason" class="reason-item">
              <span class="reason-name">{{ reasonLabel(item.reason) }}</span>
              <el-tag>{{ item.count }}</el-tag>
            </div>
          </div>
          <div v-else class="no-data">暂无数据</div>
        </el-card>
      </el-col>

      <!-- 右侧：申诉列表 -->
      <el-col :xs="24" :md="18">
        <!-- 工具栏 -->
        <el-card class="toolbar-card" shadow="hover" style="margin-bottom: 20px">
          <el-row :gutter="15">
            <el-col :xs="24" :sm="12" :md="8">
              <el-select v-model="filterStatus" @change="loadAppeals" placeholder="筛选状态" style="width: 100%">
                <el-option label="全部" value=""></el-option>
                <!-- value 必须是**数字**：后端 `by-status` 收的是 @RequestParam Integer status，
                     从前写 'pending'/'approved' 会直接 400（页面又被死分支吞掉，看起来只是"没有数据"） -->
                <el-option label="待处理" :value="0"></el-option>
                <el-option label="已通过" :value="1"></el-option>
                <el-option label="已驳回" :value="2"></el-option>
                <el-option label="已撤销" :value="3"></el-option>
              </el-select>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <el-button type="primary" @click="showSubmitDialog = true" style="width: 100%">发起申诉</el-button>
            </el-col>
          </el-row>
        </el-card>

        <!-- 申诉列表表格 -->
        <el-card class="table-card" shadow="hover">
          <el-table :data="appeals" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="60"></el-table-column>
            <!-- 2026-10-03（第四十三轮 W9）：一门课有多个学期，原来这一页**连课程都不显示**，
                 更说不出申诉的是哪一次成绩。后端已在 score_id 非空时带出
                 scoreSemester/scoreTotal/scoreGrade，这里显示出来；老行取不到就显示「未指定成绩」。 -->
            <el-table-column label="申诉成绩" min-width="220">
              <template #default="{ row }">
                <span>{{ appealScoreText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="申诉原因" min-width="200">
              <template #default="{ row }">
                <span class="reason-text">{{ row.reason }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="提交时间" width="150">
              <template #default="{ row }">
                {{ formatTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleDetail(row.id)">查看</el-button>
                <!-- status 是 int（0 待处理/1 已通过/2 已驳回/3 已撤销）。
                     从前写的是 `row.status === 'pending'`，任何一行都匹配不上 —— 撤销按钮从来没出现过 -->
                <el-button v-if="Number(row.status) === 0" link type="danger" size="small" @click="handleWithdraw(row.id)">撤销</el-button>
              </template>
            </el-table-column>
          </el-table>
          <!-- 分页 -->
          <div class="pagination">
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="[5, 10, 15, 20]"
              :total="total"
              layout="total, sizes, prev, pager, next, jumper"
              @change="loadAppeals"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 申诉提交对话框 -->
    <el-dialog v-model="showSubmitDialog" title="发起申诉" width="600px" @close="resetSubmitForm">
      <el-form :model="submitForm" label-width="80px">
        <el-form-item label="关联预警">
          <el-input v-model="submitForm.warningId" placeholder="可选：填写自己的预警ID（如 395）"></el-input>
        </el-form-item>
        <el-form-item label="申诉原因">
          <!-- reason 是**代码**列（'entry_error' 之类）：左侧「申诉原因分布」按它 GROUP BY，
               塞自由文本会把每句话各算一类。自由描述放下面那个 description 框。 -->
          <el-select v-model="submitForm.reason" placeholder="请选择申诉原因" style="width: 100%">
            <el-option v-for="r in reasons" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="详细描述">
          <el-input v-model="submitForm.description" type="textarea" rows="4" placeholder="请详细描述申诉理由"></el-input>
        </el-form-item>
        <el-form-item label="附件">
          <el-input v-model="submitForm.attachments" placeholder="可选：输入附件URL或相关链接"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showSubmitDialog = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit">提交申诉</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 申诉详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="申诉详情" width="600px" @close="currentDetail = null">
      <div v-if="currentDetail" class="detail-content">
        <div class="detail-item">
          <span class="detail-label">申诉ID：</span>
          <span>{{ currentDetail.id }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">申诉成绩：</span>
          <span>{{ appealScoreText(currentDetail) }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">状态：</span>
          <el-tag :type="getStatusType(currentDetail.status)">{{ getStatusLabel(currentDetail.status) }}</el-tag>
        </div>
        <div class="detail-item">
          <span class="detail-label">申诉原因：</span>
          <p>{{ reasonLabel(currentDetail.reason) }}</p>
        </div>
        <div v-if="currentDetail.description" class="detail-item">
          <span class="detail-label">详细描述：</span>
          <p>{{ currentDetail.description }}</p>
        </div>
        <div v-if="currentDetail.warningId" class="detail-item">
          <span class="detail-label">关联预警：</span>
          <span>{{ currentDetail.warningId }}</span>
        </div>
        <div v-if="currentDetail.reply" class="detail-item">
          <span class="detail-label">处理回复：</span>
          <p>{{ currentDetail.reply }}</p>
        </div>
        <div class="detail-item">
          <span class="detail-label">提交时间：</span>
          <span>{{ formatTime(currentDetail.createdAt) }}</span>
        </div>
        <div v-if="currentDetail.attachments" class="detail-item">
          <span class="detail-label">附件：</span>
          <!-- 原来漏了 `:`，href 被当成字面量 "currentDetail.attachments"，链接永远点不开 -->
          <el-link type="primary" :href="currentDetail.attachments" target="_blank">{{ currentDetail.attachments }}</el-link>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { studentAPI } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

/* ============================================================================
 * 这一页有三个坑，都在"请求发出去了、响应也回来了、页面却什么都不显示"这条线上：
 *
 * 1) id 空间：/students/appeals/{x}/** 全系列收的都是 **users.id**
 *    （后端 resolveStudentProfileId / selectByUserId 现查 student_profile）。
 *    而本页原来用的是 `localStorage.getItem('studentId') || userInfo.studentId` ——
 *    'studentId' 这个键全仓**从来没有人写过**，于是每个请求的路径都是
 *    `/appeals/undefined/statistics`，404 或参数转换异常，被 catch 吞成一句 toast。
 *    只用 users.id（localStorage 'userId'）。
 *
 * 2) 响应形状：`src/api/client.js` 的响应拦截器在 code===200 时**已经解开一层**，
 *    await 出来的就是内层载荷。本页原来通篇 `response.data?.code === 200`（恒假、
 *    无 else、静默）、`response.data.success`（没有 `?.`，直接 TypeError）、
 *    以及 `data.data`（历史接口的列表键叫 `list`）。
 *
 * 3) key 名：成功率接口回的是 `{total, success, rate}`，页面读的是 `.successRate`；
 *    原因统计回的是 `[{reason, count}]` 数组，页面当成 `{reason: count}` 对象渲染。
 *    这两个都不是"没数据"，是**名字对不上**。
 * ========================================================================== */

const userId = ref(null)

const statistics = ref({
  total: 0,
  pending: 0,
  approved: 0,
  rejected: 0,
  withdrawn: 0
})
const successRate = ref(0)
/** 后端回的是数组 `[{reason, count}]`，不是对象 */
const reasonStats = ref([])

const appeals = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const filterStatus = ref('')

const showSubmitDialog = ref(false)
const showDetailDialog = ref(false)
const currentDetail = ref(null)

/** 申诉原因词表：与 Appeals.vue / Scores.vue / 小程序 dict.js 同一份，库里现有的 entry_error 与之吻合 */
const reasons = [
  { label: '成绩录入错误', value: 'entry_error' },
  { label: '缓考未同步', value: 'makeup_not_sync' },
  { label: '阅卷评分有误', value: 'grading_issue' },
  { label: '数据异常', value: 'data_error' },
  { label: '其他原因', value: 'other' }
]

const submitForm = reactive({
  warningId: '',
  reason: '',
  description: '',
  attachments: ''
})

onMounted(async () => {
  const userInfo = JSON.parse(localStorage.getItem('user') || '{}')
  userId.value = localStorage.getItem('userId') || userInfo.userId || userInfo.id

  if (!userId.value) {
    ElMessage.error('未找到登录用户信息，无法加载申诉数据')
    return
  }

  await loadStatistics()
  await loadAppeals()
  await loadReasonStats()
})

async function loadStatistics() {
  try {
    const stats = await studentAPI.getAppealStatistics(userId.value)
    if (stats && typeof stats === 'object' && !stats.error) {
      Object.assign(statistics.value, stats)
    }

    const rate = await studentAPI.getAppealSuccessRate(userId.value)
    // 键名是 `rate`（服务端 BigDecimal，形如 0.0）；从前读 `.successRate` 永远拿到 undefined → 0
    successRate.value = rate && rate.rate != null ? Number(rate.rate) : 0
  } catch (error) {
    ElMessage.error('加载统计数据失败')
    console.error(error)
  }
}

async function loadAppeals() {
  try {
    if (filterStatus.value !== '' && filterStatus.value !== null) {
      const list = await studentAPI.getAppealsByStatus(userId.value, filterStatus.value)
      appeals.value = Array.isArray(list) ? list : []
      // 这个分支不走分页接口（服务端把该状态的全部一次给回来），所以 total 就是这一批的长度。
      // 沿用上一次"全部"的 total 会让分页器摆出一堆翻不动的页码。
      total.value = appeals.value.length
    } else {
      const page = await studentAPI.getAppealHistory(userId.value, currentPage.value, pageSize.value)
      // 后端回 `{total, page, pageSize, list:[...]}` —— 列表键是 list
      appeals.value = (page && page.list) || []
      total.value = (page && page.total) || 0
    }
  } catch (error) {
    ElMessage.error('加载申诉列表失败')
    console.error(error)
  }
}

async function loadReasonStats() {
  try {
    const list = await studentAPI.getAppealReasonStatistics(userId.value)
    reasonStats.value = Array.isArray(list) ? list : []
  } catch (error) {
    console.error('加载原因统计失败', error)
  }
}

async function handleSubmit() {
  if (!submitForm.reason) {
    ElMessage.warning('请选择申诉原因')
    return
  }

  try {
    const appeal = {
      // 后端 `submitAppeal` 收的 studentId 是 **users.id**，它自己再查 student_profile
      studentId: userId.value,
      reason: submitForm.reason,
      description: submitForm.description,
      attachments: submitForm.attachments
    }
    // 关联预警是可选的：空串不发这个键（后端对 "" 会当没填，但干脆别发更干净）
    if (submitForm.warningId !== '' && submitForm.warningId != null) {
      appeal.warningId = submitForm.warningId
    }

    const res = await studentAPI.submitAppeal(appeal)
    // 成功/失败都在 200 里：失败回 `{error:"..."}`（缺 studentId / 预警不存在 / 预警不属于该学生）
    if (res && res.error) {
      ElMessage.error(res.error)
      return
    }
    ElMessage.success(res && res.message ? res.message : '申诉已提交')
    showSubmitDialog.value = false
    resetSubmitForm()
    await loadAppeals()
    await loadStatistics()
    await loadReasonStats()
  } catch (error) {
    ElMessage.error('提交申诉失败')
    console.error(error)
  }
}

async function handleDetail(appealId) {
  try {
    const detail = await studentAPI.getAppealDetail(appealId)
    // 查不到时后端回 `{error:"申诉不存在"}`（HTTP 仍 200），从前这个分支根本到不了
    if (!detail || detail.error) {
      ElMessage.error((detail && detail.error) || '申诉不存在')
      return
    }
    currentDetail.value = detail
    showDetailDialog.value = true
  } catch (error) {
    ElMessage.error('加载详情失败')
    console.error(error)
  }
}

async function handleWithdraw(appealId) {
  ElMessageBox.confirm('确定要撤销该申诉吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      // 撤销接口的 studentId 是**必填查询参数**（@RequestParam Long studentId，没有默认值）。
      // 从前只传 appealId，服务端 400，而页面的死分支把它当成"撤销失败"的 toast 吞掉。
      const res = await studentAPI.withdrawAppeal(appealId, userId.value)
      if (res && res.success === false) {
        ElMessage.error(res.message || '撤销失败（可能该申诉已被处理）')
        return
      }
      ElMessage.success('申诉已撤销')
      await loadAppeals()
      await loadStatistics()
    } catch (error) {
      ElMessage.error('撤销申诉失败')
      console.error(error)
    }
  }).catch(() => {})
}

function resetSubmitForm() {
  submitForm.warningId = ''
  submitForm.reason = ''
  submitForm.description = ''
  submitForm.attachments = ''
}

/**
 * 申诉指向的那条成绩，渲染成一句话。
 *
 * 后端（第四十三轮 W9）在 `score_id` 非空时会带出 `scoreSemester` / `scoreTotal` / `scoreGrade`；
 * 老行与课程级入口提交的行没有这些字段 —— 那就退化成只显示课程名，**不显示空白也不显示 `null`**。
 *
 * 学期只用**全局学期序号**（`第5学期`），不带年份：`scores.year` 这一列与 `semester`
 * 并不自洽（见 `StudentServiceImpl.getStudentScores` 的注释），拿它拼年份会印出一个错的学年。
 */
function appealScoreText(row) {
  if (!row) return '未指定成绩'
  const course = row.courseName
    || (row.courseId !== null && row.courseId !== undefined ? `课程 #${row.courseId}` : '')

  const tail = []
  if (row.scoreSemester !== null && row.scoreSemester !== undefined && row.scoreSemester !== '') {
    tail.push(`第${row.scoreSemester}学期`)
  }
  if (row.scoreTotal !== null && row.scoreTotal !== undefined) {
    tail.push(`${row.scoreTotal}分`)
  }

  if (!course && !tail.length) return '未指定成绩'
  if (!tail.length) return course
  return `${course || '未指定课程'} · ${tail.join(' · ')}`
}

/** code → 中文。认不出原样返回 code（不吞成"未知"，否则没法排查） */
function reasonLabel(reason) {
  if (reason === null || reason === undefined || reason === '') return '未填写'
  const found = reasons.find(r => r.value === String(reason))
  return found ? found.label : String(reason)
}

// status 是 int：0 待处理 / 1 已通过 / 2 已驳回 / 3 已撤销。
// 从前这里用的是字符串键，而库里存的是数字 —— 每一行都会落到 `|| status` 上显示成 "0"/"1"。
function getStatusLabel(status) {
  const labels = {
    0: '待处理',
    1: '已通过',
    2: '已驳回',
    3: '已撤销'
  }
  return labels[Number(status)] || `未知状态(${status})`
}

function getStatusType(status) {
  const types = {
    0: 'warning',
    1: 'success',
    2: 'danger',
    3: 'info'
  }
  return types[Number(status)] || 'info'
}

function formatTime(time) {
  if (!time) return '--'
  return new Date(time).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>

<style scoped lang="scss">
.appeal-container {
  padding: 24px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%);
  min-height: 100vh;

  .stats-row {
    margin-bottom: 24px;

    .stat-card {
      text-align: center;
      padding: 24px;
      border-radius: 16px;
      box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
      transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
      border: 2px solid transparent;

      &:hover {
        box-shadow: 0 12px 32px rgba(0, 0, 0, 0.15);
        transform: translateY(-8px);
        border-color: #e8ecf1;
      }

      .stat-number {
        font-size: 32px;
        font-weight: bold;
        background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        background-clip: text;
        margin-bottom: 10px;
      }

      .stat-label {
        font-size: 13px;
        color: #606266;
        font-weight: 500;
      }
    }
  }

  .content-row {
    .reason-card {
      border-radius: 16px;
      box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
      transition: all 0.3s ease;
      border: 2px solid transparent;

      &:hover {
        border-color: #e8ecf1;
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
      }

      .card-header {
        .title {
          font-weight: 600;
          font-size: 16px;
          background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
          background-clip: text;
        }
      }

      .reason-list {
        .reason-item {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding: 10px 0;
          border-bottom: 1px solid #ebeef5;

          &:last-child {
            border-bottom: none;
          }

          .reason-name {
            color: #606266;
            font-size: 14px;
          }
        }
      }
    }

    .toolbar-card {
      margin-bottom: 24px;
      border-radius: 16px;
      box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
      transition: all 0.3s ease;
      border: 2px solid transparent;

      &:hover {
        border-color: #e8ecf1;
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
      }
    }

    .table-card {
      border-radius: 16px;
      box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
      transition: all 0.3s ease;
      border: 2px solid transparent;

      &:hover {
        border-color: #e8ecf1;
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
      }

      .card-header {
        .title {
          font-weight: 600;
          font-size: 16px;
          background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
          background-clip: text;
        }
      }

      .reason-text {
        color: #606266;
        word-break: break-word;
      }

      .pagination {
        text-align: right;
        margin-top: 20px;
      }
    }
  }

  .detail-content {
    line-height: 1.8;

    .detail-item {
      margin-bottom: 20px;
      padding-bottom: 15px;
      border-bottom: 1px solid #ebeef5;

      &:last-child {
        border-bottom: none;
      }

      .detail-label {
        font-weight: 600;
        color: #303133;
        display: inline-block;
        min-width: 80px;
      }

      p {
        margin: 10px 0 0 80px;
        color: #606266;
        line-height: 1.5;
      }
    }
  }

  .no-data {
    text-align: center;
    padding: 40px 20px;
    color: #909399;
  }

  .dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
  }

  :deep(.el-card) {
    border-radius: 16px;
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
    border: 2px solid transparent;
    transition: all 0.3s ease;
    margin-bottom: 24px !important;
  }

  :deep(.el-card:hover) {
    border-color: #e8ecf1;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  }

  :deep(.el-button) {
    border-radius: 8px;
    transition: all 0.3s ease;
  }

  :deep(.el-button:hover) {
    transform: translateY(-2px);
  }

  :deep(.el-table) {
    border-radius: 12px;
    overflow: hidden;
  }

  :deep(.el-table th) {
    background: linear-gradient(135deg, #f8f9fa 0%, #e8ecf1 100%);
    color: #333;
    font-weight: 600;
  }
}

</style>
