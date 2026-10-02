package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.entity.Permission;
import com.academic.mapper.PermissionMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 「编辑单条权限」真的写库了（W1.16，2026-10-02）。
 *
 * <p><b>修之前是什么样</b>：PC {@code AdminPermissions.vue} 的「编辑」按钮是
 * {@code ElMessage.info('编辑功能开发中...')} —— 点了有反馈、弹窗不开、库里不动。
 * 同一个文件里角色的新建 / 编辑 / 删除在第二十四轮都做掉了，这个被落下。
 *
 * <p><b>这里钉四件事</b>：
 * <br>① 名称 / 描述真的进 UPDATE 语句，且**去掉首尾空白**；
 * <br>② {@code code} 不可改 —— 传了就明确 400，**不能静默忽略**（它是前端角色弹窗
 *     {@code v-model="newRole.permissions"} 与 {@code resolvePermissions} 按字面引用的标识符，
 *     静默忽略会让调用方以为改成功了）；
 * <br>③ 空描述要写 {@code null} 而不是空串。这一条**必须读 SET 子句**才验得到：
 *     {@code UpdateWrapper.set("description", null)} 与"压根没 set"在
 *     {@code update(null, wrapper)} 这一层长得一样，但前者会把列清成 NULL、
 *     后者不动那一列 —— 而 MyBatis-Plus 默认更新策略是 NOT_NULL，
 *     走 {@code updateById(entity)} 的话 {@code setDescription(null)} 是一句空话；
 * <br>④ 权限不存在回 404、没有可改字段回 400，不许把失败包成成功。
 */
@ExtendWith(MockitoExtension.class)
class AdminPermissionUpdateTest {

    @Mock private PermissionMapper permissionMapper;
    @InjectMocks private AdminController controller;

    /** 造一条"库里已存在"的权限，供 selectById 返回。 */
    private Permission existing() {
        Permission p = new Permission();
        p.setId(10L);
        p.setCode("PERM_PROBE");
        p.setName("旧名字");
        p.setDescription("旧描述");
        return p;
    }

    /** 捕获那次 update(null, wrapper) 的 wrapper。 */
    @SuppressWarnings("unchecked")
    private UpdateWrapper<Permission> capturedWrapper() {
        ArgumentCaptor<UpdateWrapper<Permission>> cap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(permissionMapper).update(isNull(), cap.capture());
        return cap.getValue();
    }

    private Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    @DisplayName("改名称：SET 子句里有 name，且值已 trim")
    void updateName_writesTrimmedValue() {
        Permission p = existing();
        when(permissionMapper.selectById(10L)).thenReturn(p, p);

        ApiResponse<Map<String, Object>> resp =
                controller.updatePermission(10L, body("name", "  新名字  "));

        assertEquals(200, resp.getCode());
        UpdateWrapper<Permission> w = capturedWrapper();
        assertTrue(w.getSqlSet().contains("name"), "SET 子句应包含 name：" + w.getSqlSet());
        assertTrue(w.getParamNameValuePairs().containsValue("新名字"),
                "值应被 trim 后写入：" + w.getParamNameValuePairs());
    }

    @Test
    @DisplayName("清描述：空串必须写 null（不是空串、也不是不动这一列）")
    void updateEmptyDescription_writesNullExplicitly() {
        Permission p = existing();
        when(permissionMapper.selectById(10L)).thenReturn(p, p);

        controller.updatePermission(10L, body("description", ""));

        UpdateWrapper<Permission> w = capturedWrapper();
        assertTrue(w.getSqlSet().contains("description"),
                "SET 子句必须出现 description，否则等于没清：" + w.getSqlSet());
        assertTrue(w.getParamNameValuePairs().containsValue(null),
                "值应为 null（清成 NULL）而不是空串：" + w.getParamNameValuePairs());
    }

    @Test
    @DisplayName("传 code：明确 400，且一个字段都不写")
    void updateRejectsCodeChange() {
        when(permissionMapper.selectById(10L)).thenReturn(existing());

        ApiResponse<Map<String, Object>> resp =
                controller.updatePermission(10L, body("code", "PERM_HACKED"));

        assertEquals(400, resp.getCode());
        verify(permissionMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("空 body：400（不是「更新成功」）")
    void updateEmptyBody_returns400() {
        when(permissionMapper.selectById(10L)).thenReturn(existing());

        ApiResponse<Map<String, Object>> resp = controller.updatePermission(10L, new HashMap<>());

        assertEquals(400, resp.getCode());
        verify(permissionMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("名称全空白：400，且不写库")
    void updateBlankName_returns400() {
        when(permissionMapper.selectById(10L)).thenReturn(existing());

        ApiResponse<Map<String, Object>> resp = controller.updatePermission(10L, body("name", "   "));

        assertEquals(400, resp.getCode());
        verify(permissionMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("权限不存在：404，且不写库")
    void updateMissingPermission_returns404() {
        when(permissionMapper.selectById(999999L)).thenReturn(null);

        ApiResponse<Map<String, Object>> resp = controller.updatePermission(999999L, body("name", "x"));

        assertEquals(404, resp.getCode());
        verify(permissionMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("名称超长：400")
    void updateTooLongName_returns400() {
        when(permissionMapper.selectById(10L)).thenReturn(existing());

        ApiResponse<Map<String, Object>> resp =
                controller.updatePermission(10L, body("name", "x".repeat(101)));

        assertEquals(400, resp.getCode());
        verify(permissionMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("成功时回读的是**库里那条**（code 原样带出，不会被改动）")
    void updateReturnsPersistedRow() {
        Permission before = existing();
        Permission after = existing();
        after.setName("新名字");
        when(permissionMapper.selectById(10L)).thenReturn(before, after);

        ApiResponse<Map<String, Object>> resp = controller.updatePermission(10L, body("name", "新名字"));

        assertEquals(200, resp.getCode());
        assertEquals("PERM_PROBE", resp.getData().get("code"), "权限码必须原样返回");
        assertEquals("新名字", resp.getData().get("name"));
    }
}
