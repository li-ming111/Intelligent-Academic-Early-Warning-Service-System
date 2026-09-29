<template>
  <div class="counselor-credit-monitor">
    <div class="page-header">
      <h1>班级学分监控</h1>
      <p>班级学分达标率追踪和不足学生警示</p>
    </div>

    <!-- 接口失败时明确告知，避免页面上的 0 被当成真实统计结果 -->
    <el-alert v-if="loadError" type="error" show-icon :closable="false"
              title="统计数据加载失败" description="未能获取班级学分统计，当前显示的 0 并非真实数据，请稍后重试。" />

    <!-- 整体统计 -->
    <div class="stats-grid">
      <div class="stat-box">
        <div class="stat-title">班级总数</div>
        <div class="stat-value">{{ monitor.totalClasses || 0 }}</div>
        <div class="stat-hint">个</div>
      </div>
      <div class="stat-box">
        <div class="stat-title">整体达标率</div>
        <div class="stat-value">{{ monitor.overallAchievementRate || 0 }}%</div>
        <div class="stat-hint">百分比</div>
      </div>
      <div class="stat-box">
        <div class="stat-title">优秀班级</div>
        <div class="stat-value" style="color: #67c23a;">{{ monitor.excellentClasses || 0 }}</div>
        <div class="stat-hint">个(>80%)</div>
      </div>
      <div class="stat-box">
        <div class="stat-title">风险班级</div>
        <div class="stat-value" style="color: #f56c6c;">{{ monitor.lowClasses || 0 }}</div>
        <div class="stat-hint">个(<60%)</div>
      </div>
    </div>

    <!-- 班级达标率对比 -->
    <el-card style="margin-bottom: 20px;">
      <template #header>
        <div class="card-header">班级达标率对比</div>
      </template>

      <el-table :data="monitor.classMonitors || []" stripe v-loading="loading">
        <el-table-column prop="className" label="班级名称" width="150"></el-table-column>
        <el-table-column prop="studentCount" label="学生数" width="100"></el-table-column>
        <el-table-column prop="creditAchievementCount" label="达标数" width="100"></el-table-column>
        <el-table-column label="达标率" width="200">
          <template #default="{ row }">
            <el-progress 
              :percentage="parseInt(row.achievementRate || 0)"
              :color="row.achievementRate >= 80 ? '#67c23a' : (row.achievementRate >= 60 ? '#2563eb' : '#f56c6c')"
              style="width: 100%;"
            ></el-progress>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="100">
          <template #default="{ row }">
            <el-tag :type="row.achievementRate >= 80 ? 'success' : (row.achievementRate >= 60 ? 'warning' : 'danger')">
              {{ row.achievementRate >= 80 ? '优秀' : (row.achievementRate >= 60 ? '一般' : '风险') }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 学分不足学生 -->
    <el-card>
      <template #header>
        <div class="card-header">学分不足学生</div>
      </template>

      <el-table :data="insufficientStudents" stripe v-loading="loading">
        <el-table-column prop="studentName" label="学生名称" width="120"></el-table-column>
        <el-table-column prop="studentId" label="学号" width="120"></el-table-column>
        <el-table-column prop="score" label="当前学分" width="100">
          <template #default="{ row }">
            <span style="color: #f56c6c; font-weight: bold;">{{ row.score }}</span>
          </template>
        </el-table-column>
        <el-table-column label="风险等级" width="100">
          <template #default="{ row }">
            <el-tag :type="row.riskLevel === 'red' ? 'danger' : 'warning'">
              {{ row.riskLevel === 'red' ? '高风险' : '中风险' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remarks" label="备注" min-width="200"></el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="viewStudent(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="totalInsufficientStudents"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 20px;"
        @size-change="handlePageSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <!-- 学生详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="学生学分详情" width="600px">
      <div v-if="selectedStudent" class="student-detail">
        <p><strong>学生名称：</strong>{{ selectedStudent.studentName }}</p>
        <p><strong>学号：</strong>{{ selectedStudent.studentId }}</p>
        <p><strong>当前学分：</strong>{{ selectedStudent.score }}</p>
        <p><strong>风险等级：</strong>
          <el-tag :type="selectedStudent.riskLevel === 'red' ? 'danger' : 'warning'">
            {{ selectedStudent.riskLevel === 'red' ? '高风险' : '中风险' }}
          </el-tag>
        </p>
        <p><strong>建议措施：</strong></p>
        <p style="color: #666; margin-left: 20px;">
          {{ selectedStudent.riskLevel === 'red' ? 
            '学生学分严重不足，建议立即进行个性化学业规划和重点指导，加强学业监督。' :
            '学生学分略有不足，建议进行学业指导和学分预测，制定追赶计划。'
          }}
        </p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { counselorAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'

const monitor = ref({})
const insufficientStudents = ref([])
const loading = ref(false)
/** 统计接口是否失败。失败时页面显示明确提示，而不是静默展示 0。 */
const loadError = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const totalInsufficientStudents = ref(0)
const detailDialogVisible = ref(false)
const selectedStudent = ref(null)

onMounted(async () => {
  await loadCreditMonitor()
  await loadInsufficientStudents()
})

// 加载学分监控数据
const loadCreditMonitor = async () => {
  try {
    const counselorId = localStorage.getItem('counselorId') || getUserId()
    if (!counselorId) return
    
    try {
      const response = await counselorAPI.getCreditMonitor(counselorId)
      if (response) {
        monitor.value = response
        loadError.value = false
      }
    } catch (error) {
      // 从前这里填入 {totalStudents:45, creditSufficient:40, ...} 作为"默认数据"。
      // 那组数字没有任何来源，而且模板根本不读它们（模板读的是 classMonitors 等），
      // 唯一的实际效果是让接口失败看起来像加载成功 —— 页面显示 0，但没有任何错误提示。
      console.warn('学分监控API不可用', error)
      loadError.value = true
      monitor.value = {}
    }
  } catch (error) {
    console.error('加载学分监控数据失败:', error)
  }
}

// 加载学分不足学生
const loadInsufficientStudents = async () => {
  try {
    const counselorId = localStorage.getItem('counselorId') || getUserId()
    if (!counselorId) return
    
    try {
      const response = await counselorAPI.getCreditInsufficientStudents(counselorId, currentPage.value, pageSize.value)
      // 后端返回的是分页对象 {total, page, size, list}。
      // 从前这里用 `Array.isArray(response)` 判断，永远为 false ——
      // 表格恒为空，而 total 也永远是 0，翻页器形同虚设。
      // 同时兼容旧的数组形态，避免接口版本不一致时整页空白。
      if (Array.isArray(response)) {
        insufficientStudents.value = response
        totalInsufficientStudents.value = response.length
      } else if (response) {
        const list = response.list ?? response.records ?? response.data ?? []
        insufficientStudents.value = Array.isArray(list) ? list : []
        totalInsufficientStudents.value = response.total ?? insufficientStudents.value.length
      }
    } catch (error) {
      // 后端接口不可用
      console.error('学分不足API不可用:', error)
      insufficientStudents.value = []
      totalInsufficientStudents.value = 0
    }
  } catch (error) {
    console.error('加载学分不足学生失败:', error)
  }
}

// 分页处理
const handlePageChange = () => {
  loadInsufficientStudents()
}

const handlePageSizeChange = () => {
  currentPage.value = 1
  loadInsufficientStudents()
}

// 查看学生详情
const viewStudent = (row) => {
  selectedStudent.value = row
  detailDialogVisible.value = true
}
</script>

<style scoped>
.counselor-credit-monitor {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0 0 8px 0;
  font-size: 24px;
  color: #333;
}

.page-header p {
  margin: 0;
  color: #999;
  font-size: 14px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 15px;
  margin-bottom: 20px;
}

.stat-box {
  background: white;
  padding: 20px;
  border-radius: 8px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.stat-title {
  color: #999;
  font-size: 12px;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #409eff;
  margin-bottom: 4px;
}

.stat-hint {
  color: #999;
  font-size: 12px;
}

.card-header {
  font-size: 16px;
  font-weight: bold;
  color: #333;
}

.student-detail {
  line-height: 1.8;
}

.student-detail p {
  margin: 10px 0;
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
