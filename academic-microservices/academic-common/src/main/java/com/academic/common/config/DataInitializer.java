package com.academic.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库自动初始化组件。
 *
 * <p>应用启动时检查 users 表是否存在，不存在则自动执行建表 + 种子数据脚本。
 * 所有 DDL 使用 {@code CREATE TABLE IF NOT EXISTS}，多服务并行启动时安全幂等。
 *
 * <p>本类有两条职责，缺一不可：<b>建库</b>与<b>自检</b>。此前只有前者 ——
 * 语句失败仅 {@code log.warn} 后继续，于是一个与线上库脱节的脚本会让全新库
 * "看起来建好了"实际半残（表少列、种子数据一条没进），而失败淹没在启动日志里。
 * 现在失败一律 {@code log.error} 并汇总计数，且事后跑一次结构自检对照
 * {@link #EXPECTED_COLUMNS}，漂移在启动日志里直接可见。
 *
 * <p>注意：users.role 是 TINYINT(1)，JDBC 驱动需要 tinyInt1isBit=false 参数。
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /**
     * 关键表的期望列数，自检基线。数字取自线上库 information_schema。
     *
     * <p>只覆盖"代码真正读写"的表：改表结构时若漏改 {@code init_schema.sql}，
     * 这里会在启动时报 ERROR 指出是哪张表少了多少列，而不是等到运行期
     * 抛 {@code Unknown column}（这类错误往往只在某个角色点开某个页面时才暴露）。
     *
     * <p>判据是"相等"而非"大于等于"：多出来的列通常无害，但少列一定有问题，
     * 而两者都值得知道。新增列后请同步更新本表与 {@code init_schema.sql}。
     */
    private static final Map<String, Integer> EXPECTED_COLUMNS = new LinkedHashMap<>();
    static {
        EXPECTED_COLUMNS.put("users", 11);
        EXPECTED_COLUMNS.put("student_profile", 13);
        EXPECTED_COLUMNS.put("teacher_profile", 8);
        EXPECTED_COLUMNS.put("counselor_profiles", 21);
        // 2026-10-12（W39）：9 → 7。这一条**本来就写错了**（活库与 init_schema.sql 都是 7 列），
        // 于是每个服务每次启动都把它报成一处"漂移"。同一个自检已经把 3 处过期基线一直报着红色，
        // 报久了就没人看 —— 而它正是本仓用来逮 schema 变更的机制。三处一起订正（见下）。
        EXPECTED_COLUMNS.put("colleges", 7);
        EXPECTED_COLUMNS.put("majors", 9);
        EXPECTED_COLUMNS.put("classes", 8);
        EXPECTED_COLUMNS.put("courses", 11);
        // 2026-10-10（W36）：14 → 15，新增 remark（教师录入/导入的「备注」）。
        // 判据是"相等"，加了列不改这行 ⇒ 每个服务启动都会 ERROR 报"少列"，
        // 那声警报本身是对的（它逮到了这次 schema 变更），不更新就等于把自己的自检喂瞎。
        EXPECTED_COLUMNS.put("scores", 15);
        // 2026-10-12（W39）：`score_record` 整条基线**移除**（原为 22）。
        // W36/W38 把成绩链路全量迁到权威表 scores 后，这张表全仓零读零写，
        // 已随 db_backups/score_record_drop_20261012_apply.sql 从活库与 init_schema.sql 一并删掉。
        // 基线留着的话，每个服务每次启动都会报 `score_record(表不存在)` —— 一声永远修不掉的假警报。
        EXPECTED_COLUMNS.put("warning_rules", 24);
        // 2026-10-12（W39）：21 → 22。这一条**本来就写错了**（活库与 init_schema.sql 都是 22 列，
        // 那 22 列里含 fail_count）。基线写 21 与上一条 9→7 是同一种过期，一并订正。
        EXPECTED_COLUMNS.put("academic_warning", 22);
        // 2026-10-12（W39）：8 → 9。同上，活库与 init_schema.sql 都是 9 列（多 progress / progress_note）。
        EXPECTED_COLUMNS.put("assistance_plans", 9);
        EXPECTED_COLUMNS.put("subscription_preferences", 13);
    }

    /**
     * ⚠️ <b>这里曾经有个 {@code @ConditionalOnBean(DataSource.class)}（2026-07-17 加入，2026-10-12 W39 删除），
     * 它让整个组件一次都没跑过。</b>
     *
     * <p>机制：{@code @ConditionalOnBean} 只在<b>用户自己声明</b>的 bean 上可靠。
     * 不写 {@code @Bean DataSource} 时它是**竞态**的 —— 评估这个条件时，自动配置的数据源
     * 可能还没注册，于是条件判假、本 bean 被静默跳过。后果不是报错，而是
     * "建库 + 种子数据 + 结构自检"**全部不发**：{@code logs/} 里 {@code 结构自检} 零命中即是证据。
     *
     * <p>把条件去掉是安全的：11 个依赖本模块的服务（除 gateway 外）都配了 {@code spring.datasource}，
     * 各自都会有一个 {@code DataSource}；而 gateway 根本不依赖 academic-common（已核 pom）。
     * 万一将来真有服务引了本模块又没配数据源，Spring 会在启动时**明确报错**
     * （"required a bean of type 'javax.sql.DataSource'"）—— 这比"静默不跑"好得多。
     */
    @Bean
    public CommandLineRunner initDatabase(DataSource dataSource) {
        return args -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {

                // 检查核心表是否存在（只要 users 表不存在就视为全新库）
                boolean freshDatabase;
                try (ResultSet tables = conn.getMetaData().getTables(null, null, "users", null)) {
                    freshDatabase = !tables.next();
                }

                if (freshDatabase) {
                    log.info("=== [DataInit] 检测到新数据库，开始自动建表... ===");
                    int schemaFailures = executeScript(stmt, "init_schema.sql");
                    log.info("=== [DataInit] 表结构创建完成（{} 张表） ===", countTables(conn));
                    int dataFailures = executeScript(stmt, "init_data.sql");
                    if (schemaFailures + dataFailures > 0) {
                        log.error("[DataInit] 初始化未完全成功：建表失败 {} 条、种子数据失败 {} 条。"
                                + "该库处于半残状态，请按上方 ERROR 逐条修复后重建。",
                                schemaFailures, dataFailures);
                    } else {
                        log.info("=== [DataInit] 种子数据插入完成 ===");
                    }
                } else {
                    log.info("[DataInit] 数据库已初始化，跳过建表。");
                }

                // 全新库和已有库都要校验：已有库被手工改过列才是最需要发现的场景
                verifySchema(conn);
            } catch (Exception e) {
                log.error("[DataInit] 数据库初始化失败: {}", e.getMessage(), e);
            }
        };
    }

    /**
     * 统计当前库的基表数量，用于启动日志。排除 {@code *_bak_*} 备份表。
     */
    private int countTables(Connection conn) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.TABLES "
                   + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' "
                   // 排除备份表；这里用 LIKE 的单字符通配符 `_` 而非转义写法，
                   // 免得依赖 NO_BACKSLASH_ESCAPES 的 sql_mode 设置
                   + "  AND TABLE_NAME NOT LIKE '%_bak_%'")) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 对照 {@link #EXPECTED_COLUMNS} 检查实际表结构，把漂移写成一条 ERROR。
     *
     * <p>表不存在与列数不符都算漂移。查询走 information_schema，不需要知道库名
     * （用 {@code DATABASE()}），因此在任何环境都能直接跑。
     */
    private void verifySchema(Connection conn) {
        List<String> drift = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM information_schema.COLUMNS "
                   + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> entry : EXPECTED_COLUMNS.entrySet()) {
                ps.setString(1, entry.getKey());
                try (ResultSet rs = ps.executeQuery()) {
                    int actual = rs.next() ? rs.getInt(1) : 0;
                    if (actual == 0) {
                        drift.add(entry.getKey() + "(表不存在)");
                    } else if (actual != entry.getValue()) {
                        drift.add(entry.getKey() + "(实际 " + actual
                                + " 列, 期望 " + entry.getValue() + " 列)");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[DataInit] 结构自检未能执行: {}", e.getMessage());
            return;
        }
        if (drift.isEmpty()) {
            log.info("[DataInit] 结构自检通过：{} 张关键表列数均与基线一致。", EXPECTED_COLUMNS.size());
        } else {
            log.error("[DataInit] 结构自检发现 {} 处漂移：{}。"
                    + "init_schema.sql 已与线上库脱节，或线上库被手工改过列；"
                    + "三处要一起改：init_schema.sql、活库、本表 EXPECTED_COLUMNS"
                    + "（⚠️ 别跑 db_backups/rebuild_schema.py —— 它从静态 dump 重建 init_schema.sql，会把新表/新列抹掉）。",
                    drift.size(), String.join(", ", drift));
        }
    }

    /**
     * 执行 classpath 下的 SQL 脚本，返回<b>失败语句条数</b>（0 表示全部成功）。
     *
     * <p>切分规则：逐行累积，遇到以分号结尾的行即视为一条完整语句。
     * 只按分号切分是不够的 —— 建表语句的列定义跨行，且 {@code comment '...'}
     * 里可能出现分号。当前脚本均满足"一条语句的末行以分号结尾"。
     *
     * <p>两类失败被区分对待：多服务竞争导致的"已存在/Duplicate"是幂等跳过（debug），
     * 其余是真正的失败（error，带完整 SQL 与脚本名），并计入返回值。
     */
    private int executeScript(Statement stmt, String scriptPath) {
        int executed = 0;
        int idempotentSkipped = 0;
        int failed = 0;
        try {
            ClassPathResource resource = new ClassPathResource(scriptPath);
            if (!resource.exists()) {
                log.error("[DataInit] 脚本不存在: {}（初始化将不完整）", scriptPath);
                return 1;
            }
            String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            String[] lines = sql.split("\n");

            StringBuilder current = new StringBuilder();
            for (String line : lines) {
                String trimmed = line.trim();
                // 跳过空行和注释
                if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("#")) {
                    continue;
                }
                current.append(" ").append(trimmed);
                // 分号结尾 = 一条完整语句
                if (!trimmed.endsWith(";")) {
                    continue;
                }
                String statement = current.toString().trim();
                // 去掉末尾分号（Statement.execute 不加分号也能执行，加了有时报错）
                if (statement.endsWith(";")) {
                    statement = statement.substring(0, statement.length() - 1).trim();
                }
                if (!statement.isEmpty()) {
                    try {
                        stmt.execute(statement);
                        executed++;
                    } catch (Exception e) {
                        String msg = e.getMessage() == null ? "" : e.getMessage();
                        // 忽略"已存在"类错误（多服务竞争/幂等执行）
                        if (msg.contains("already exists") || msg.contains("Duplicate")
                                || msg.contains("duplicate")) {
                            idempotentSkipped++;
                            log.debug("[DataInit] 幂等跳过: {}", msg.split("\n")[0]);
                        } else {
                            failed++;
                            log.error("[DataInit] 语句执行失败 (脚本={}): {}\n  SQL: {}",
                                    scriptPath, msg.split("\n")[0],
                                    statement.substring(0, Math.min(300, statement.length())));
                        }
                    }
                }
                current.setLength(0);
            }
            log.info("[DataInit] 脚本 {} 完成: 成功 {} 条, 幂等跳过 {} 条, 失败 {} 条",
                    scriptPath, executed, idempotentSkipped, failed);
        } catch (Exception e) {
            log.error("[DataInit] 脚本读取异常 {}: {}", scriptPath, e.getMessage(), e);
            return failed + 1;
        }
        return failed;
    }
}
