<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="学院与专业" :show-back="false" />

    <filter-bar v-model="tab" :options="tabs" />

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <!-- ---------- 学院 ---------- -->
        <template v-if="tab === 'colleges'">
          <view class="toolbar">
            <text class="toolbar__title">共 {{ colleges.length }} 个学院</text>
            <view class="toolbar__add" @tap="openCollegeForm(null)">
              <app-icon name="plus" :size="32" color="#fff" />
              <text class="toolbar__add-text">新建学院</text>
            </view>
          </view>

          <empty-state v-if="!colleges.length" text="暂无学院" hint="用右上角新建一个" icon="building" />

          <view v-else class="card card--flush">
            <view
              v-for="(c, i) in colleges"
              :key="c.id"
              class="col"
              :class="{ 'col--divided': i > 0 }"
              @tap="openCollege(c)"
            >
              <view class="col__main">
                <view class="col__top">
                  <text class="col__name">{{ c.name }}</text>
                  <text class="col__code">{{ c.code }}</text>
                </view>
                <text class="col__desc">{{ c.description || '无描述' }}</text>
                <!-- 三个数是**从 /admin/colleges 列表接口**来的。
                     单学院接口 /colleges/{id} 返回的是 College 实体，这三个字段是 null ——
                     详情页不能重新请求一次再拿它们，会变成 "--"。 -->
                <text class="col__counts">
                  学生 {{ n(c.studentCount) }} · 教师 {{ n(c.teacherCount) }} · 辅导员 {{ n(c.counselorCount) }}
                </text>
              </view>
              <app-icon name="chevron" :size="34" color="#cbd5e1" />
            </view>
          </view>

          <text class="note">学院人数按账号归属统计；尚未分配账号的学院显示为 0。</text>
        </template>

        <!-- ---------- 专业 ---------- -->
        <template v-else>
          <view class="toolbar">
            <text class="toolbar__title">共 {{ majors.length }} 个专业</text>
            <view class="toolbar__add" :class="{ 'toolbar__add--off': !colleges.length }" @tap="openMajorForm(null)">
              <app-icon name="plus" :size="32" color="#fff" />
              <text class="toolbar__add-text">新建专业</text>
            </view>
          </view>

          <empty-state v-if="!majors.length" text="暂无专业" hint="专业必须挂在某个学院下" icon="doc" />

          <!-- 按学院分组：接口是平铺的 12 条，但人看专业是按学院找的 -->
          <view v-for="g in majorGroups" :key="g.collegeId" class="section">
            <section-title :title="g.collegeName" :extra="`${g.rows.length} 个专业`" />
            <view class="card card--flush">
              <view
                v-for="(m, i) in g.rows"
                :key="m.id"
                class="maj"
                :class="{ 'maj--divided': i > 0 }"
                @tap="openMajor(m)"
              >
                <view class="maj__main">
                  <view class="maj__top">
                    <text class="maj__name">{{ m.name }}</text>
                    <text v-if="m.shortName" class="tag tag--info">{{ m.shortName }}</text>
                  </view>
                  <text class="maj__meta">专业代码 {{ m.code || '--' }}{{ m.description ? ' · ' + m.description : '' }}</text>
                </view>
                <app-icon name="chevron" :size="34" color="#cbd5e1" />
              </view>
            </view>
          </view>
        </template>
      </template>
    </view>

    <app-tab-bar role="admin" />

    <!-- ============ 抽屉 ============ -->
    <detail-sheet v-model="sheetOpen" :title="sheetTitle" :height="mode === 'college' ? '76vh' : '64vh'">
      <!-- ---------- 学院详情 ---------- -->
      <view v-if="mode === 'college' && currentCollege" class="sheet">
        <view class="facts">
          <view class="facts__row">
            <text class="facts__k">学院编码</text>
            <text class="facts__v">{{ currentCollege.code || '--' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">说明</text>
            <text class="facts__v">{{ currentCollege.description || '--' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">学生 / 教师</text>
            <text class="facts__v">{{ n(currentCollege.studentCount) }} / {{ n(currentCollege.teacherCount) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">辅导员</text>
            <text class="facts__v">{{ n(currentCollege.counselorCount) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">创建时间</text>
            <text class="facts__v">{{ formatDate(currentCollege.createdAt, true) }}</text>
          </view>
        </view>

        <section-title plain title="该学院的专业" :extra="`${collegeMajors.length} 个`" />
        <empty-state
          v-if="!collegeMajors.length"
          text="这个学院还没有专业"
          hint="用下方「新增专业」挂一个上去"
          icon="doc"
          :top="30"
        />
        <view v-else class="card card--flush">
          <view
            v-for="(m, i) in collegeMajors"
            :key="m.id"
            class="maj"
            :class="{ 'maj--divided': i > 0 }"
            @tap="openMajor(m)"
          >
            <view class="maj__main">
              <text class="maj__name">{{ m.name }}</text>
              <text class="maj__meta">专业代码 {{ m.code || '--' }}</text>
            </view>
            <app-icon name="chevron" :size="34" color="#cbd5e1" />
          </view>
        </view>
      </view>

      <!-- ---------- 专业详情 ---------- -->
      <view v-else-if="mode === 'major' && currentMajor" class="sheet">
        <view class="facts">
          <view class="facts__row">
            <text class="facts__k">所属学院</text>
            <text class="facts__v">{{ collegeNameOf(currentMajor.collegeId) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">专业代码</text>
            <text class="facts__v">{{ currentMajor.code || '--' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">简称</text>
            <text class="facts__v">{{ currentMajor.shortName || '--' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">说明</text>
            <text class="facts__v">{{ currentMajor.description || '--' }}</text>
          </view>
        </view>
        <text class="note">专业下的学生人数请在「用户管理」中按学院查看。</text>
      </view>

      <!-- ---------- 学院表单 ---------- -->
      <view v-else-if="mode === 'college-form'" class="sheet">
        <view class="field">
          <text class="field__label">学院名称</text>
          <input v-model="form.name" class="field__input" placeholder="如 软件学院" placeholder-class="field__ph" />
        </view>
        <view class="field">
          <text class="field__label">学院编码</text>
          <input v-model="form.code" class="field__input" placeholder="如 SOFT" placeholder-class="field__ph" />
        </view>
        <view class="field">
          <text class="field__label">说明</text>
          <input v-model="form.description" class="field__input" placeholder="选填" placeholder-class="field__ph" />
        </view>
      </view>

      <!-- ---------- 专业表单 ---------- -->
      <view v-else-if="mode === 'major-form'" class="sheet">
        <view class="field">
          <text class="field__label">所属学院</text>
          <view class="opts">
            <view
              v-for="c in colleges"
              :key="c.id"
              class="opt"
              :class="{ 'opt--on': form.collegeId === c.id }"
              @tap="form.collegeId = c.id"
            >{{ c.name }}</view>
          </view>
        </view>
        <view class="field">
          <text class="field__label">专业名称</text>
          <input v-model="form.name" class="field__input" placeholder="如 软件工程" placeholder-class="field__ph" />
        </view>
        <view class="field">
          <text class="field__label">专业代码</text>
          <input v-model="form.code" class="field__input" placeholder="如 01" placeholder-class="field__ph" />
        </view>
        <view class="field">
          <text class="field__label">简称</text>
          <input v-model="form.shortName" class="field__input" placeholder="如 软工" placeholder-class="field__ph" />
        </view>
        <view class="field">
          <text class="field__label">说明</text>
          <input v-model="form.description" class="field__input" placeholder="选填" placeholder-class="field__ph" />
        </view>
        <text class="note">专业代码只在学院内唯一，不同学院可能存在相同代码。</text>
      </view>

      <template #footer>
        <template v-if="mode === 'college'">
          <view class="acts">
            <view class="act" @tap="openMajorForm(null, currentCollege.id)">新增专业</view>
            <view class="act act--danger" @tap="deleteCollege">删除学院</view>
          </view>
          <view class="btn-primary footer-btn" @tap="openCollegeForm(currentCollege)">编辑学院信息</view>
        </template>

        <template v-else-if="mode === 'major'">
          <view class="acts">
            <view class="act act--danger" @tap="deleteMajor">删除专业</view>
          </view>
          <view class="btn-primary footer-btn" @tap="openMajorForm(currentMajor)">编辑专业信息</view>
        </template>

        <template v-else>
          <view class="form-acts">
            <view class="form-acts__cancel" @tap="sheetOpen = false">取消</view>
            <view class="btn-primary form-acts__ok" :class="{ 'btn-primary--disabled': submitting }" @tap="submit">
              {{ submitting ? '提交中…' : (editingId ? '保存' : '创建') }}
            </view>
          </view>
        </template>
      </template>
    </detail-sheet>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { adminAPI } from '@/services/api.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { formatDate, number } from '@/utils/format.js'

const themeStyle = roleThemeStyle('admin')

const tabs = [
  { label: '学院', value: 'colleges' },
  { label: '专业', value: 'majors' }
]
const tab = ref('colleges')

const loading = ref(true)
const colleges = ref([])
const majors = ref([])

const sheetOpen = ref(false)
/** college | major | college-form | major-form */
const mode = ref('college')
const currentCollege = ref(null)
const currentMajor = ref(null)
const collegeMajors = ref([])
const editingId = ref(null)
const submitting = ref(false)
const form = ref({ name: '', code: '', description: '', shortName: '', collegeId: null })

const sheetTitle = computed(() => {
  if (mode.value === 'college') return currentCollege.value ? currentCollege.value.name : '学院'
  if (mode.value === 'major') return currentMajor.value ? currentMajor.value.name : '专业'
  if (mode.value === 'college-form') return editingId.value ? '编辑学院' : '新建学院'
  return editingId.value ? '编辑专业' : '新建专业'
})

/** 平铺的 12 个专业按学院分组。没有学院的专业单列一组，不藏起来。 */
const majorGroups = computed(() => {
  const map = new Map()
  colleges.value.forEach(c => map.set(c.id, { collegeId: c.id, collegeName: c.name, rows: [] }))
  const orphan = { collegeId: 0, collegeName: '未挂学院的代码', rows: [] }
  majors.value.forEach(m => {
    const g = map.get(m.collegeId)
    if (g) g.rows.push(m)
    else orphan.rows.push(m)
  })
  const list = [...map.values()].filter(g => g.rows.length)
  if (orphan.rows.length) list.push(orphan)
  return list
})

function n(v) {
  return number(v, 0)
}

function collegeNameOf(id) {
  const c = colleges.value.find(x => x.id === id)
  return c ? c.name : (id ? `未匹配的学院 id ${id}` : '--')
}

function toast(msg, ms = 2600) {
  uni.showToast({ title: String(msg || ''), icon: 'none', duration: ms })
}

function confirm(content, title = '确认') {
  return new Promise(resolve => {
    uni.showModal({
      title,
      content,
      success: r => resolve(!!r.confirm),
      fail: () => resolve(false)
    })
  })
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [c, m] = await Promise.all([
      adminAPI.getColleges().catch(e => { console.error('[admin colleges] 学院失败:', e); return null }),
      adminAPI.getMajors().catch(e => { console.error('[admin colleges] 专业失败:', e); return null })
    ])
    colleges.value = Array.isArray(c) ? c : []
    majors.value = Array.isArray(m) ? m : []
  } finally {
    loading.value = false
  }
}

async function reloadMajors() {
  try {
    const m = await adminAPI.getMajors()
    majors.value = Array.isArray(m) ? m : []
  } catch (e) {
    console.error('[admin colleges] 专业重载失败:', e)
  }
}

async function openCollege(c) {
  currentCollege.value = c
  mode.value = 'college'
  collegeMajors.value = []
  sheetOpen.value = true
  try {
    const res = await adminAPI.getMajorsByCollege(c.id)
    collegeMajors.value = Array.isArray(res) ? res : []
  } catch (e) {
    console.error('[admin colleges] 学院专业失败:', e)
  }
}

function openMajor(m) {
  currentMajor.value = m
  mode.value = 'major'
  sheetOpen.value = true
}

function openCollegeForm(c) {
  editingId.value = c ? c.id : null
  form.value = {
    name: c ? c.name || '' : '',
    code: c ? c.code || '' : '',
    description: c ? c.description || '' : '',
    shortName: '',
    collegeId: c ? c.id : null
  }
  mode.value = 'college-form'
  sheetOpen.value = true
}

function openMajorForm(m, presetCollegeId) {
  editingId.value = m ? m.id : null
  form.value = {
    name: m ? m.name || '' : '',
    code: m ? m.code || '' : '',
    description: m ? m.description || '' : '',
    shortName: m ? m.shortName || '' : '',
    collegeId: m ? m.collegeId : (presetCollegeId || (colleges.value[0] ? colleges.value[0].id : null))
  }
  mode.value = 'major-form'
  sheetOpen.value = true
}

async function submit() {
  if (submitting.value) return
  const f = form.value
  if (!String(f.name || '').trim()) {
    toast(mode.value === 'college-form' ? '请填写学院名称' : '请填写专业名称')
    return
  }
  const isCollege = mode.value === 'college-form'
  if (!isCollege && !f.collegeId) {
    toast('请选择所属学院')
    return
  }

  submitting.value = true
  try {
    if (isCollege) {
      const body = {
        name: String(f.name).trim(),
        code: String(f.code || '').trim(),
        description: String(f.description || '').trim()
      }
      if (editingId.value) await adminAPI.updateCollege(editingId.value, body)
      else await adminAPI.createCollege(body)
    } else {
      const body = {
        collegeId: f.collegeId,
        name: String(f.name).trim(),
        code: String(f.code || '').trim(),
        shortName: String(f.shortName || '').trim(),
        description: String(f.description || '').trim()
      }
      if (editingId.value) await adminAPI.updateMajor(editingId.value, body)
      else await adminAPI.createMajor(body)
    }
    toast(editingId.value ? '已保存' : '已创建')
    sheetOpen.value = false
    await load()
    if (!isCollege) await reloadMajors()
  } catch (e) {
    toast((e && e.message) || '提交失败', 3200)
  } finally {
    submitting.value = false
  }
}

async function deleteCollege() {
  const c = currentCollege.value
  if (!c) return
  const people = n(c.studentCount) + n(c.teacherCount) + n(c.counselorCount)
  const extra = people > 0
    ? `\n注意：这个学院下还挂着 ${people} 个账号，删掉学院后他们的学院归属会失去对应记录。`
    : ''
  const ok = await confirm(`删除「${c.name}」？${extra}`, '删除学院')
  if (!ok) return
  try {
    await adminAPI.deleteCollege(c.id)
    toast('已删除')
    sheetOpen.value = false
    await load()
  } catch (e) {
    toast((e && e.message) || '删除失败', 3200)
  }
}

async function deleteMajor() {
  const m = currentMajor.value
  if (!m) return
  const ok = await confirm(`删除专业「${m.name}」？该专业下的学生归属不会被自动改写。`, '删除专业')
  if (!ok) return
  try {
    await adminAPI.deleteMajor(m.id)
    toast('已删除')
    sheetOpen.value = false
    // 专业详情是从"学院详情"里点进来的，两边都要刷新，否则关掉抽屉还看得见刚删的那条
    await load()
    if (currentCollege.value) {
      const res = await adminAPI.getMajorsByCollege(currentCollege.value.id).catch(() => null)
      collegeMajors.value = Array.isArray(res) ? res : []
    }
  } catch (e) {
    toast((e && e.message) || '删除失败', 3200)
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-sm;
}

.toolbar {
  display: flex;
  align-items: center;
  padding: 0 $page-padding $spacing-sm;
}

.toolbar__title {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.toolbar__add {
  @include tap-area;

  display: flex;
  align-items: center;
  height: 64rpx;
  padding: 0 $spacing-lg;
  border-radius: $radius-pill;
  background: var(--color-primary);
}

.toolbar__add--off {
  opacity: 0.45;
}

.toolbar__add-text {
  margin-left: 6rpx;
  font-size: $font-sm;
  color: #fff;
  font-weight: 500;
}

.section {
  margin-top: $spacing-lg;
}

.card {
  padding: 0;
  margin: 0 $page-padding;
  @include card;
}

.card--flush {
  overflow: hidden;
}

/* ---------- 学院行 ---------- */
.col {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
}

.col--divided {
  border-top: 1rpx solid $color-divider;
}

.col:active {
  background: $color-bg;
}

.col__main {
  flex: 1;
  min-width: 0;
}

.col__top {
  display: flex;
  align-items: baseline;
}

.col__name {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.col__code {
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: var(--color-primary);
}

.col__desc {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
  @include ellipsis;
}

.col__counts {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-secondary;
}

/* ---------- 专业行 ---------- */
.maj {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
}

.maj--divided {
  border-top: 1rpx solid $color-divider;
}

.maj:active {
  background: $color-bg;
}

.maj__main {
  flex: 1;
  min-width: 0;
}

.maj__top {
  display: flex;
  align-items: center;
}

.maj__name {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.maj__top .tag {
  margin-left: $spacing-sm;
}

.maj__meta {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
  @include ellipsis;
}

/* ---------- 抽屉 ---------- */
.sheet {
  padding-top: $spacing-md;
  padding-bottom: $spacing-xl;
}

.facts {
  padding: 0 $spacing-lg;
  border-radius: $radius-md;
  background: $color-bg;
}

.facts__row {
  display: flex;
  align-items: flex-start;
  padding: $spacing-md 0;
}

.facts__k {
  width: 180rpx;
  flex-shrink: 0;
  font-size: $font-xs;
  color: $color-text-hint;
}

.facts__v {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  color: $color-text;
  text-align: right;
  word-break: break-all;
}

.field {
  margin-bottom: $spacing-lg;
}

.field__label {
  display: block;
  margin-bottom: $spacing-xs;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.field__input {
  height: 80rpx;
  padding: 0 $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  color: $color-text;
}

.field__ph {
  color: $color-text-hint;
}

.opts {
  display: flex;
  flex-wrap: wrap;
}

.opt {
  height: $tap-min;
  padding: 0 $spacing-lg;
  margin: 0 $spacing-sm $spacing-sm 0;
  border-radius: $radius-pill;
  background: $color-bg;
  border: 1rpx solid $color-border;
  font-size: $font-sm;
  color: $color-text-secondary;
  @include flex-center;
}

.opt--on {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
  font-weight: 500;
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

.sheet .note {
  margin-left: 0;
  margin-right: 0;
}

/* ---------- 底部 ---------- */
.acts {
  display: flex;
  flex-wrap: wrap;
  margin-bottom: $spacing-md;
}

.act {
  height: $tap-min;
  padding: 0 $spacing-lg;
  margin: 0 $spacing-sm $spacing-sm 0;
  border-radius: $radius-md;
  background: $color-bg;
  border: 1rpx solid $color-border;
  font-size: $font-sm;
  color: $color-text-secondary;
  @include flex-center;
}

.act--danger {
  background: rgba(239, 68, 68, 0.1);
  border-color: transparent;
  color: $color-danger;
}

.footer-btn {
  width: 100%;
}

.form-acts {
  display: flex;
  align-items: center;
}

.form-acts__cancel {
  width: 200rpx;
  height: $tap-min;
  margin-right: $spacing-md;
  border-radius: var(--radius-md);
  background: $color-bg;
  color: $color-text-secondary;
  font-size: $font-md;
  @include flex-center;
}

.form-acts__ok {
  flex: 1;
  min-width: 0;
}
</style>
