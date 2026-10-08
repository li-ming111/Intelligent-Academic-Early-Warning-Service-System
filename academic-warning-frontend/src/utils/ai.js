/**
 * AI 结果的「诚实性闸」（W26，2026-10-05；W28 更新说明）。
 *
 * <h3>为什么需要它</h3>
 * `ai-service/application.yml` 里 `tongyi.api-key` 的**默认值就是占位串**
 * `your-api-key-here`（`${DASHSCOPE_KEY:your-api-key-here}`），而
 * `TongYiService.isConfigured()` 会把它判为"没配"。**没人设环境变量时**，
 * 三个纯 AI 端点的 `generalChat()` 一律返回 `null`，而后端当时**用一句占位串
 * 冒充分析结果、还包成 `success:true`**：
 *
 * | 端点 | 占位串 |
 * |---|---|
 * | `GET /ai/analyze-warning-trend/{counselorId}` | `预警趋势分析中，请稍后重试` |
 * | `GET /ai/analyze-warning/{warningId}`         | `预警分析中，请稍后重试` |
 * | `GET /ai/analyze-class-scores/{classId}`      | `班级分析中，请稍后重试` |
 *
 * 把这三条接到界面上，用户看到的会是一句**看起来像分析结果**的等待提示 ——
 * 与 W24（`/teachers/scores/warnings` 说"已生成预警"却没写库）
 * 、W25（`/students/register` 回"注册成功"却零写库）同一族病：**说法与事实不符**。
 *
 * <h3>怎么处置</h3>
 * 闸**放在 `aiAPI` 封装层**（见 `api/index.js` 的 `aiGuard`）：命中占位串就 **throw**，
 * 于是每个页面**原有的 `catch`** 会把它当成一次失败如实报出来，
 * 不必在 8 个页面各写一遍"这是占位串"的渲染分支。
 *
 * <h3>⚠️ W28（2026-10-08）：后端那个形状**已经改了**，但本闸不撤</h3>
 * 上表那三条占位串**在 `AIController.java` 里已经不存在了** —— 后端改为
 * 如实返回 `ApiResponse.error(...)`，并区分"没配 key"（配置问题，重试无用）
 * 与"调用失败"（瞬时故障，重试有意义）。
 * 本闸**仍保留**这三条，理由是**深度防御**，不是为了兼容一个还在跑的旧后端：
 * ① 现役进程可能是**换代码前启动的旧 jar**（本仓踩过：改了返回串却在浏览器里看到旧行为，
 *    见手册 W23 一节），闸能让"旧后端"下的界面依旧不把占位串当真；
 * ② 后面若有人再往这些端点里塞"进度话术"，闸会立刻打红。
 * 所以这张表**记的是历史形状**：它描述的是"曾经会发生什么"，而不是"现在会发生什么"。
 *
 * <h3>判据的两侧</h3>
 * 本文件是**判据类代码**，判据比预期宽或窄都在本仓栽过不止一次 ⇒ 两侧都要打靶：
 * · 正对照：三条占位串 + `AI_UNAVAILABLE` 原文，**必须**命中；
 * · 负对照：一段正常分析文本、"预警分析中"（少了"，请稍后重试"）等**必须**不命中。
 * 打靶脚本：`testsprite/local-check/_w26_ai_guard.mjs`（`node` 直接 import 本文件跑，测的是真件不是副本）。
 * W28 在 B 组末尾补了两条负对照：后端如实报错时用的那句**原因**会正常出现在响应体里，
 * 闸必须**放行** —— 否则 `/ai/analyze/{studentId}` 里真正的 scoreAnalysis / riskPrediction 会被连坐丢掉。
 */

/**
 * 后端**曾经**在没配 key 时用来冒充分析结果的占位串。
 *
 * <p>⚠️ 前三条自 W28（2026-10-08）起后端已不再产生，留着是深度防御（见文件头那段）。
 * 末条是 `/ai/chat`、`/ai/qa` 如实报错时会**正常出现**的原文（那两条一直在如实报错）。
 */
const AI_PLACEHOLDERS = Object.freeze([
  '预警趋势分析中，请稍后重试',
  '预警分析中，请稍后重试',
  '班级分析中，请稍后重试',
  // 逐字取自 AIController 的 AI_UNAVAILABLE（`/ai/chat`、`/ai/qa` 走这条 —— 它们是**如实报错**的）
  '没有可用的 AI 回答：通义千问未配置有效的 API Key（环境变量 DASHSCOPE_KEY），或本次调用失败。请配置后再使用该功能。'
])

/** 给用户看的那句话（页面 catch 里直接用 `error.message`）。 */
export const AI_UNCONFIGURED_MESSAGE =
  'AI 服务当前不可用：后端没有配置通义千问 API Key（环境变量 DASHSCOPE_KEY），这次拿不到真实的分析结果。'

/**
 * 这段文本是不是后端用来冒充结果的占位串？
 *
 * @param {*} value 任意值（非字符串一律 false）
 * @returns {boolean}
 */
export function isAiPlaceholder(value) {
  if (typeof value !== 'string') return false
  return AI_PLACEHOLDERS.includes(value.trim())
}

/**
 * 递归找出响应里第一个占位串（后端把它埋在 `aiAnalysis` 之类的字段里）。
 *
 * @param {*} value 任意响应片段
 * @param {number} depth 当前深度（防止环状结构里跑飞；只扫 4 层，够用了）
 * @returns {string|null} 命中的占位串原文；没命中返回 null
 */
export function findAiPlaceholder(value, depth = 0) {
  if (depth > 4 || value == null) return null
  if (typeof value === 'string') return isAiPlaceholder(value) ? value.trim() : null
  if (Array.isArray(value)) {
    for (const item of value) {
      const hit = findAiPlaceholder(item, depth + 1)
      if (hit) return hit
    }
    return null
  }
  if (typeof value === 'object') {
    for (const key of Object.keys(value)) {
      const hit = findAiPlaceholder(value[key], depth + 1)
      if (hit) return hit
    }
  }
  return null
}

/**
 * 闸本体：挂在 `aiAPI` 的每个方法上（见 `api/index.js`）。
 *
 * <p>命中占位串 → **throw**（页面 catch 会如实报错）；否则原样返回响应，调用方无需改。
 *
 * @param {*} resp 后端响应（`client.js` 拦截器已解包到 `data`）
 * @returns {*} 原样的 resp
 * @throws {Error} `error.code === 'AI_UNAVAILABLE'`，`message` 是给用户看的人话
 */
export function aiGuard(resp) {
  const hit = findAiPlaceholder(resp)
  if (hit) {
    const error = new Error(AI_UNCONFIGURED_MESSAGE)
    error.code = 'AI_UNAVAILABLE'
    error.placeholder = hit      // 留个痕，方便排查是哪个端点
    throw error
  }
  return resp
}
