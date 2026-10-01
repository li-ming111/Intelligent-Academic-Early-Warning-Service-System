package com.academic.service.impl;

import com.academic.entity.SupportResource;
import com.academic.exception.ResourceNotFoundException;
import com.academic.mapper.SupportResourceMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 支持资源这条链的回归钉子。
 *
 * <p>钉的是 2026-09-30 第九轮读码 + 活库核对确认的四处历史缺陷：
 * <ol>
 *   <li><b>GET 在写、而且写错了列</b>：{@code getResourceDetail}（查看详情）累加的是
 *       {@code downloads}，而真正的浏览计数 {@code views} 在全仓**没有任何写入方**
 *       —— {@code grep views} 在 {@code academic-microservices} 下只命中实体字段声明。
 *       活库种子里 views/downloads = 150/45、200/60、80/30、120/38 全部永远不变。</li>
 *   <li><b>失败被包成成功</b>："资源不存在"原来写成返回值里的 {@code {status:"error"}}，
 *       controller 却无条件 {@code ApiResponse.success(...)} ⇒ 200/200。</li>
 *   <li><b>列表的 status 筛选被静默否掉</b>：先按参数建条件，紧接着又无条件 AND 一个
 *       {@code status=1} ⇒ {@code ?status=0} 恒返回空。</li>
 *   <li><b>写路径静默丢字段</b>：{@code add}/{@code update} 只读 8 个 key，
 *       {@code content}/{@code author}/{@code fileSize}/{@code fileType} 从来没读过
 *       （而 content/author 在活库 4 行里**都有值**：27~45 字正文 + 作者名）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class SupportResourceServiceImplTest {

    @Mock
    private SupportResourceMapper supportResourceMapper;

    @InjectMocks
    private SupportResourceServiceImpl service;

    // ------------------------------------------------------------------
    // getResourceDetail：这是"查看详情"，该涨的是 views
    // ------------------------------------------------------------------

    @Test
    @DisplayName("查看详情：涨的是 views，不是 downloads（原来把浏览记到了下载头上）")
    void testGetResourceDetail_IncrementsViewsNotDownloads() {
        when(supportResourceMapper.selectById(1L)).thenReturn(existing(1L, 150, 45));

        service.getResourceDetail("1");

        SupportResource saved = captureUpdate();
        assertEquals(151, saved.getViews().intValue(), "views 必须 +1：这一列原来全仓没有任何写入方");
        assertEquals(45, saved.getDownloads().intValue(),
                "downloads 不该被「查看详情」改动：它原来统计的是详情被打开的次数");
    }

    @Test
    @DisplayName("查看详情：views 为 NULL 的旧行从 1 起算（不能 NPE）")
    void testGetResourceDetail_NullViewsStartsAtOne() {
        SupportResource row = existing(1L, null, null);
        when(supportResourceMapper.selectById(1L)).thenReturn(row);

        service.getResourceDetail("1");

        assertEquals(1, captureUpdate().getViews().intValue());
    }

    @Test
    @DisplayName("查看详情：返回体里 views 和 downloads 各是各的，并且带上 content/author")
    void testGetResourceDetail_ReturnsBothCountersAndContent() {
        SupportResource row = existing(7L, 150, 45).setContent("正文").setAuthor("于凤海");
        when(supportResourceMapper.selectById(7L)).thenReturn(row);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) service.getResourceDetail("7");

        assertEquals(151, ((Integer) result.get("views")).intValue());
        assertEquals(45, ((Integer) result.get("downloads")).intValue());
        assertEquals("正文", result.get("content"), "详情原来不返回正文（列表反而返回整个实体）");
        assertEquals("于凤海", result.get("author"));
    }

    @Test
    @DisplayName("查看详情：资源不存在 → 抛 ResourceNotFoundException（不再返回 {status:error} 被包成成功）")
    void testGetResourceDetail_UnknownIdThrows() {
        when(supportResourceMapper.selectById(999L)).thenReturn(null);

        ResourceNotFoundException e = assertThrows(ResourceNotFoundException.class,
                () -> service.getResourceDetail("999"));
        assertTrue(e.getMessage().contains("999"));
        verify(supportResourceMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("查看详情：资源ID不是数字 → IllegalArgumentException（不再是裸的 NumberFormatException→500）")
    void testGetResourceDetail_NonNumericIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.getResourceDetail("abc"));
        assertThrows(IllegalArgumentException.class, () -> service.getResourceDetail(""));
        assertThrows(IllegalArgumentException.class, () -> service.getResourceDetail("  "));
        verify(supportResourceMapper, never()).selectById(any());
    }

    // ------------------------------------------------------------------
    // getResourceList：status 筛选得真能生效
    // ------------------------------------------------------------------

    @Test
    @DisplayName("列表：调用方给了 status=0 就听调用方的（原来被无条件 AND 的 status=1 否掉）")
    void testGetResourceList_HonoursCallerStatus() {
        when(supportResourceMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> params = new HashMap<>();
        params.put("status", "0");
        service.getResourceList(params);

        Collection<Object> bound = capturedParams();
        assertTrue(bound.contains("0"), "status=0 必须传到 SQL；实际绑定值=" + bound);
        assertFalse(bound.contains(1), "不能同时又 AND 一个 status=1（那样 status=0 AND status=1 恒空）");
    }

    @Test
    @DisplayName("列表：没给 status 才默认只看在架（status=1）")
    void testGetResourceList_DefaultsToActive() {
        when(supportResourceMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.getResourceList(null);

        Collection<Object> bound = capturedParams();
        assertTrue(bound.contains(1), "默认应筛 status=1；实际绑定值=" + bound);
    }

    @Test
    @DisplayName("列表：空串参数当没筛（原来 eq(\"type\", \"\") ⇒ 静默返回空）")
    void testGetResourceList_BlankFiltersIgnored() {
        when(supportResourceMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> params = new HashMap<>();
        params.put("type", "");
        params.put("category", "   ");
        service.getResourceList(params);

        Collection<Object> bound = capturedParams();
        assertFalse(bound.contains(""), "空串不该变成筛选条件；实际绑定值=" + bound);
        assertEquals(1, bound.size(), "只该剩一个默认 status=1；实际绑定值=" + bound);
    }

    // ------------------------------------------------------------------
    // addResource：标题要有校验，四个 key 不能再被丢掉
    // ------------------------------------------------------------------

    @Test
    @DisplayName("新增：标题为空 → IllegalArgumentException（不再 {status:error} 冒充成功）")
    void testAddResource_BlankTitleRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.addResource(new HashMap<>()));

        Map<String, Object> blank = new HashMap<>();
        blank.put("title", "   ");
        assertThrows(IllegalArgumentException.class, () -> service.addResource(blank));

        verify(supportResourceMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增：content/author/fileSize/fileType 都要落库（原来这 4 个 key 压根没读）")
    void testAddResource_WritesPreviouslyDroppedKeys() {
        Map<String, Object> request = new HashMap<>();
        request.put("title", "高等数学学习指导");
        request.put("content", "第一章 极限与连续……");
        request.put("author", "于凤海");
        request.put("fileSize", "20480");
        request.put("fileType", "pdf");

        service.addResource(request);

        SupportResource saved = captureInsert();
        assertEquals("第一章 极限与连续……", saved.getContent(), "content 原来被静默丢弃");
        assertEquals("于凤海", saved.getAuthor(), "author 原来被静默丢弃");
        assertEquals(20480L, saved.getFileSize().longValue(), "fileSize 原来被静默丢弃");
        assertEquals("pdf", saved.getFileType());
    }

    @Test
    @DisplayName("新增：两个计数从 0 起，status=1")
    void testAddResource_InitialState() {
        Map<String, Object> request = new HashMap<>();
        request.put("title", "标题");

        service.addResource(request);

        SupportResource saved = captureInsert();
        assertEquals(0, saved.getViews().intValue());
        assertEquals(0, saved.getDownloads().intValue());
        assertEquals(1, saved.getStatus().intValue());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    @DisplayName("新增：fileSize 不是数字 → 报错，不静默当没填")
    void testAddResource_NonNumericFileSizeRejected() {
        Map<String, Object> request = new HashMap<>();
        request.put("title", "标题");
        request.put("fileSize", "12MB");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.addResource(request));
        assertTrue(e.getMessage().contains("fileSize"));
        verify(supportResourceMapper, never()).insert(any());
    }

    // ------------------------------------------------------------------
    // updateResource：null 不能被静默跳过，四个 key 不能再被丢掉
    // ------------------------------------------------------------------

    @Test
    @DisplayName("更新：资源不存在 → ResourceNotFoundException")
    void testUpdateResource_UnknownIdThrows() {
        when(supportResourceMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateResource("999", new HashMap<>()));
        verify(supportResourceMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("更新：显式传 null → 报错且一个字节都不写（不能「报成功却没写」）")
    void testUpdateResource_ExplicitNullRejectedNotSilentlySkipped() {
        when(supportResourceMapper.selectById(1L)).thenReturn(existing(1L, 150, 45));

        Map<String, Object> request = new HashMap<>();
        request.put("title", null);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.updateResource("1", request));
        assertTrue(e.getMessage().contains("title"));
        assertTrue(e.getMessage().contains("null"));
        // 关键：不能"更新成功"地回一个什么都没写的响应
        verify(supportResourceMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("更新：标题被改成空白 → 报错（与新增口径一致）")
    void testUpdateResource_BlankTitleRejected() {
        when(supportResourceMapper.selectById(1L)).thenReturn(existing(1L, 150, 45));

        Map<String, Object> request = new HashMap<>();
        request.put("title", "  ");

        assertThrows(IllegalArgumentException.class, () -> service.updateResource("1", request));
        verify(supportResourceMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("更新：content/author 能改；没给到的字段保持原值")
    void testUpdateResource_WritesDroppedKeysAndLeavesOthers() {
        SupportResource row = existing(1L, 150, 45).setType("课件").setContent("旧正文");
        when(supportResourceMapper.selectById(1L)).thenReturn(row);

        Map<String, Object> request = new HashMap<>();
        request.put("content", "新正文");
        request.put("author", "司明");
        service.updateResource("1", request);

        SupportResource saved = captureUpdate();
        assertEquals("新正文", saved.getContent(), "content 原来被静默丢弃");
        assertEquals("司明", saved.getAuthor(), "author 原来被静默丢弃");
        assertEquals("课件", saved.getType(), "请求里没给的字段不该被改动");
        assertEquals(150, saved.getViews().intValue(), "更新资源不该动 views");
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    @DisplayName("更新：资源ID不是数字 → IllegalArgumentException")
    void testUpdateResource_NonNumericIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateResource("x", new HashMap<>()));
        verify(supportResourceMapper, never()).selectById(any());
    }

    // ------------------------------------------------------------------
    // deleteResource：软删除
    // ------------------------------------------------------------------

    @Test
    @DisplayName("删除：资源不存在 → ResourceNotFoundException")
    void testDeleteResource_UnknownIdThrows() {
        when(supportResourceMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.deleteResource("999"));
        verify(supportResourceMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("删除：是软删除（status=0），不删行")
    void testDeleteResource_SoftDeletes() {
        when(supportResourceMapper.selectById(3L)).thenReturn(existing(3L, 80, 30));

        service.deleteResource("3");

        SupportResource saved = captureUpdate();
        assertEquals(0, saved.getStatus().intValue());
        assertNotNull(saved.getUpdatedAt());
        // deleteById 在本工程的 BaseMapper 里是重载（Serializable / T），带 any() 会撞上
        // "对 deleteById 的引用不明确"。这里写明是 id 那一支。
        verify(supportResourceMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除：资源ID为空/不是数字 → IllegalArgumentException")
    void testDeleteResource_BadIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteResource(""));
        assertThrows(IllegalArgumentException.class, () -> service.deleteResource("2.5"));
        verify(supportResourceMapper, never()).selectById(any());
    }

    // ------------------------------------------------------------------
    // searchResources
    // ------------------------------------------------------------------

    @Test
    @DisplayName("搜索：空关键词/空类型不算条件，且始终只在在架资源里找")
    void testSearchResources_BlankTermsIgnored() {
        when(supportResourceMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> request = new HashMap<>();
        request.put("keyword", "  ");
        request.put("type", "");
        service.searchResources(request);

        Collection<Object> bound = capturedParams();
        assertEquals(1, bound.size(), "只该剩 status=1；实际绑定值=" + bound);
        assertTrue(bound.contains(1));
    }

    @Test
    @DisplayName("搜索：返回 {resources,total} 结构")
    void testSearchResources_Shape() {
        when(supportResourceMapper.selectList(any()))
                .thenReturn(Collections.singletonList(existing(1L, 150, 45)));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) service.searchResources(new HashMap<>());

        assertNotNull(result.get("resources"));
        assertEquals(1, ((Integer) result.get("total")).intValue());
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private static SupportResource existing(Long id, Integer views, Integer downloads) {
        return new SupportResource()
                .setId(id)
                .setTitle("高等数学学习指导")
                .setStatus(1)
                .setViews(views)
                .setDownloads(downloads)
                .setCreatedAt(LocalDateTime.now().minusDays(3));
    }

    private SupportResource captureInsert() {
        ArgumentCaptor<SupportResource> captor = ArgumentCaptor.forClass(SupportResource.class);
        verify(supportResourceMapper).insert(captor.capture());
        return captor.getValue();
    }

    private SupportResource captureUpdate() {
        ArgumentCaptor<SupportResource> captor = ArgumentCaptor.forClass(SupportResource.class);
        verify(supportResourceMapper).updateById(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------
    // ⚠️ 用 QueryWrapper 的**绑定参数值**当判据，而不是 SQL 片段的字符串。
    //    原因：MyBatis-Plus 生成的是 `status = #{ew.paramNameValuePairs.MPGENVAL1}`，
    //    SQL 片段里**看不到值**（只有占位符）—— 只 grep SQL 文本的话，
    //    `eq("status",1)` 和 `eq("status",0)` 是一模一样的，负向对照打不出来。
    //
    //    ❗另一个坑（本探针第一版就栽在这）：paramNameValuePairs 是**惰性**的，
    //    直到 SQL 段被渲染才写进去。mapper 是 mock，永远不会渲染 —— 于是第一版
    //    恒返回空集合，读到的是"没有条件"，对**正确实现和坏实现都返回空**，
    //    完全没有牙齿。所以这里先 getSqlSegment() 强制渲染，再读值；
    //    值仍为空就**直接失败并报出 SQL 段**，避免"探针坏了"被读成"断言通过"。
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Collection<Object> capturedParams() {
        ArgumentCaptor<QueryWrapper<SupportResource>> captor =
                ArgumentCaptor.forClass(QueryWrapper.class);
        verify(supportResourceMapper).selectList(captor.capture());
        QueryWrapper<SupportResource> wrapper = captor.getValue();
        String sql = wrapper.getSqlSegment();   // 渲染条件 → 顺带把绑定值写进 paramNameValuePairs
        Collection<Object> values = wrapper.getParamNameValuePairs().values();
        if (values.isEmpty()) {
            fail("探针失效：paramNameValuePairs 为空（SQL 段=" + sql + "）。"
                    + "这说明条件渲染没被触发 —— 此刻的空集合不等于「没有条件」，请修探针，别改断言。");
        }
        return values;
    }
}
