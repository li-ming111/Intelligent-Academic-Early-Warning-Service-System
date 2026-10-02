package com.academic.common.constant;

/**
 * {@code academic_warning.status} 的取值与文案 —— **全仓唯一一处**（2026-10-02，W1.6 收尾）。
 *
 * <h3>为什么要有这个类</h3>
 * 状态是**三态**：{@code 0 待处理 / 1 已处理 / 2 已解除}（「已解除」由
 * {@code warning-service} 的定时扫描按 {@code ScoreCaliber} 现算后写回，
 * 见 {@code WarningScheduledTask}）。
 *
 * <p>但在 2026-10-02 之前，把状态翻成中文这件事**有三个地方各写了一遍**，而且
 * 因为当时都按**两态**写，{@code status=2} 被翻成了两个**相反**的结果：
 * <table border="1">
 *   <caption>修之前</caption>
 *   <tr><th>位置</th><th>写法</th><th>status=2 变成</th></tr>
 *   <tr><td>{@code TeacherController} 班级预警列表</td>
 *       <td>{@code status == 0 ? "待处理" : "已处理"}</td><td><b>已处理</b></td></tr>
 *   <tr><td>{@code TeacherController} 处理结果回读</td>
 *       <td>{@code status == 1 ? "已处理" : "待处理"}</td><td><b>待处理</b></td></tr>
 *   <tr><td>{@code CollegeAdminController} 学院预警导出</td>
 *       <td>{@code status == 1 ? "已处理" : "待处理"}</td><td><b>待处理</b></td></tr>
 * </table>
 * 同一张表、同一个取值，三个接口给出两种相反答案 —— 这正是"手写第二张映射表必然漂移"
 * 的典型（同 {@link ScoreCaliber} 的教训）。所以这里收敛成一个类，三处都调它。
 *
 * <p><b>口径</b>：{@code status} 为 {@code null} 时按 **待处理** 处理 ——
 * 宁可多显示一条待处理（有人会去看），也不要让一条预警凭空"消失"。
 */
public final class WarningStatus {

    /** 待处理（新生成的预警就是这个）。 */
    public static final int PENDING = 0;
    /** 已处理（辅导员/教师/管理员人工处理过）。 */
    public static final int HANDLED = 1;
    /** 已解除（按成绩口径现算已不再达阈值，由定时扫描自动写回）。 */
    public static final int DISPELLED = 2;

    public static final String PENDING_TEXT = "待处理";
    public static final String HANDLED_TEXT = "已处理";
    public static final String DISPELLED_TEXT = "已解除";

    private WarningStatus() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 状态码 → 展示文案。认不出的值按「待处理」。
     *
     * <p>⚠️ 前端有一份对应的三态表（小程序 {@code utils/warning.js} 的
     * {@code warningStatus()}、PC {@code utils/score.js}）。**改这里就要同步改那两处**，
     * 否则会出现"同一个 status 两端显示不同"—— 那正是本轮修掉的东西。
     */
    public static String text(Integer status) {
        if (status == null) {
            return PENDING_TEXT;
        }
        return switch (status) {
            case HANDLED -> HANDLED_TEXT;
            case DISPELLED -> DISPELLED_TEXT;
            default -> PENDING_TEXT;
        };
    }
}
