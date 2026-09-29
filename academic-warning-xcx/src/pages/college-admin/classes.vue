<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="班级管理" :show-back="false" />

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <view class="summary">
          <text class="summary__text">
            共 <text class="summary__num">{{ classes.length }}</text> 个班 ·
            有学生 <text class="summary__num">{{ withStudents }}</text> 个
          </text>
          <text class="summary__hint">点一行看花名册</text>
        </view>

        <empty-state
          v-if="!classes.length"
          text="本院暂无班级"
          hint="班级由管理员在「课程与班级」页建立"
          icon="building"
        />

        <view v-else class="card card--flush">
          <view
            v-for="(c, i) in classes"
            :key="c.id"
            class="cls"
            :class="{ 'cls--divided': i > 0 }"
            @tap="openRoster(c)"
          >
            <view class="cls__head">
              <text class="cls__name">{{ c.name || `班级 ${c.id}` }}</text>
              <text v-if="Number(c.warningCount) > 0" class="tag tag--warning">预警 {{ c.warningCount }}</text>
              <text v-else class="tag tag--pending">无预警</text>
            </view>
            <view class="cls__nums">
              <view class="num">
                <text class="num__v" :class="{ 'num__v--muted': !Number(c.studentCount) }">{{ c.studentCount || 0 }}</text>
                <text class="num__k">学生</text>
              </view>
              <view class="num">
                <text class="num__v" :class="{ 'num__v--muted': !Number(c.gradedStudentCount) }">
                  {{ c.gradedStudentCount || 0 }}
                </text>
                <text class="num__k">有成绩</text>
              </view>
              <view class="num">
                <text class="num__v" :class="gpaClass(c.avgGpa)">{{ gpaText(c.avgGpa) }}</text>
                <text class="num__k">平均绩点</text>
              </view>
            </view>
          </view>
        </view>

        <text class="note">
          「有成绩」是**有成绩记录的学生数**，跟「学生」不是一回事 —— 学生招进来了但一门课都还没录成绩时，
          两个数字会不一样。平均绩点为「--」表示这个班**一条成绩都没有**，不是绩点为 0。
        </text>
        <text class="note">
          建班表与学生表是两回事：学院可以在班级表里建满班，但学生要挂到班级下才算数。
          软件学院 6 个班里只有「计科2306班」有学生（24 人），其余 5 个班是空的；
          电子工程学院那两个班也是空的 —— 这是库里的实情，不是接口少给了数据。
        </text>
      </template>
    </view>

    <app-tab-bar role="collegeAdmin" />

    <!-- ============ 花名册 ============ -->
    <detail-sheet v-model="sheetOpen" :title="sheetTitle" height="78vh">
      <view class="sheet">
        <view v-if="rosterLoading" class="empty-tip">正在读取花名册…</view>
        <template v-else>
          <view v-if="!roster.length" class="empty-tip">这个班还没有学生。</view>
          <view v-else class="card card--flush">
            <view
              v-for="(s, i) in roster"
              :key="s.id"
              class="stu"
              :class="{ 'stu--divided': i > 0 }"
            >
              <view class="stu__head">
                <text class="stu__name">{{ s.name || `学生 ${s.id}` }}</text>
                <text class="stu__sid">{{ s.studentId || '未记录学号' }}</text>
              </view>
              <view class="stu__nums">
                <view class="num">
                  <text class="num__v" :class="gpaClass(s.gpa)">{{ gpaText(s.gpa) }}</text>
                  <text class="num__k">绩点</text>
                </view>
                <view class="num">
                  <text class="num__v">{{ s.passedCourses || 0 }} / {{ s.totalCourses || 0 }}</text>
                  <text class="num__k">通过 / 总课程</text>
                </view>
                <view class="num">
                  <text class="num__v" :class="{ 'num__v--danger': Number(s.failedCourses) > 0 }">{{ s.failedCourses || 0 }}</text>
                  <text class="num__k">不及格</text>
                </view>
                <view class="num">
                  <text class="num__v" :class="{ 'num__v--warn': Number(s.warningCount) > 0 }">{{ s.warningCount || 0 }}</text>
                  <text class="num__k">预警</text>
                </view>
              </view>
            </view>
          </view>

          <text class="note note--in">
            花名册接口**不校验这个班属不属于你的学院**。实测拿电子工程学院管理员的账号去请求软件学院
            计科2306班的班级 id，照样能拿到 24 个人的姓名与学号（只是成绩类字段因为按本院统计全是空）。
            这是服务端的越权读取，页面上挡不住，只作为已知问题记录。
          </text>
        </template>
      </view>
    </detail-sheet>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { collegeAdminAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'

const { userId } = useUser()
const themeStyle = roleThemeStyle('collegeAdmin')

const loading = ref(true)
const classes = ref([])

const sheetOpen = ref(false)
const rosterLoading = ref(false)
const roster = ref([])
const currentClass = ref(null)

const withStudents = computed(() => classes.value.filter(c => Number(c.studentCount) > 0).length)

const sheetTitle = computed(() => {
  if (!currentClass.value) return '班级花名册'
  return `${currentClass.value.name || '班级'} · ${currentClass.value.studentCount || 0} 人`
})

function gpaText(gpa) {
  return gpa === null || gpa === undefined ? '--' : Number(gpa).toFixed(2)
}

function gpaClass(gpa) {
  if (gpa === null || gpa === undefined) return 'num__v--muted'
  const n = Number(gpa)
  if (n < 1.5) return 'num__v--danger'
  if (n < 2.0) return 'num__v--warn'
  return ''
}

function toast(msg, ms = 2600) {
  uni.showToast({ title: String(msg || ''), icon: 'none', duration: ms })
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const list = await collegeAdminAPI.getClasses(userId)
    const rows = Array.isArray(list) ? list : []
    // 有学生的班排前面：空班挑不出信息，先让有用的可见
    classes.value = rows.slice().sort((a, b) => {
      const sa = Number(a.studentCount) || 0
      const sb = Number(b.studentCount) || 0
      if (sa !== sb) return sb - sa
      return String(a.name || '').localeCompare(String(b.name || ''), 'zh-Hans-CN')
    })
  } catch (e) {
    console.error('[college-admin classes] 班级失败:', e)
    classes.value = []
    toast((e && e.message) || '加载失败')
  } finally {
    loading.value = false
  }
}

async function openRoster(c) {
  currentClass.value = c
  roster.value = []
  rosterLoading.value = true
  sheetOpen.value = true
  try {
    const list = await collegeAdminAPI.getClassStudents(userId, c.id)
    roster.value = Array.isArray(list) ? list : []
  } catch (e) {
    console.error('[college-admin classes] 花名册失败:', e)
    toast((e && e.message) || '花名册加载失败')
  } finally {
    rosterLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-md;
  padding-bottom: $spacing-3xl;
}

.summary {
  display: flex;
  align-items: baseline;
  padding: 0 $page-padding $spacing-sm;
}

.summary__text {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.summary__num {
  font-weight: 700;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.summary__hint {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
}

.card {
  padding: 0;
  margin: 0 $page-padding;
  @include card;
}

.card--flush {
  overflow: hidden;
}

/* ---------- 班级行 ---------- */
.cls {
  padding: $spacing-lg;
}

.cls--divided {
  border-top: 1rpx solid $color-divider;
}

.cls:active {
  background: $color-bg;
}

.cls__head {
  display: flex;
  align-items: center;
}

.cls__name {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-sm;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.cls__nums {
  display: flex;
  margin-top: $spacing-md;
}

.num {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.num__v {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.num__v--danger {
  color: $color-danger;
}

.num__v--warn {
  color: $color-warning;
}

.num__v--muted {
  color: $color-text-hint;
  font-weight: 400;
}

.num__k {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: $color-text-hint;
}

/* ---------- 花名册 ---------- */
.sheet {
  padding-top: $spacing-md;
  padding-bottom: $spacing-xl;
}

.sheet .card {
  margin: 0;
}

.stu {
  padding: $spacing-md $spacing-lg;
}

.stu--divided {
  border-top: 1rpx solid $color-divider;
}

.stu__head {
  display: flex;
  align-items: baseline;
}

.stu__name {
  flex: 1;
  min-width: 0;
  font-size: $font-base;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.stu__sid {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
}

.stu__nums {
  display: flex;
  margin-top: $spacing-sm;
}

.empty-tip {
  display: block;
  padding: $spacing-md 0;
  font-size: $font-xs;
  color: $color-text-hint;
}

.note {
  display: block;
  margin: $spacing-lg $page-padding 0;
  padding: $spacing-md;
  border-radius: $radius-sm;
  background: rgba(245, 158, 11, 0.1);
  font-size: $font-xs;
  line-height: 1.6;
  color: #92400e;
}

.note--in {
  margin-left: 0;
  margin-right: 0;
}
</style>
