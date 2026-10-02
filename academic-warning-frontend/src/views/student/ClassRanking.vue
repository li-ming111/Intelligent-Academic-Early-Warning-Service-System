<template>
  <div class="class-ranking-container">
    <div class="header">
      <h1>班级排名排行榜</h1>
      <p>显示班级全体学生的学业排名</p>
    </div>

    <!-- 排序选项 -->
    <div class="sort-section">
      <el-select v-model="sortBy" placeholder="选择排序方式" class="sort-select">
        <el-option 
          v-for="option in sortOptions" 
          :key="option.value" 
          :label="option.label" 
          :value="option.value"
        />
      </el-select>
    </div>

    <!-- 排名列表 -->
    <div class="ranking-list">
      <div v-if="sortedMembers.length === 0" class="empty-state">
        <el-empty description="暂无班级排名数据"></el-empty>
      </div>
      <div v-else class="rankings">
        <div 
          v-for="(member, index) in sortedMembers" 
          :key="member.id" 
          class="ranking-item"
          :class="[getRankClass(index), { 'is-current': member.isCurrent }]"
        >
          <div class="rank-badge">
            <span class="rank-number">{{ member.rank === null ? '—' : member.rank }}</span>
          </div>

          <div class="member-details">
            <div class="member-name-section">
              <h3 class="member-name">{{ member.name }}</h3>
              <span v-if="member.isCurrent" class="badge-current">你</span>
            </div>
            <p class="student-id">学号: {{ member.studentId }}</p>
          </div>

          <div class="member-scores">
            <!-- 该同学把「成绩可见范围」设成了非公开（`student_profile.privacy_level`），
                 后端在排行榜里遮蔽了他的 GPA 与学分。行还在（名次要对得上），
                 只是数值为空 —— 这里如实写「未公开」，而不是显示一个像出错的 '—'。 -->
            <template v-if="member.gpaHidden">
              <div class="score-item">
                <span class="score-label">GPA</span>
                <span class="score-value muted">未公开</span>
              </div>
            </template>
            <template v-else>
              <div class="score-item">
                <span class="score-label">GPA</span>
                <span class="score-value">{{ fmtGpa(member.gpa) }}</span>
              </div>
              <div class="score-item">
                <span class="score-label">学分</span>
                <span class="score-value">{{ fmtCredits(member.credits) }}</span>
              </div>
            </template>
          </div>

          <!-- 2026-10-01 第二十一轮删：这里原来挂着一枚 `{{ member.status }}` 标签
               （优秀/良好/及格/警告）。班级榜响应的行只有 profileId/studentNo/
               studentName/className/gpa/gradedCredits，没有 status ⇒ 每行渲染出一个
               灰色空标签，getStatusType 的映射表也没有任何输入能命中。
               GPA 分档是另立口径的事（几分为优秀？），没定之前不编，先删。 -->
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { studentAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'
import { toClassMembers, fmtGpa, fmtCredits } from '@/utils/classRanking'

const sortBy = ref('rank')
const members = ref([])
const loading = ref(false)

const sortOptions = [
  { label: 'GPA排序', value: 'gpa' },
  { label: '学分排序', value: 'credits' },
  { label: '默认排序', value: 'rank' }
]

// 排序成员
// 缺值（GPA / 学分可能为 null）一律排到最后，不参与减法比较 —— `null - 3` 会变成 -3
// 而不是"更小"，把没成绩的同学排到榜首。
const sortedMembers = computed(() => {
  const sorted = [...members.value]

  if (sortBy.value === 'gpa') {
    sorted.sort((a, b) => numDesc(a.gpa, b.gpa))
  } else if (sortBy.value === 'credits') {
    sorted.sort((a, b) => numDesc(a.credits, b.credits))
  } else {
    sorted.sort((a, b) => numAsc(a.rank, b.rank))
  }

  return sorted
})

/** 数值升序；null/undefined 恒排最后（名次越小越靠前）。 */
function numAsc(a, b) {
  const na = typeof a === 'number' ? a : null
  const nb = typeof b === 'number' ? b : null
  if (na === null && nb === null) return 0
  if (na === null) return 1
  if (nb === null) return -1
  return na - nb
}

/**
 * 数值降序；null/undefined 恒排最后。
 * 不能写成 `-numAsc(a, b)` —— 那样会把"缺值排最后"翻成"缺值排最前"。
 */
function numDesc(a, b) {
  const na = typeof a === 'number' ? a : null
  const nb = typeof b === 'number' ? b : null
  if (na === null && nb === null) return 0
  if (na === null) return 1
  if (nb === null) return -1
  return nb - na
}

// 获取排名样式
const getRankClass = (index) => {
  if (index === 0) return 'rank-1'
  if (index === 1) return 'rank-2'
  if (index === 2) return 'rank-3'
  return 'rank-normal'
}

// 获取状态类型
const getStatusType = (status) => {
  const typeMap = {
    '优秀': 'success',
    '良好': 'info',
    '及格': 'warning',
    '警告': 'danger'
  }
  return typeMap[status] || 'info'
}

// 加载班级排名
//
// ⚠️ 2026-10-01 第二十一轮改：`/students/class-ranking/{uid}` 回的是
// `{studentId, classId, semester, rank, total, list[]}` —— 一个**对象**，全班同学在 `list` 里。
// 原实现断言 `Array.isArray(response) && response.length > 0`，恒为假 ⇒ 榜单永远空，
// 而 `total` / 自己的 `rank` 这些真值也跟着被丢掉。映射统一走 `@/utils/classRanking`。
const loadClassRanking = async () => {
  loading.value = true
  try {
    const userId = getUserId()
    if (!userId) {
      console.error('[ClassRanking] 无法获取用户ID')
      return
    }

    const response = await studentAPI.getClassRankingList(userId)
    members.value = toClassMembers(response)
  } catch (error) {
    console.error('[ClassRanking] 加载班级排名异常:', error)
    ElMessage.error(error?.message ? `加载班级排名失败：${error.message}` : '加载班级排名失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadClassRanking()
})
</script>

<style scoped>
.class-ranking-container {
  padding: 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%);
  min-height: 100vh;
}

.header {
  margin-bottom: 30px;
}

.header h1 {
  font-size: 28px;
  margin: 0 0 8px 0;
  color: #2c3e50;
}

.header p {
  margin: 0;
  color: #666;
  font-size: 14px;
}

/* 排序部分 */
.sort-section {
  margin-bottom: 20px;
  display: flex;
  gap: 10px;
}

.sort-select {
  width: 150px;
}

/* 排名列表 */
.ranking-list {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.empty-state {
  text-align: center;
  padding: 40px 20px;
}

.rankings {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ranking-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 10px;
  border-left: 4px solid #e8ecf1;
  transition: all 0.3s ease;
}

.ranking-item:hover {
  background: #f0f2f7;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.ranking-item.is-current {
  background: linear-gradient(135deg, #f5f3ff 0%, #efe6ff 100%);
  border-left-color: #667eea;
}

.ranking-item.rank-1 {
  border-left-color: #ffd700;
  background: linear-gradient(135deg, #fffef0 0%, #fffde8 100%);
}

.ranking-item.rank-2 {
  border-left-color: #c0c0c0;
  background: linear-gradient(135deg, #fefefe 0%, #f9f9f9 100%);
}

.ranking-item.rank-3 {
  border-left-color: #cd7f32;
  background: linear-gradient(135deg, #fffbf5 0%, #ffece0 100%);
}

/* 排名徽章 */
.rank-badge {
  min-width: 60px;
  height: 60px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: white;
  font-size: 24px;
}

.rank-1 .rank-badge {
  background: linear-gradient(135deg, #ffd700 0%, #ffed4e 100%);
  color: #333;
}

.rank-2 .rank-badge {
  background: linear-gradient(135deg, #c0c0c0 0%, #e8e8e8 100%);
  color: #666;
}

.rank-3 .rank-badge {
  background: linear-gradient(135deg, #cd7f32 0%, #dd8f3d 100%);
}

.rank-normal .rank-badge {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

/* 成员详情 */
.member-details {
  flex: 1;
  min-width: 0;
}

.member-name-section {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.member-name {
  margin: 0;
  font-size: 16px;
  color: #2c3e50;
  font-weight: 600;
}

.badge-current {
  background: #667eea;
  color: white;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.student-id {
  margin: 0;
  font-size: 12px;
  color: #999;
}

/* 成绩部分 */
.member-scores {
  display: flex;
  gap: 20px;
  min-width: 120px;
}

.score-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.score-label {
  font-size: 11px;
  color: #999;
  margin-bottom: 4px;
}

.score-value {
  font-size: 16px;
  font-weight: 600;
  color: #667eea;
}

/* 隐私遮蔽（privacy_level 非公开）：写成说明文字，不抢眼 */
.score-value.muted {
  font-size: 13px;
  font-weight: 400;
  color: #94a3b8;
}

/* 状态徽章 */
.status-badge {
  min-width: 80px;
  text-align: center;
}

/* 响应式 */
@media (max-width: 768px) {
  .ranking-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .member-scores {
    width: 100%;
    justify-content: space-between;
  }

  .rank-badge {
    min-width: 50px;
    height: 50px;
    font-size: 20px;
  }
}
</style>
