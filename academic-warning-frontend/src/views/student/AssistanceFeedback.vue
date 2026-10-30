<template>
  <div class="feedback-container">
    <!-- 评价统计
         ⚠️ key 一律用后端 `GET /students/evaluations/{profileId}/statistics` 真实返回的四个名字：
            totalCount / averageScore / avgEffectiveness / ratingDistribution。
            改前这里是 total / avgRating —— 后端从来没有这两个键（`|| 0` 兜住 undefined），
            所以「已评价计划」恒 0、「平均评分」恒 0.0，无论学生评价过几次。 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-number">{{ statistics.totalCount || 0 }}</div>
          <div class="stat-label">已评价计划</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <!-- 一条评价都没有时后端回 0，但「0.0 分」读起来像"大家都打了 0 分"；
               显示 "—" 才是实话：没有数据，不是平均值为零 -->
          <div class="stat-number">{{ statistics.totalCount > 0 ? num(statistics.averageScore).toFixed(1) : '—' }}</div>
          <div class="stat-label">平均评分</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-number">{{ statistics.totalCount > 0 ? num(statistics.avgEffectiveness).toFixed(1) : '—' }}</div>
          <div class="stat-label">有效性评分</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-number">{{ pendingPlans.length }}</div>
          <div class="stat-label">待评价计划</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 评分分布 + 评价列表 -->
    <el-row :gutter="20" class="content-row">
      <!-- 左侧：评分分布 -->
      <el-col :xs="24" :md="6">
        <el-card class="distribution-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="title">评分分布</span>
            </div>
          </template>
          <!-- 判空用 totalCount 而不是 ratingDistribution：后端现在**总是**回 5 个桶
               （"1".."5" 都在，没人打过的填 0），空对象在 JS 里是真值，
               写 `v-if="statistics.ratingDistribution"` 会让"一条评价都没有"也画出一列 0 次。 -->
          <div v-if="statistics.totalCount > 0" class="distribution-list">
            <div v-for="(count, rating) in statistics.ratingDistribution" :key="rating" class="distribution-item">
              <span class="rating-star">
                <el-rate :model-value="parseInt(rating)" disabled size="small" />
              </span>
              <el-progress :percentage="getRatingPercentage(count)" :color="getProgressColor(rating)" />
              <span class="count">{{ count }}次</span>
            </div>
          </div>
          <div v-else class="no-data">暂无数据</div>
        </el-card>
      </el-col>

      <!-- 右侧：评价列表 -->
      <el-col :xs="24" :md="18">
        <!-- 工具栏 -->
        <el-card class="toolbar-card" shadow="hover" style="margin-bottom: 20px">
          <el-row :gutter="15">
            <el-col :xs="24" :sm="12" :md="8">
              <el-button type="primary" @click="showEvaluationDialog = true" style="width: 100%">
                评价帮扶计划
              </el-button>
            </el-col>
          </el-row>
        </el-card>

        <!-- 评价列表表格 -->
        <el-card class="table-card" shadow="hover">
          <template #header v-if="evaluations.length > 0">
            <div class="card-header">
              <span class="title">我的评价</span>
            </div>
          </template>
          <el-table v-if="evaluations.length > 0" :data="evaluations" stripe style="width: 100%">
            <el-table-column label="计划名称" min-width="160">
              <template #default="{ row }">{{ planName(row.planId) }}</template>
            </el-table-column>
            <!-- 评分/反馈的字段名用后端的：score / content。
                 改前读的是 rating / feedback —— 评价列表接口从来没回过这两个键，
                 所以「评分」那栏永远是 0 颗星、「反馈」那栏永远空白。
                 ⚠️ `:model-value` 而不是 `v-model`：v-model 会往 row 上写，改的是服务端数据的本地副本。 -->
            <el-table-column label="评分" width="80">
              <template #default="{ row }">
                <el-rate :model-value="num(row.score)" disabled size="small" />
              </template>
            </el-table-column>
            <el-table-column label="有效性" width="90">
              <template #default="{ row }">
                <!-- 历史行为 NULL（加列前提交的评价根本没这一项），如实显示「未评分」，
                     不用 `|| 0` 盖成 0 分 —— 那会把"没收集到"说成"打了 0 分"。 -->
                <el-tag v-if="row.effectiveness != null" :type="getEffectivenessType(row.effectiveness)">
                  {{ row.effectiveness }} / 5
                </el-tag>
                <span v-else class="feedback-text">未评分</span>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="反馈" min-width="200">
              <template #default="{ row }">
                <span class="feedback-text">{{ row.content }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="评价时间" width="150">
              <template #default="{ row }">
                {{ formatTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="no-data-placeholder">
            <el-empty description="暂无评价" />
          </div>
        </el-card>

        <!-- 计划列表：筛选在这里生效（原先下拉挂在「我的评价」上方，却既不筛评价也不筛计划） -->
        <el-card class="pending-card" shadow="hover" style="margin-top: 20px">
          <template #header>
            <div class="card-header">
              <span class="title">{{ planListTitle }}</span>
              <el-select v-model="filterStatus" size="small" style="width: 130px">
                <el-option label="全部计划" value=""></el-option>
                <el-option label="待评价" value="pending"></el-option>
                <el-option label="已评价" value="evaluated"></el-option>
              </el-select>
            </div>
          </template>
          <el-table v-if="filteredPlans.length > 0" :data="filteredPlans" stripe style="width: 100%">
            <el-table-column prop="title" label="计划名称" min-width="150"></el-table-column>
            <!-- 状态文案直接用后端给的 statusText（进行中/已完成/未开始）。
                 改前是前端按 'initiated'/'in_progress' 这类**字符串**查表，而后端回的是数字
                 （0/1/2），于是标签上显示的就是那个数字本身。 -->
            <el-table-column prop="statusText" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">{{ row.statusText || '未开始' }}</el-tag>
              </template>
            </el-table-column>
            <!-- 进度字段叫 progress（0-100 的整数），不是 progressPercentage ——
                 改前读后者恒 undefined，`|| 0` 兜成 0，进度条**永远 0%**。 -->
            <el-table-column label="进度" width="150">
              <template #default="{ row }">
                <el-progress :percentage="num(row.progress)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEvaluate(row)">
                  评价
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="no-data-placeholder">
            <el-empty description="暂无计划" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 评价提交对话框 -->
    <el-dialog v-model="showEvaluationDialog" title="评价帮扶计划" width="600px" @close="resetEvaluationForm">
      <el-form :model="evaluationForm" label-width="100px">
        <el-form-item label="选择计划" required>
          <el-select v-model="evaluationForm.planId" placeholder="请选择要评价的计划" style="width: 100%">
            <el-option
              v-for="plan in pendingPlans"
              :key="plan.id"
              :label="plan.title || '帮扶计划'"
              :value="plan.id"
            />
          </el-select>
        </el-form-item>
        <!-- ⚠️ 两个 el-rate 都**不能**开 allow-half：库里的 score / effectiveness 都是 INT 列，
             MySQL 会把 3.5 静默四舍五入成 4 —— 学生点 3.5 星、库里 4 星、页面显示 4 星。
             后端现在也会对非整数评分报错（不是取整），这里的设置是让它根本不发出去。 -->
        <el-form-item label="计划评分" required>
          <el-rate v-model="evaluationForm.score" show-score />
        </el-form-item>
        <el-form-item label="有效性评分" required>
          <el-rate v-model="evaluationForm.effectiveness" show-score />
        </el-form-item>
        <el-form-item label="反馈内容">
          <el-input
            v-model="evaluationForm.content"
            type="textarea"
            rows="4"
            placeholder="请输入您对帮扶计划的评价和建议"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showEvaluationDialog = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit">提交评价</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 编辑评价对话框 -->
    <el-dialog v-model="showEditDialog" title="编辑评价" width="600px" @close="resetEvaluationForm">
      <el-form :model="evaluationForm" label-width="100px">
        <el-form-item label="计划评分">
          <el-rate v-model="evaluationForm.score" show-score />
        </el-form-item>
        <el-form-item label="有效性评分">
          <el-rate v-model="evaluationForm.effectiveness" show-score />
        </el-form-item>
        <el-form-item label="反馈内容">
          <el-input
            v-model="evaluationForm.content"
            type="textarea"
            rows="4"
            placeholder="请输入您对帮扶计划的评价和建议"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showEditDialog = false">取消</el-button>
          <el-button type="primary" @click="handleUpdate">更新评价</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { studentAPI } from '@/api'
import { ElMessage } from 'element-plus'

/**
 * ⚠️ 这个页面要同时用到**两套 ID**，必须分清（两者有重叠值，传错不报错、只会静默查空）：
 *  - `userId`    = `users.id`（司明 = 27）：`/students/assistance/{uid}`、`/students/student-by-user/{uid}`
 *  - `profileId` = `student_profile.id`（司明 = 3）：`/students/evaluations/{profileId}/list|statistics`
 *    （底层 SQL 走 `assistance_plans.student_id`，存的正是 profile id）
 *
 * 改前这里只有一个 `studentId`，取自 `localStorage.getItem('studentId')` ——
 * 而 Login.vue 登录时**从来没写过这个键**，所以它恒为 undefined，
 * 请求真的发成了 `/students/evaluations/undefined/list`（后端 parseLong 抛异常 → code 500），
 * 而页面又用死分支读响应，于是"永远没有评价"看起来一切都正常。
 */
const userId = ref(null)
const profileId = ref(null)

const EMPTY_STATISTICS = {
  totalCount: 0,
  averageScore: 0,
  avgEffectiveness: 0,
  ratingDistribution: {}
}

const statistics = ref({ ...EMPTY_STATISTICS })

const evaluations = ref([])
const assistancePlans = ref([])
const filterStatus = ref('pending')

const showEvaluationDialog = ref(false)
const showEditDialog = ref(false)

/**
 * 提交体的 key 就是后端 `submitEvaluation` 读的那几个：
 * `planId` / `score` / `effectiveness` / `content`（外加上两个名字 `rating` / `feedback`，
 * 只为兼容旧页面，新代码不再用）。
 * `id` 只在"编辑"时带上：后端见到 `id` 走**更新**，否则同一计划会被插成两条（实测过）。
 */
const evaluationForm = reactive({
  id: null,
  planId: null,
  score: 0,
  effectiveness: 0,
  content: ''
})

/**
 * 待评价 = 未完成（status !== 2）且尚未提交过评价的计划。
 *
 * ⚠️ 改前默认分支只判 `notFinished`，**已评价**的计划也会留在"待评价计划"里，
 * 上方统计卡的数据因此虚高；「已完成」也不能进待评价（原来判的是字符串 'completed'，
 * 后端给的是数字 2，条件恒为真）。
 */
const evaluatedPlanIds = computed(() => new Set(evaluations.value.map(e => String(e.planId))))
const pendingPlans = computed(() =>
  assistancePlans.value.filter(p => Number(p.status) !== 2 && !evaluatedPlanIds.value.has(String(p.id)))
)
const filteredPlans = computed(() => {
  if (filterStatus.value === 'pending') return pendingPlans.value
  if (filterStatus.value === 'evaluated') {
    return assistancePlans.value.filter(p => evaluatedPlanIds.value.has(String(p.id)))
  }
  return assistancePlans.value
})
const planListTitle = computed(() => {
  if (filterStatus.value === 'pending') return '待评价计划'
  if (filterStatus.value === 'evaluated') return '已评价计划'
  return '全部计划'
})
/** 评价行只有 planId，没有计划标题；从已加载的计划里取，取不到就说"帮扶计划"。 */
function planName(planId) {
  return assistancePlans.value.find(p => String(p.id) === String(planId))?.title || '帮扶计划'
}

onMounted(async () => {
  const userInfo = JSON.parse(localStorage.getItem('user') || '{}')
  userId.value = localStorage.getItem('userId') || userInfo.userId || userInfo.id

  if (!userId.value) {
    ElMessage.error('未取到当前登录用户，请重新登录')
    return
  }
  await loadAssistancePlans()
  await loadProfileId()
  await loadEvaluations()
  await loadStatistics()
})

/** `users.id` → `student_profile.id`。评价相关的两个接口只认后者。 */
async function loadProfileId() {
  try {
    const res = await studentAPI.getStudentInfoByUserId(userId.value)
    if (res && res.id != null) {
      profileId.value = res.id
    } else {
      // 查不到学生档案时后端回的是 `{error:"学生不存在"}`（同样 HTTP 200）
      ElMessage.error((res && res.error) || '未找到学生档案，无法加载评价')
    }
  } catch (error) {
    console.error('加载学生档案失败', error)
  }
}

async function loadAssistancePlans() {
  try {
    const res = await studentAPI.getAssistancePlans(userId.value)
    // ⚠️ `client.js` 的响应拦截器在 code===200 时**已经把内层 data 解包返回**，
    // 所以这里拿到的就是数组本身。写成 `res.data?.code === 200` 的分支恒为假、
    // 且没有 else —— 不报错、不写日志、页面永远是空态（本次一并修掉的那类"死分支"）。
    assistancePlans.value = Array.isArray(res) ? res : []
  } catch (error) {
    console.error('加载帮扶计划失败', error)
  }
}

async function loadEvaluations() {
  if (!profileId.value) return
  try {
    const res = await studentAPI.getStudentEvaluations(profileId.value)
    evaluations.value = Array.isArray(res) ? res : []
  } catch (error) {
    ElMessage.error('加载评价列表失败')
    console.error(error)
  }
}

async function loadStatistics() {
  if (!profileId.value) return
  try {
    const res = await studentAPI.getEvaluationStatistics(profileId.value)
    statistics.value = res && typeof res === 'object' ? res : { ...EMPTY_STATISTICS }
  } catch (error) {
    console.error('加载统计数据失败', error)
  }
}

function handleEvaluate(plan) {
  evaluationForm.id = null
  evaluationForm.planId = plan.id
  evaluationForm.score = 0
  evaluationForm.effectiveness = 0
  evaluationForm.content = ''
  showEvaluationDialog.value = true
}

function handleEdit(evaluation) {
  evaluationForm.id = evaluation.id
  evaluationForm.planId = evaluation.planId
  // 字段名用后端的 score / content —— 改前读的是 rating / feedback，
  // 这两个键评价接口从来没回过，所以"编辑"弹窗里永远是空的评分和空白反馈。
  evaluationForm.score = num(evaluation.score)
  evaluationForm.effectiveness = num(evaluation.effectiveness)
  evaluationForm.content = evaluation.content || ''
  showEditDialog.value = true
}

/** 两个弹窗共用的前置校验。 */
function validateForm() {
  if (!evaluationForm.planId) {
    ElMessage.warning('请选择要评价的计划')
    return false
  }
  if (!evaluationForm.score) {
    ElMessage.warning('请为帮扶计划评分')
    return false
  }
  if (!evaluationForm.effectiveness) {
    // 页面上「有效性评分」本来就标着必填，那就真的拦住 ——
    // 发一个 0 过去只会被后端拒绝（1-5 才是合法值），不如在这儿把话说清楚。
    ElMessage.warning('请为帮扶有效性评分')
    return false
  }
  return true
}

async function handleSubmit() {
  if (!validateForm()) return
  await sendEvaluation(false)
}

async function handleUpdate() {
  if (!validateForm()) return
  await sendEvaluation(true)
}

/**
 * 提交或更新。
 *
 * ⚠️ 成败**不能**看 HTTP 状态，也不能看响应信封里的 `code` ——
 * 校验失败同样是 200 + `code:200`，真正的结果在载荷里：`{success:false, message}`。
 * 改前这里写的是 `response.data.success`：拦截器已经解包，`response.data` 是 undefined，
 * 这一行**抛 TypeError**，被 catch 转成「提交评价失败」——
 * 于是明明已经写进库了，页面还是报失败，还顺手把弹窗留着让你再点一次（再点就写第二条）。
 */
async function sendEvaluation(isEdit) {
  try {
    const evaluation = {
      planId: evaluationForm.planId,
      // users.id：后端拿它和计划所属学生核对，对不上直接拒绝写入（不静默改归属）
      studentId: userId.value,
      score: evaluationForm.score,
      effectiveness: evaluationForm.effectiveness,
      content: evaluationForm.content
    }
    if (isEdit) {
      // 只有编辑才带 id：带上才走更新，否则同一计划会被插成第二条
      evaluation.id = evaluationForm.id
    }

    const res = await studentAPI.submitEvaluation(evaluation)
    if (res && res.success === false) {
      ElMessage.error(res.message || (isEdit ? '更新评价失败' : '提交评价失败'))
      return
    }
    // 用后端给的话，别在前端统一说"已提交" ——
    // 不带 id 重复提交时后端回的是「该计划已有评价，本次为更新」，那才是事实。
    ElMessage.success((res && res.message) || (isEdit ? '评价已更新' : '评价已提交'))
    if (isEdit) showEditDialog.value = false
    else showEvaluationDialog.value = false
    resetEvaluationForm()
    await loadEvaluations()
    await loadStatistics()
  } catch (error) {
    ElMessage.error(isEdit ? '更新评价失败' : '提交评价失败')
    console.error(error)
  }
}

function resetEvaluationForm() {
  evaluationForm.id = null
  evaluationForm.planId = null
  evaluationForm.score = 0
  evaluationForm.effectiveness = 0
  evaluationForm.content = ''
}

/** JSON 里的值可能是 null / undefined / 字符串，统一成有限数字（拿不到就是 0）。 */
function num(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function getRatingPercentage(count) {
  const maxCount = Math.max(...Object.values(statistics.value.ratingDistribution || {}))
  return maxCount > 0 ? (count / maxCount) * 100 : 0
}

function getProgressColor(rating) {
  const colors = {
    '1': '#f56c6c',
    '2': '#e6a23c',
    '3': '#e6a23c',
    '4': '#67c23a',
    '5': '#67c23a'
  }
  return colors[rating] || '#67c23a'
}

function getEffectivenessType(effectiveness) {
  if (!effectiveness) return 'info'
  if (effectiveness >= 4) return 'success'
  if (effectiveness >= 3) return 'warning'
  return 'danger'
}

/**
 * 状态码 → 标签色。状态**文案**直接用后端给的 `statusText`，不再在前端查表。
 *
 * 改前这里是按 'initiated' / 'in_progress' / 'completed' 这些字符串查的，
 * 而后端 `planStatusText` 用的是数字（0 未开始 / 1 进行中 / 2 已完成），
 * 于是 `getStatusLabel` 查不到、`|| status` 兜底，标签上显示的是数字本身。
 */
function getStatusType(status) {
  const types = { 0: 'info', 1: 'warning', 2: 'success' }
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
.feedback-container {
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
    .distribution-card {
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

      .distribution-list {
        .distribution-item {
          display: flex;
          align-items: center;
          gap: 10px;
          padding: 10px 0;
          border-bottom: 1px solid #ebeef5;

          &:last-child {
            border-bottom: none;
          }

          .rating-star {
            min-width: 100px;
          }

          .el-progress {
            flex: 1;
          }

          .count {
            min-width: 50px;
            text-align: right;
            color: #606266;
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

      .feedback-text {
        color: #606266;
        word-break: break-word;
        line-height: 1.5;
      }

      .no-data-placeholder {
        padding: 40px 20px;
      }
    }

    .pending-card {
      margin-top: 24px;
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

  .card-header {
    .title {
      font-weight: 600;
      font-size: 16px;
    }
  }

  .communication-content {
    line-height: 1.8;

    p {
      margin: 12px 0;
      color: #333;
      font-size: 15px;
    }
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
