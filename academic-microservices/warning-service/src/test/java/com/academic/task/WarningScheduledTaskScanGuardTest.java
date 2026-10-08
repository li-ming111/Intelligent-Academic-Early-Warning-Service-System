package com.academic.task;

import com.academic.mapper.AcademicWarningMapper;
import com.academic.mapper.ScoreReadMapper;
import com.academic.mapper.WarningReleaseRecordMapper;
import com.academic.mapper.WarningRuleMapper;
import com.academic.notify.WarningNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 全量重扫的**互斥**（W26，2026-10-05）。
 *
 * <p>为什么这个必须钉：W26 给界面加了「重新扫描预警」按钮。在此之前
 * {@code runScan} **无锁、无"扫描中"标志** —— 唯一的幂等手段是"学生 + 挂科门数"去重，
 * 那只挡得住**串行**重复（点两次、一次跑完再点一次），挡不住**并发**：
 * 两次触发各读同一份快照、各自判定"这个学生还没有预警"，就会各插一条。
 * 定时任务（凌晨 2:00 + 每 30 分钟一次）与手动触发之间同样会互相打架。
 *
 * <p>两个用例分别打两面：
 * <ol>
 *   <li><b>确定性</b>：把"扫描中"标志直接置上，再触发一次 —— 必须回 {@code skipped=true}
 *       且**一个 mapper 都不碰**（碰了就是方法体又跑了一遍）；</li>
 *   <li><b>真并发</b>：让第一轮卡在第一次查库上，此时从另一个线程触发 —— 必须被跳过。
 *       这条是"用 mock 计数断言 runScan 体只执行一次"的真身：只有真的互斥才成立。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class WarningScheduledTaskScanGuardTest {

    @Mock private ScoreReadMapper scoreReadMapper;
    @Mock private AcademicWarningMapper academicWarningMapper;
    @Mock private WarningRuleMapper warningRuleMapper;
    @Mock private WarningReleaseRecordMapper warningReleaseRecordMapper;
    @Mock private WarningNotifier warningNotifier;

    @InjectMocks private WarningScheduledTask scheduledTask;

    @Test
    @DisplayName("已有一轮在跑 → 本次触发直接 skipped，且一个 mapper 都不碰")
    void testTriggerWhileScanningIsSkippedWithoutTouchingAnything() {
        // 模拟"上一轮还在跑"：互斥标志已占
        ReflectionTestUtils.setField(scheduledTask, "scanning", new AtomicBoolean(true));

        WarningScheduledTask.ScanResult result = scheduledTask.triggerNowDetailed();

        assertTrue(result.skipped(), "已在扫描中必须回 skipped=true，而不是排队或再跑一遍");
        assertEquals(0, result.generated());
        assertEquals(0, result.released());
        verifyNoInteractions(warningRuleMapper, scoreReadMapper,
                academicWarningMapper, warningReleaseRecordMapper, warningNotifier);
    }

    @Test
    @DisplayName("真并发：第一轮卡在查库时再触发一次 → 第二次被跳开，扫描体只执行一次")
    void testConcurrentTriggerRunsBodyOnlyOnce() throws Exception {
        CountDownLatch firstInsideScan = new CountDownLatch(1);
        CountDownLatch letFirstFinish = new CountDownLatch(1);

        // 第一次查库时卡住 —— 此刻第一轮**确实在扫描中**
        when(warningRuleMapper.selectList(any())).thenAnswer(invocation -> {
            firstInsideScan.countDown();
            assertTrue(letFirstFinish.await(10, TimeUnit.SECONDS), "测试自身超时");
            return List.of();   // 无规则 ⇒ doScan 立刻收尾，返回 skipped=false
        });

        AtomicReference<WarningScheduledTask.ScanResult> first = new AtomicReference<>();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        Thread a = new Thread(() -> {
            try {
                first.set(scheduledTask.triggerNowDetailed());
            } catch (Throwable t) {
                firstFailure.set(t);
            }
        }, "first-scan");
        a.start();

        assertTrue(firstInsideScan.await(10, TimeUnit.SECONDS), "第一轮没能进入扫描体");

        // 第二轮：真实地"同时"打进来
        WarningScheduledTask.ScanResult second = scheduledTask.triggerNowDetailed();

        assertTrue(second.skipped(), "扫描进行中的第二次触发必须被跳过，实际: " + second);
        assertEquals(0, second.generated());
        assertEquals(0, second.released());

        letFirstFinish.countDown();
        a.join(10_000);
        assertNull(firstFailure.get(), "第一轮不该抛异常: " + firstFailure.get());
        assertNotNull(first.get(), "第一轮应当正常返回");
        assertFalse(first.get().skipped(), "第一轮是真扫描，不该被标成 skipped");

        // 关键计数：查库只发生了一次 ⇒ 方法体只跑了一遍
        verify(warningRuleMapper, times(1)).selectList(any());
    }

    @Test
    @DisplayName("一轮跑完后标志必须释放 —— 否则第二次是「永远跳过」（互斥漏了 finally 的症状）")
    void testFlagReleasedAfterScanSoNextRoundStillRuns() {
        when(warningRuleMapper.selectList(any())).thenReturn(List.of());

        WarningScheduledTask.ScanResult first = scheduledTask.triggerNowDetailed();
        WarningScheduledTask.ScanResult second = scheduledTask.triggerNowDetailed();

        assertFalse(first.skipped());
        assertFalse(second.skipped(), "第一轮结束后标志未被释放，后续扫描被永久锁死");
        verify(warningRuleMapper, times(2)).selectList(any());
    }
}
