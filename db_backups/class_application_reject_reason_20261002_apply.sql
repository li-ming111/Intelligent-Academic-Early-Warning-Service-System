-- ============================================================================
-- 第三十一轮（W1.14，2026-10-02）：给 `counselor_class_application` 补 `reject_reason`
--
-- 为什么：管理员在两个前端上**都**收「拒绝理由」并真的发了出去 ——
--   · PC  `AdminClassManagement.vue` 的拒绝对话框（`rejectForm.reason`，还做了非空校验）
--     → `adminAPI.rejectClassManagementRequest(id, reason, type)`
--     → `POST /admin/class-management/reject/{id}?type=...`  body `{reason}`
--   · 小程序 `pages/admin/class-requests.vue` 同款
--     → `adminAPI.rejectClassRequest(id, reason, type)`
-- 而 `AdminController.rejectClassRequest` 的方法签名里**收了这个 Map 却一次都没读**：
--     `@RequestBody Map<String, String> rejectData`  ——  `rejectData` 全文只出现在参数列表里。
-- 于是「拒绝理由」**从页面上发出去就没了**。
--
-- 表结构上两半不一样：
--   · `teacher_class_application`   **有** `reject_reason`（早就加了，但没人写）
--   · `counselor_class_application` **没有**这一列  ← 本次补的就是它
--
-- ⚠️ 加列要**两处都改**：这里 + `academic-common/src/main/resources/init_schema.sql`。
--    `db_backups/rebuild_schema.py` 已于 2026-10-12（W39）**整块删除**（它从静态 dump 读，
--    会把已删的列/表复活）。`init_schema.sql` 现在是唯一权威结构（见 FEATURES.md §8.14）。
--
-- 可重复执行（用 information_schema 判一下再 ALTER；MySQL 8 的 ADD COLUMN 不支持 IF NOT EXISTS）。
-- ============================================================================

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'counselor_class_application'
      AND COLUMN_NAME = 'reject_reason'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `counselor_class_application` ADD COLUMN `reject_reason` varchar(500) DEFAULT NULL COMMENT ''拒绝原因'' AFTER `application_type`',
    'SELECT ''counselor_class_application.reject_reason 已存在，跳过'' AS note');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 执行后应当能查到这个列：
--   SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA='academic_warning_system'
--      AND TABLE_NAME IN ('teacher_class_application','counselor_class_application')
--      AND COLUMN_NAME='reject_reason';
-- 期望 2 行。
