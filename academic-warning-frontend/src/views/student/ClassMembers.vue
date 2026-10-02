<template>
  <div class="class-members-container">
    <div class="header">
      <h1>班级成员列表</h1>
      <p>当前班级：{{ classIdentifier }}</p>
    </div>

    <!-- 搜索和筛选 -->
    <div class="filter-section">
      <el-input 
        v-model="searchQuery" 
        placeholder="搜索学生姓名或学号..." 
        class="search-input"
        clearable
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <!-- 成员列表 -->
    <div class="members-list">
      <div v-if="filteredMembers.length === 0" class="empty-state">
        <el-empty description="暂无班级成员数据"></el-empty>
      </div>
      <div v-else class="members-grid">
        <div v-for="member in filteredMembers" :key="member.id" class="member-card">
          <div class="member-header">
            <div class="member-avatar">{{ (member.name || '?').charAt(0) }}</div>
            <!-- 名次口径：GPA 严格高于该生的人数 + 1（并列同名次），与后端的班级名次同一套 -->
            <div class="member-rank">{{ member.rank === null ? '未排名' : '第 ' + member.rank + ' 名' }}</div>
          </div>
          <div class="member-info">
            <h3>{{ member.name }}<span v-if="member.isCurrent" class="self-tag">你</span></h3>
            <p class="student-id">{{ member.studentId }}</p>
            <!-- 原模板这里还有一行 `{{ member.major }}`：班级榜响应里没有专业字段，
                 每张卡都是空白行。删掉，不留空行 -->
          </div>
          <div class="member-stats">
            <!-- 同 ClassRanking：该同学把「成绩可见范围」设成了非公开，
                 后端遮蔽了他的 GPA 与学分，这里如实写「未公开」而不是 '—' -->
            <template v-if="member.gpaHidden">
              <div class="stat-item">
                <span class="label">GPA</span>
                <span class="value muted">未公开</span>
              </div>
            </template>
            <template v-else>
              <div class="stat-item">
                <span class="label">GPA</span>
                <span class="value">{{ fmtGpa(member.gpa) }}</span>
              </div>
              <div class="stat-item">
                <span class="label">学分</span>
                <span class="value">{{ fmtCredits(member.credits) }}</span>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { studentAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'
import { toClassMembers, fmtGpa, fmtCredits } from '@/utils/classRanking'

const searchQuery = ref('')
const classIdentifier = ref('班级成员')
const members = ref([])
const loading = ref(false)

// 筛选成员
const filteredMembers = computed(() => {
  if (!searchQuery.value) {
    return members.value
  }
  const query = searchQuery.value.toLowerCase()
  return members.value.filter(member =>
    (member.name || '').toLowerCase().includes(query) ||
    (member.studentId || '').toLowerCase().includes(query)
  )
})

// 加载班级成员
//
// ⚠️ 2026-10-01 第二十一轮改：从前这里调 `/students/class-members/{uid}` 并断言
// `Array.isArray(response)` —— 而那个端点回的是一个**对象**（本人的班级摘要，
// 实现是 `return getClassInfo(uid)`），断言恒为假 ⇒ 页面永远"暂无班级成员数据"，
// 拿到的那份摘要被无声丢掉。全班同学在 `/students/class-ranking/{uid}` 的 `list` 里，
// 这里改读它（字段映射统一放在 `@/utils/classRanking`，与排行榜页共用一份）。
const loadClassMembers = async () => {
  loading.value = true
  try {
    const userId = getUserId()
    if (!userId) {
      console.error('[ClassMembers] 无法获取用户ID')
      return
    }

    const response = await studentAPI.getClassRankingList(userId)
    const list = toClassMembers(response)
    members.value = list
    if (list.length && list[0].className) {
      classIdentifier.value = list[0].className
    }
  } catch (error) {
    // 后端返回 code!==200 时拦截器会 reject（reject 出来的已经是后端那句话），
    // 原实现只 console.error 不提示，页面上只看得到"暂无数据"，分不清是空还是坏了。
    console.error('[ClassMembers] 加载班级成员异常:', error)
    ElMessage.error(error?.message ? `加载班级成员失败：${error.message}` : '加载班级成员失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadClassMembers()
})
</script>

<style scoped>
.class-members-container {
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

/* 搜索和筛选 */
.filter-section {
  margin-bottom: 20px;
  display: flex;
  gap: 10px;
}

.search-input {
  max-width: 300px;
}

/* 成员列表 */
.members-list {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.empty-state {
  text-align: center;
  padding: 40px 20px;
}

.members-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.member-card {
  background: linear-gradient(135deg, #f8f9fa 0%, #ffffff 100%);
  border: 1px solid #e8ecf1;
  border-radius: 10px;
  padding: 16px;
  transition: all 0.3s ease;
  cursor: pointer;
}

.member-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
  border-color: #667eea;
}

.member-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.member-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 18px;
}

.member-rank {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 4px 8px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
}

.member-info {
  margin-bottom: 12px;
}

.member-info h3 {
  margin: 0 0 4px 0;
  font-size: 16px;
  color: #2c3e50;
  font-weight: 600;
}

.self-tag {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 10px;
  background: #667eea;
  color: #fff;
  font-size: 11px;
  font-weight: 500;
  vertical-align: middle;
}

.student-id {
  margin: 0 0 4px 0;
  font-size: 12px;
  color: #999;
}

.major {
  margin: 0;
  font-size: 12px;
  color: #666;
}

.member-stats {
  display: flex;
  justify-content: space-around;
  padding-top: 12px;
  border-top: 1px solid #e8ecf1;
}

.stat-item {
  text-align: center;
  flex: 1;
}

.stat-item .label {
  display: block;
  font-size: 11px;
  color: #999;
  margin-bottom: 4px;
}

.stat-item .value {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #667eea;
}

/* 隐私遮蔽（privacy_level 非公开） */
.stat-item .value.muted {
  font-size: 13px;
  font-weight: 400;
  color: #94a3b8;
}

/* 响应式 */
@media (max-width: 768px) {
  .members-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  }
}
</style>
