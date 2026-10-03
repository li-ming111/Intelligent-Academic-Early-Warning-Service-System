/**
 * 业务字典。
 *
 * **每一条都已在库里 / 后端代码里核实过取值，不要凭印象加条目。**
 * 尤其注意"同一个词的 int 在不同表里含义完全不同"：
 * `status = 1` 在 `academic_warning` 是"已处理"，在 `assistance_plans` 是"进行中"，
 * 在 `score_appeals` 是"已通过"，在 `users` 是"正常"。
 * 所以字典一律按"表"分组，跨表复用是 bug。
 */

/* ========== 预警等级（academic_warning.warning_level，int 1/2/3） ========== */
export const WARNING_LEVEL_TEXT = {
  1: '轻度',
  2: '中度',
  3: '严重'
}

/* ==========
 * 预警处理状态（academic_warning.status，int）
 * 0 待处理 / 1 已处理 / 2 已解除
 *
 * ⚠️ 2026-10-02（W1.6）补第 2 态。此前这里只写了 0/1，并且注释里列了两条"互证"：
 *      teacher-service `w.getStatus()==0 ? "待处理" : "已处理"`
 *      admin-service   `intOf(status)==1 ? "已处理" : "待处理"`
 *   —— 这两条**在 status=2 上给出相反答案**（一个"已处理"、一个"待处理"），
 *   所以它们从来不是互证，只是两个都按两态写的地方。现在后端已收敛到
 *   `com.academic.common.constant.WarningStatus.text()`，三处共用一份。
 *
 * 第 3 态的来源：warning-service 的定时扫描按成绩口径现算，不再达阈值时把
 * status 置 2（见 `WarningScheduledTask`）。
 * ========== */
export const WARNING_STATUS_TEXT = {
  0: '待处理',
  1: '已处理',
  2: '已解除'
}

/* ==========
 * 帮扶计划状态（assistance_plans.status，int）
 * 0 待开始 / 1 进行中 / 2 已完成
 * 来源：AssistancePlanServiceImpl.parseStatus()，且 `setStatus(percent==0 ? 0 : percent>=100 ? 2 : 1)`
 * 与进度联动 —— 进度 100% 时状态必然是 2。
 * ========== */
export const PLAN_STATUS_TEXT = {
  0: '待开始',
  1: '进行中',
  2: '已完成'
}

export const PLAN_STATUS_KEY = {
  0: 'pending',
  1: 'active',
  2: 'done'
}

/* ==========
 * 成绩申诉状态（score_appeals.status，int）
 * 0 待处理 / 1 已通过 / 2 已驳回 / 3 已撤回
 * 来源：mapper `selectPendingByStudentId`(status=0)、`countSuccessByStudentId`(status=1)、
 *       `withdrawAppeal`(SET status=3)；PC `Appeals.vue` 的 0/1/2 文案。
 *
 * 2026-10-02（W1.1 / W5.8）两条旧口径问题都已修，此处的历史说明保留以免回退：
 * 1. 曾经**没有任何代码写 status=2** —— 管理员申诉审核接口
 *    （`POST /admin/appeals/{id}/review`，body `{status: 1|2, reply}`）补上了，
 *    2 现在是真会被写入的取值；
 * 2. `getAppealStatistics` 曾经是 `rejected = total - pending - approved`，
 *    减法把**已撤回(3)** 折进了「已驳回」。现改为四态各自 COUNT，
 *    且接口多回一个 `withdrawn` ⇒ pending + approved + rejected + withdrawn == total。
 * ========== */
export const APPEAL_STATUS_TEXT = {
  0: '待处理',
  1: '已通过',
  2: '已驳回',
  3: '已撤回'
}

export const APPEAL_STATUS_KEY = {
  0: 'pending',
  1: 'approved',
  2: 'rejected',
  3: 'withdrawn'
}

/* ==========
 * 申诉原因（score_appeals.reason，varchar —— 后端**没有**枚举，存的是自由文本）
 *
 * 这五个 code 来自 PC 端 `views/Appeals.vue` 的 `reasons` 选项表，
 * 库里现有的唯一值 `entry_error` 与之吻合，所以它是本项目既成的词表，
 * 不是这里新造的。新增 code 前必须先确认库里/PC 端出现过。
 * ========== */
export const APPEAL_REASONS = [
  { label: '成绩录入错误', value: 'entry_error' },
  { label: '缓考未同步', value: 'makeup_not_sync' },
  { label: '阅卷评分有误', value: 'grading_issue' },
  { label: '数据异常', value: 'data_error' },
  { label: '其他原因', value: 'other' }
]

/** code → 中文标签。认不出时原样返回 code，不吞成「未知」（否则没法排查）。 */
export function appealReasonText(value) {
  if (value === null || value === undefined || value === '') return '未填写'
  const found = APPEAL_REASONS.find(r => r.value === String(value))
  return found ? found.label : String(value)
}

/* ==========
 * 班级申请状态（teacher_class_application / counselor_class_application，int）
 * 0 待审核 / 1 已通过 / 2 已拒绝 / 3 已取消
 * 来源：CounselorController `setStatus(0) // 0-待审核`、`setStatus(3) // 3-已取消`；
 *       PC `CounselorClassManagement.getStatusText` 与 `TeacherClassManagement.statusText`。
 * ========== */
export const APPLICATION_STATUS_TEXT = {
  0: '待审核',
  1: '已通过',
  2: '已拒绝',
  3: '已取消'
}

export const APPLICATION_STATUS_KEY = {
  0: 'pending',
  1: 'approved',
  2: 'rejected',
  3: 'cancelled'
}

/* ==========
 * 用户账号状态（users.status，int）
 * 0 审批中 / 1 正常 / 2 锁定 / 其他 禁用
 * 来源：user-service `UserServiceImpl.login()` 的文案分支 + admin 审批通过后 `setStatus(1)`。
 * ========== */
export const USER_STATUS_TEXT = {
  0: '审批中',
  1: '正常',
  2: '锁定',
  3: '禁用'
}

/* ==========
 * 消息/通知已读状态（notifications.status / messages.status，int）
 * 0 未读 / 1 已读
 * ========== */
export const READ_STATUS_TEXT = {
  0: '未读',
  1: '已读'
}

/* ========== 预警类型（academic_warning.type，int） ========== */
/** 库里现存 only type=1。未在其他代码里找到 2/3 的定义，故不臆造。 */
export const WARNING_TYPE_TEXT = {
  1: '成绩预警'
}

/* ========== 学期 ========== */
/** 库里学期字段是 `2025-2026-1` 形态；1 = 秋季学期，2 = 春季学期。 */
export const SEMESTER_TERMS = {
  1: '第一学期',
  2: '第二学期'
}

/* ========== 通用转换 ========== */

/**
 * 按字典取文案。
 * @param {object} dict 上表之一
 * @param {*} value 后端值（int / 数字字符串）
 * @param {string} fallback 认不出时的兜底
 */
export function dictText(dict, value, fallback = '未知') {
  if (value === null || value === undefined) return fallback
  const key = typeof value === 'number' ? value : String(value).trim()
  return dict[key] ?? fallback
}

/** 状态 → 标签样式键（success / warning / danger / info / pending）。 */
export const STATUS_TAG_TYPE = {
  pending: 'warning',
  approved: 'success',
  active: 'info',
  done: 'success',
  handled: 'success',
  // 2026-10-02（W1.6）：预警「已解除」—— 用 info（中性灰）而不是 success，
  // 因为 success 是"人处理完了"（handled），已解除是"分数上来了，自动的"，
  // 两者混成一个颜色，页面上就分不出哪条是辅导员真处理过的。
  dispelled: 'info',
  rejected: 'danger',
  withdrawn: 'info',
  cancelled: 'info'
}
