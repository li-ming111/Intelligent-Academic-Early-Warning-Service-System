<template>
  <div class="ai-insight">
    <div class="ai-head">
      <span class="ai-title">{{ title }}</span>
      <el-button size="small" :loading="loading" @click="run">
        {{ loading ? '分析中…' : (result ? '重新生成' : '开始分析') }}
      </el-button>
    </div>

    <!-- 失败就如实说失败。`error` 里装的是后端原话或诚实性闸给的人话
         （AI 没配 key 时是"没有配置 DASHSCOPE_KEY…"，**不是**一句伪装成结果的等待提示）。 -->
    <div v-if="error" class="ai-error">{{ error }}</div>
    <div v-else-if="loading" class="ai-empty">正在请求 AI 服务…</div>
    <div v-else-if="!result" class="ai-empty">{{ hint || '点击「开始分析」获取结果' }}</div>
    <div v-else class="ai-body">
      <slot :result="result">
        <div v-for="(block, i) in blocks" :key="i" class="ai-block">
          <template v-if="block.type === 'text'">
            <div v-if="block.label" class="ai-label">{{ block.label }}</div>
            <p class="ai-text">{{ block.value }}</p>
          </template>
          <template v-else-if="block.type === 'list'">
            <div class="ai-label">{{ block.label }}</div>
            <ul class="ai-list">
              <li v-for="(it, j) in block.value" :key="j">{{ it }}</li>
            </ul>
          </template>
          <template v-else>
            <div class="ai-label">{{ block.label }}</div>
            <div class="ai-kv">{{ block.value }}</div>
          </template>
        </div>
      </slot>
      <div v-if="showRaw" class="ai-raw">
        <el-button link size="small" @click="rawOpen = !rawOpen">
          {{ rawOpen ? '收起原始数据' : '查看原始数据' }}
        </el-button>
        <pre v-if="rawOpen" class="ai-pre">{{ prettyRaw }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 通用 AI 结果面板（W26）。
 *
 * <p>为什么做成组件：这轮要把 14 个此前**零调用**的 `aiAPI` 端点接到 8 个页面上。
 * 每个页面各写一遍「按钮 + loading + 错误 + 渲染」既啰嗦又容易漂移 ——
 * 尤其是**错误展示**：`utils/ai.js` 那道诚实性闸给出的那句人话，
 * 只有在所有页面都原样透出 `error.message` 时才有意义。
 *
 * <p>⚠️ 组件的 `catch` 里**不做任何"兜底文案"**：后端说什么就显示什么。
 * 本仓最忌讳的就是把"没拿到"渲染成"拿到了一句提示"。
 *
 * @prop {string} title   面板标题
 * @prop {Function} load  返回 Promise 的取数函数（内部已由 aiAPI 过闸）；resolve 出的对象即结果
 * @prop {string} hint    未开始时的占位文案
 * @prop {boolean} auto   是否挂载即自动请求（默认 false）
 */
import { ref, computed, onMounted } from 'vue'

const props = defineProps({
  title: { type: String, default: 'AI 分析' },
  load: { type: Function, required: true },
  hint: { type: String, default: '' },
  auto: { type: Boolean, default: false }
})

const loading = ref(false)
const result = ref(null)
const error = ref('')
const rawOpen = ref(false)

async function run () {
  loading.value = true
  error.value = ''
  result.value = null
  try {
    const res = await props.load()
    if (res === null || res === undefined || (typeof res === 'object' && Object.keys(res).length === 0)) {
      // 拿到了一个空壳 —— 如实说"没有返回内容"，不编一句
      error.value = '后端没有返回内容'
      return
    }
    result.value = res
  } catch (e) {
    // 原样透出：aiAPI 的诚实性闸、后端的业务错误、网络错误都走这里
    error.value = e?.message || '请求失败'
  } finally {
    loading.value = false
  }
}

/** 已知字段 → 中文标签。认不出来的 key 原样显示（不猜、不丢）。 */
const LABELS = {
  aiAnalysis: 'AI 分析',
  aiComment: 'AI 综合评语',
  // `/ai/analyze/{studentId}` 在模型没答时**不丢掉**规则算出的成绩分析/风险预测，
  // 只把评语换成这个带原因的字段（见 AIController.analyzeStudent 里那段注释）。
  aiCommentUnavailable: 'AI 综合评语（未生成）',
  aiRecommendations: 'AI 建议',
  aiRiskAnalysis: 'AI 风险解读',
  aiTrendAnalysis: 'AI 趋势解读',
  analysis: '分析',
  description: '说明',
  suggestion: '建议',
  riskLevel: '风险等级',
  riskScore: '风险分',
  riskFactors: '风险因素',
  predictions: '预测',
  suggestions: '建议',
  actionItems: '行动项',
  recommendations: '推荐',
  trend: '趋势',
  trendDetail: '趋势明细',
  semesterTrends: '各学期趋势',
  scoreAnalysis: '成绩分析',
  riskPrediction: '风险预测',
  warningLevel: '预警等级',
  warningLevelName: '预警等级',
  generatedAt: '生成时间',
  predictedAt: '预测时间'
}
/** 这些是元信息，不往正文里塞（要看可以点"查看原始数据"）。 */
const META_KEYS = new Set(['generatedAt', 'predictedAt', 'counselorId', 'warningId', 'classId', 'studentId'])

const blocks = computed(() => {
  const r = result.value
  if (!r || typeof r !== 'object') return [{ type: 'text', label: '', value: String(r) }]
  const out = []
  for (const [key, val] of Object.entries(r)) {
    if (META_KEYS.has(key)) continue
    const label = LABELS[key] || key
    if (val === null || val === undefined || val === '') continue
    if (Array.isArray(val)) {
      const items = val.map(v => (typeof v === 'object' ? JSON.stringify(v) : String(v)))
      if (items.length) out.push({ type: 'list', label, value: items })
    } else if (typeof val === 'object') {
      // 嵌套一层：把里面的 string 字段铺平（如 scoreAnalysis 里的 suggestion）
      const inner = Object.entries(val)
        .filter(([, v]) => typeof v === 'string' && v)
        .map(([k, v]) => `${LABELS[k] || k}：${v}`)
      const nums = Object.entries(val)
        .filter(([, v]) => typeof v === 'number')
        .map(([k, v]) => `${LABELS[k] || k}：${v}`)
      const merged = [...inner, ...nums]
      if (merged.length) out.push({ type: 'list', label, value: merged })
    } else if (typeof val === 'boolean') {
      out.push({ type: 'text', label, value: val ? '是' : '否' })
    } else {
      out.push({ type: 'text', label, value: String(val) })
    }
  }
  return out
})

const prettyRaw = computed(() => {
  try { return JSON.stringify(result.value, null, 2) } catch (e) { return String(result.value) }
})
const showRaw = computed(() => result.value && typeof result.value === 'object')

onMounted(() => { if (props.auto) run() })

defineExpose({ run })
</script>

<style scoped>
.ai-insight { border: 1px solid #e2e8f0; border-radius: 10px; padding: 12px 14px; background: #fbfdff; }
.ai-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.ai-title { font-size: 13px; font-weight: 600; color: #1e3a5f; }
.ai-empty { font-size: 12px; color: #94a3b8; padding: 6px 0; }
.ai-error { font-size: 12px; color: #b91c1c; background: #fef2f2; border-radius: 6px; padding: 8px 10px; line-height: 1.6; }
.ai-body { font-size: 12px; color: #334155; }
.ai-block + .ai-block { margin-top: 10px; }
.ai-label { font-weight: 600; color: #475569; margin-bottom: 3px; }
.ai-text { margin: 0; line-height: 1.7; white-space: pre-wrap; }
.ai-list { margin: 0; padding-left: 18px; line-height: 1.7; }
.ai-kv { line-height: 1.7; }
.ai-raw { margin-top: 8px; }
.ai-pre { background: #f1f5f9; border-radius: 6px; padding: 8px; font-size: 11px; overflow: auto; max-height: 240px; margin: 6px 0 0; }
</style>
