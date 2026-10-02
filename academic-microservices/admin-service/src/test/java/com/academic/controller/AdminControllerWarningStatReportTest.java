package com.academic.controller;

import com.academic.common.constant.WarningStatus;
import com.academic.common.entity.AcademicWarning;
import com.academic.mapper.AcademicWarningMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 管理员「报表导出 → 模板3 预警等级统计」的单元测试（W1.6，2026-10-02）。
 *
 * <p>这一块原来是**两态**：{@code status == 1 ? 1 : 0} —— 一行只有「待处理/已处理」两格，
 * 于是 {@code status = 2}（已解除）被算进「待处理」。页面上"已解除"从来显示不出来、
 * 报表里"待处理"比实际多。三态（待处理/已处理/已解除）是本项目 {@code academic_warning.status}
 * 的真实取值，见 {@link WarningStatus}。
 *
 * <p>这个缺陷**任何页面级扫描都抓不到** —— 它不在 Vue 里，也不在 Controller 的响应体里，
 * 只在导出 xlsx 的那一行组装代码里。所以这里用反射直接钉住那个私有方法。
 *
 * <p>另外钉住**列名契约**：{@link AdminController#getReportHeaders(String)} 的 "3" 分支
 * 与 {@link AdminController#buildWarningStatRows()} 产出的单元格**必须等宽**。自定义模板路径
 * （{@code matchBuiltinReportRows}）靠"表头逐字相等"来认领内置报表，列数对不上时
 * 匹配静默失败 —— 用户上传一个表头正确的模板，拿回一份只有表头的空报表。
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerWarningStatReportTest {

    @Mock
    private AcademicWarningMapper academicWarningMapper;

    @InjectMocks
    private AdminController controller;

    /** 反射调私有方法 —— 整条报表链（headers/data/buildXxxRows）都是私有的，唯一的公开入口
     *  {@code generateReportFile} 会往磁盘写 xlsx，不适合做单元测试。 */
    @SuppressWarnings("unchecked")
    private <T> T invokePrivate(String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = AdminController.class.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return (T) m.invoke(controller, args);
    }

    private List<List<String>> rows() throws Exception {
        return invokePrivate("buildWarningStatRows", new Class<?>[0]);
    }

    private static AcademicWarning warning(int level, Integer status) {
        return new AcademicWarning().setWarningLevel(level).setStatus(status);
    }

    // ---------------- 三态分桶 ----------------

    /**
     * ★ 核心断言：0/1/2 分别落进「待处理/已处理/已解除」三格。
     *
     * <p>负向对照：把 {@code case DISPELLED -> 2} 改回 {@code -> 0}（原实现的两态写法），
     * 这条立刻红 —— 已解除会被算进待处理。
     */
    @Test
    void threeStatesLandInSeparateBuckets() throws Exception {
        when(academicWarningMapper.selectList(any())).thenReturn(List.of(
                warning(3, WarningStatus.PENDING),
                warning(3, WarningStatus.HANDLED),
                warning(3, WarningStatus.DISPELLED),
                warning(3, WarningStatus.DISPELLED)));

        List<String> row3 = rows().get(2); // 第 3 行是 level=3

        assertEquals("高级预警", row3.get(0));
        assertEquals("1", row3.get(1), "待处理");
        assertEquals("1", row3.get(2), "已处理");
        assertEquals("2", row3.get(3), "已解除（两态实现会把它并进第 1 格）");
        assertEquals("4", row3.get(4), "合计");
    }

    /** status 为 null（加列之前的历史行）按「待处理」—— 与 {@link WarningStatus#text} 同一口径。 */
    @Test
    void nullStatusCountsAsPending() throws Exception {
        when(academicWarningMapper.selectList(any())).thenReturn(List.of(warning(2, null)));

        List<String> row2 = rows().get(1);

        assertEquals("1", row2.get(1), "null 归待处理");
        assertEquals("0", row2.get(2));
        assertEquals("0", row2.get(3));
        assertEquals("1", row2.get(4));
    }

    /** 认不出的 status（脏数据）也归待处理，且**不新增格子** —— 不能把表撑成 6 列。 */
    @Test
    void unknownStatusCountsAsPendingWithoutAddingColumn() throws Exception {
        when(academicWarningMapper.selectList(any())).thenReturn(List.of(warning(1, 99)));

        List<String> row1 = rows().get(0);

        assertEquals(5, row1.size(), "列数恒为 5");
        assertEquals("1", row1.get(1));
    }

    // ---------------- 行/列形状 ----------------

    /**
     * ★ 表头与数据行必须等宽、且顺序一一对应。
     *
     * <p>自定义模板路径靠「表头逐字相等」认领内置报表（{@code matchBuiltinReportRows}），
     * 列数或列名漂一格，匹配就静默失败 —— 症状是"导出成功但只有表头没有数据"。
     */
    @Test
    void headerContractMatchesRowWidth() throws Exception {
        String[] headers = invokePrivate("getReportHeaders", new Class<?>[]{String.class}, "3");

        assertArrayEquals(new String[]{"预警等级", "待处理", "已处理", "已解除", "合计"}, headers);
        when(academicWarningMapper.selectList(any())).thenReturn(List.of(warning(1, 0)));
        assertEquals(headers.length, rows().get(0).size(), "每一行的单元格数必须等于表头列数");
    }

    /** 一行数据都没有时，三档仍各留一行 0 —— 报表缺档看起来像漏了数据。 */
    @Test
    void emptyTableStillEmitsThreeZeroRows() throws Exception {
        when(academicWarningMapper.selectList(any())).thenReturn(List.of());

        List<List<String>> r = rows();

        assertEquals(3, r.size());
        assertArrayEquals(new String[]{"低级预警", "0", "0", "0", "0"}, r.get(0).toArray());
        assertArrayEquals(new String[]{"中级预警", "0", "0", "0", "0"}, r.get(1).toArray());
        assertArrayEquals(new String[]{"高级预警", "0", "0", "0", "0"}, r.get(2).toArray());
    }

    /** 行序恒为 1→2→3（低→高），不随数据出现顺序变 —— 报表每期形状要一致。 */
    @Test
    void rowOrderIsLevelOneToThree() throws Exception {
        when(academicWarningMapper.selectList(any())).thenReturn(List.of(warning(3, 0), warning(1, 0)));

        List<List<String>> r = rows();

        assertEquals("低级预警", r.get(0).get(0));
        assertEquals("中级预警", r.get(1).get(0));
        assertEquals("高级预警", r.get(2).get(0));
    }

    /**
     * 等级不在 1/2/3 之内（null / 4 / 0）→ **不归入任何一行，也不新增行**。
     * 与前端 {@code buildWarningTrend}「未识别等级不归入任何一条」同一口径。
     */
    @Test
    void unknownLevelIsSkippedEntirely() throws Exception {
        when(academicWarningMapper.selectList(any())).thenReturn(List.of(
                warning(4, 0), warning(0, 0), warning(2, WarningStatus.HANDLED)));

        List<List<String>> r = rows();

        assertEquals(3, r.size(), "不能为 level=4 新增一行");
        assertEquals("1", r.get(1).get(2), "只有 level=2 那条被统计");
        assertEquals("0", r.get(0).get(4));
        assertEquals("0", r.get(2).get(4));
    }

    /** 合计恒等于三态之和 —— 拿它当"有没有算漏"的自检。 */
    @Test
    void totalEqualsSumOfThreeStates() throws Exception {
        when(academicWarningMapper.selectList(any())).thenReturn(List.of(
                warning(2, 0), warning(2, 0), warning(2, 1), warning(2, 2), warning(2, 2), warning(2, 2)));

        List<String> row2 = rows().get(1);

        int sum = Integer.parseInt(row2.get(1)) + Integer.parseInt(row2.get(2)) + Integer.parseInt(row2.get(3));
        assertEquals(sum, Integer.parseInt(row2.get(4)));
        assertEquals(6, sum);
    }

    /** 等级为 null 的行不炸、也不算 —— {@code selectList} 回 null 时更不能 NPE。 */
    @Test
    void nullWarningLevelDoesNotThrow() throws Exception {
        when(academicWarningMapper.selectList(any())).thenReturn(List.of(
                new AcademicWarning().setStatus(0)));

        assertEquals(3, rows().size());
    }
}
