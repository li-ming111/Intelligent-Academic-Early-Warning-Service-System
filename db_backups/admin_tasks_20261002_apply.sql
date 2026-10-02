-- ============================================================================
-- 第二十八轮（W1.11，2026-10-02）：新建 `admin_tasks` 表
--
-- 为什么建它：`AdminController` 的 `/admin/tasks/*` 四个端点**全是桩** ——
--   · `getTasksList` 里写死 `List<Map<String,Object>> tasks = new ArrayList<>();`
--     并自带注释「任务列表（目前为空，后续可扩展）」，所以永远回空；
--   · `createTask` / `updateTaskStatus` / `deleteTask` 只 `log.info` 一行就回
--     "…successfully"，**一个字节都不落库**。
-- 而 PC `AdminMessages.vue` 的整个「任务管理」tab（创建表单 + 四个统计卡 +
-- 状态筛选 + 行内改状态 + 单条/批量删除 + 分页）**早就写好了、也接对了**
-- （改读 `{total, items}`，见该文件 loadTasks 上的注释），缺的只是后端。
--
-- 所以这一轮是**补后端**，前端一行不用改（除了 `createdAt` 这个键名要对上）。
--
-- 口径（与页面上那四个统计卡一一对应）：
--   status   : pending(待处理) | in_progress(进行中) | completed(已完成)
--   priority : low(低) | medium(中) | high(高)
--
-- ⚠️ `updated_at` **不加 `ON UPDATE CURRENT_TIMESTAMP`** —— 与本仓其余表保持一致
--    （`updated_at` 全仓没有 MetaObjectHandler 自动填充），由 UPDATE 语句显式写。
-- ⚠️ 加表要**两处都改**：这里 + `academic-common/src/main/resources/init_schema.sql`。
--    改完**不要**跑 `db_backups/rebuild_schema.py`（它从静态 dump 读，会把已删的表复活）。
--
-- 可重复执行。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `admin_tasks` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `title` varchar(200) NOT NULL,
    `description` text,
    `priority` varchar(20) NOT NULL DEFAULT 'medium',
    `status` varchar(20) NOT NULL DEFAULT 'pending',
    -- 创建人（users.id）。管理员建的，所以这一列上是 users 表的取值空间，不是 student_profile.id
    `created_by` bigint DEFAULT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_admin_tasks_status` (`status`),
    KEY `idx_admin_tasks_created_by` (`created_by`),
    CONSTRAINT `fk_admin_tasks_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
