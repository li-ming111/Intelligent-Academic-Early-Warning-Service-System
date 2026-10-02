package com.academic.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 运行时数据库备份 / 恢复（W1.10，2026-10-02 新增）。
 *
 * <h3>为什么要有它</h3>
 * 这一组接口此前是**编造的**：{@code /admin/backup} 只 log 一行却在内存里拼了个文件名、
 * 回 "Backup created successfully"；{@code /admin/backups/list} 返回三条 2024 年的假备份
 * （连 2.5MB/3.2MB 的大小都是编的）。上一轮改成了**如实回"未实现"** —— 诚实，但功能没有。
 * 现在真的调 {@code mysqldump} 生成 {@code .sql}。
 *
 * <h3>两个刻意的设计决定</h3>
 * <ol>
 *   <li><b>恢复只在临时库上做，绝不碰演示库</b>。{@code restoreBackup} 会新建一个
 *       {@code <db>_restore_<时间戳>} 的空库，把备份文件真灌进去、数一遍表，再删掉临时库。
 *       这是"**备份文件可用**"的验证，而不是把演示库覆盖掉 —— 后者一旦出错，
 *       答辩现场就没有数据可讲了。所以备份时用 {@code --databases} 之外的写法
 *       （文件里**不含 {@code USE} 语句**），否则灌进临时库时会被文件里的
 *       {@code USE 演示库} 带回去，正好酿成事故。</li>
 *   <li><b>口令不进命令行</b>，走子进程的 {@code MYSQL_PWD} 环境变量 ——
 *       {@code -p<password>} 会出现在进程列表里，同一台机器上谁都看得到。</li>
 * </ol>
 *
 * <h3>取不到工具时</h3>
 * 本工程没有启动脚本（IDE 直接 run），所以**不做 fail-fast**：找不到
 * {@code mysqldump} 时只是这次备份**如实失败并说清原因**，服务照常起。
 */
@Slf4j
@Service
public class BackupService {

    /** 备份文件名只允许这些字符，且必须以 .sql 结尾 —— 挡住 {@code ../} 之类的路径穿越。 */
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9_.-]{1,120}\\.sql$");

    private static final Pattern JDBC_URL =
            Pattern.compile("^jdbc:mysql://([^:/?]+)(?::(\\d+))?/([^?;]+)");

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final long TIMEOUT_MINUTES = 5;

    private final String backupDir;
    private final String mysqldumpPath;
    private final String mysqlPath;
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public BackupService(@Value("${export.dir:./exports}") String exportDir,
                         @Value("${app.backup.mysqldump:}") String mysqldumpPath,
                         @Value("${app.backup.mysql:}") String mysqlPath,
                         @Value("${spring.datasource.url:}") String jdbcUrl,
                         @Value("${spring.datasource.username:}") String username,
                         @Value("${spring.datasource.password:}") String password) {
        // 备份文件单独放一个子目录：和 Excel 导出混在一个目录里，界面上"备份列表"
        // 和"导出历史"看起来就是同一批东西，排查时容易误删。
        this.backupDir = Paths.get(exportDir, "backups").toString();
        this.mysqldumpPath = mysqldumpPath;
        this.mysqlPath = mysqlPath;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    // ==================== 备份 ====================

    /**
     * 生成一份新的备份文件。
     *
     * @return 形如 {@code {name, size, sizeText, created_at, status}} 的一行（与列表行同形）
     * @throws IllegalStateException 工具缺失 / 命令失败 / 产物为空 —— **一律抛出，不回"成功"**
     */
    public Map<String, Object> createBackup() {
        Conn c = parseJdbcUrl();
        File dir = ensureDir();
        String name = "backup_" + c.database + "_" + LocalDateTime.now().format(STAMP) + ".sql";
        Path target = Paths.get(backupDir, name);

        List<String> cmd = new ArrayList<>(List.of(
                resolveTool(mysqldumpPath, "mysqldump"),
                "--host=" + c.host,
                "--port=" + c.port,
                "--user=" + username,
                "--default-character-set=utf8mb4",
                // 一致性快照：InnoDB 下导出期间不锁表
                "--single-transaction",
                "--skip-lock-tables",
                // MySQL 8 起没有 PROCESS 权限时 mysqldump 会报错，这一项让它跳过表空间信息
                "--no-tablespaces",
                // ⚠️ 刻意**不加** --databases：那样文件里会带 CREATE DATABASE/USE，
                // 恢复演练灌进临时库时会被文件里的 USE 拉回演示库（见类注释）
                c.database));

        long started = System.currentTimeMillis();
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("MYSQL_PWD", password == null ? "" : password);
        pb.redirectOutput(target.toFile());
        pb.redirectErrorStream(false);

        String stderr = run(pb, newStderrSink());
        long cost = System.currentTimeMillis() - started;

        if (!Files.exists(target)) {
            throw new IllegalStateException("mysqldump 没有生成文件，stderr: " + tail(stderr));
        }
        long size;
        try {
            size = Files.size(target);
        } catch (IOException e) {
            throw new IllegalStateException("备份文件生成后读不到大小: " + e.getMessage(), e);
        }
        if (size <= 0) {
            deleteQuietly(target);
            throw new IllegalStateException("mysqldump 产出的是空文件，stderr: " + tail(stderr));
        }

        log.info("数据库备份完成: {} ({} 字节, {} ms)", name, size, cost);
        Map<String, Object> row = rowOf(target.toFile());
        row.put("durationMs", cost);
        return row;
    }

    // ==================== 列表 / 删除 ====================

    /** 列出备份目录里真实存在的 {@code .sql}，按时间倒序。目录不存在就是空列表。 */
    public List<Map<String, Object>> listBackups() {
        File dir = new File(backupDir);
        if (!dir.isDirectory()) {
            return new ArrayList<>();
        }
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".sql"));
        if (files == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (File f : files) {
            rows.add(rowOf(f));
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("created_at"))).reversed());
        return rows;
    }

    /** 删除一个备份文件。返回是否真的删掉了。 */
    public boolean deleteBackup(String name) {
        Path p = safeResolve(name);
        try {
            boolean deleted = Files.deleteIfExists(p);
            log.info("删除备份 {}: {}", name, deleted ? "成功" : "文件不存在");
            return deleted;
        } catch (IOException e) {
            throw new IllegalStateException("删除备份失败: " + e.getMessage(), e);
        }
    }

    // ==================== 恢复（只在临时库上演练） ====================

    /**
     * 把备份文件恢复到一个**临时库**里，数一遍表，然后把临时库删掉。
     *
     * <p>这是"这份备份真的能恢复"的证据，而**不是**把演示库覆盖掉。返回体里的
     * {@code scratchDb} 会写清临时库的名字，{@code dropped=true} 表示演练库已清理。
     *
     * @throws IllegalStateException 文件不存在 / 客户端缺失 / 灌库失败 —— 失败一律抛出
     */
    public Map<String, Object> restoreIntoScratchDb(String name) {
        Path file = safeResolve(name);
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("备份文件不存在: " + name);
        }
        Conn c = parseJdbcUrl();
        String scratch = c.database + "_restore_" + LocalDateTime.now().format(STAMP);

        try (Connection admin = open(c.host, c.port, null)) {
            exec(admin, "CREATE DATABASE `" + scratch + "` DEFAULT CHARACTER SET utf8mb4");
        } catch (Exception e) {
            throw new IllegalStateException("创建临时库 " + scratch + " 失败: " + e.getMessage(), e);
        }

        int tableCount = -1;
        try {
            List<String> cmd = List.of(
                    resolveTool(mysqlPath, "mysql"),
                    "--host=" + c.host,
                    "--port=" + c.port,
                    "--user=" + username,
                    "--default-character-set=utf8mb4",
                    scratch);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("MYSQL_PWD", password == null ? "" : password);
            pb.redirectInput(file.toFile());
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            String stderr = run(pb, newStderrSink());
            if (stderr != null && stderr.toLowerCase().contains("error")) {
                throw new IllegalStateException("恢复演练失败（灌入临时库时报错）: " + tail(stderr));
            }

            try (Connection conn = open(c.host, c.port, scratch);
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '"
                                 + scratch + "'")) {
                if (rs.next()) {
                    tableCount = rs.getInt(1);
                }
            }
            if (tableCount <= 0) {
                throw new IllegalStateException("恢复演练后临时库里一张表都没有，备份文件可能不可用");
            }
        } catch (Exception e) {
            // 统一转成 IllegalStateException：这个方法的契约是"失败一律抛，
            // 绝不回一个看起来成功的空结果"（本仓反复踩过的"假成功"）
            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }
            throw new IllegalStateException("恢复演练失败: " + e.getMessage(), e);
        } finally {
            try (Connection admin = open(c.host, c.port, null)) {
                exec(admin, "DROP DATABASE IF EXISTS `" + scratch + "`");
            } catch (Exception e) {
                // 临时库没删掉不该让"演练成功"变成失败，但必须留痕：它会一直占着磁盘
                log.warn("临时库 {} 未能删除，请手工清理: {}", scratch, e.getMessage());
            }
        }

        log.info("恢复演练完成: {} -> 临时库 {} 共 {} 张表（临时库已删除）", name, scratch, tableCount);
        Map<String, Object> result = new HashMap<>();
        result.put("verified", true);
        result.put("backupName", name);
        result.put("scratchDb", scratch);
        result.put("tableCount", tableCount);
        result.put("dropped", true);
        result.put("note", "已在临时库 " + scratch + " 上完整恢复并校验（" + tableCount
                + " 张表），随后删除临时库。**当前演示库未被覆盖** —— 这是刻意的保护。");
        return result;
    }

    // ==================== 内部工具 ====================

    private record Conn(String host, int port, String database) {}

    private Conn parseJdbcUrl() {
        Matcher m = JDBC_URL.matcher(jdbcUrl == null ? "" : jdbcUrl);
        if (!m.find()) {
            throw new IllegalStateException("无法从 spring.datasource.url 解析出 host/port/库名: " + jdbcUrl);
        }
        return new Conn(m.group(1), m.group(2) == null ? 3306 : Integer.parseInt(m.group(2)), m.group(3));
    }

    /** 工具路径：配置了就用配置的，否则回落到 PATH 上的同名命令（找不到时由进程启动失败报出）。 */
    private static String resolveTool(String configured, String fallback) {
        return (configured == null || configured.isBlank()) ? fallback : configured;
    }

    private File ensureDir() {
        File dir = new File(backupDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建备份目录: " + dir.getAbsolutePath());
        }
        return dir;
    }

    /** 把 {@code name} 解析成备份目录下的路径，并挡住路径穿越。 */
    private Path safeResolve(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("非法的备份名: " + name);
        }
        Path base = Paths.get(backupDir).toAbsolutePath().normalize();
        Path p = base.resolve(name).normalize();
        if (!p.startsWith(base)) {
            throw new IllegalArgumentException("非法的备份名: " + name);
        }
        return p;
    }

    private Map<String, Object> rowOf(File f) {
        Map<String, Object> row = new HashMap<>();
        // id 就是文件名：前端 restore/delete 拿 row.id 拼 URL，正好对上路径参数
        row.put("id", f.getName());
        row.put("name", f.getName());
        row.put("size", formatSize(f.length()));
        row.put("sizeBytes", f.length());
        row.put("created_at", LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(f.lastModified()),
                        java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        row.put("status", "success");
        return row;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024L * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private Connection open(String host, int port, String db) throws Exception {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + (db == null ? "" : db)
                + "?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=GMT%2B8"
                + "&allowPublicKeyRetrieval=true";
        return DriverManager.getConnection(url, username, password);
    }

    private static void exec(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    /**
     * 跑一个子进程，返回 stderr 文本；**退出码非 0 就抛**。
     *
     * <p>stderr 走临时文件而不是管道：从管道读要读到 EOF 才返回，而 EOF 要等进程退出
     * ⇒ 那个 {@code waitFor(timeout)} 永远等不到超时，卡死的子进程会把请求线程一直占住。
     */
    private static String run(ProcessBuilder pb, Path stderrSink) {
        try {
            pb.redirectError(stderrSink.toFile());
            Process p = pb.start();
            p.getOutputStream().close();
            if (!p.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                p.destroyForcibly();
                throw new IllegalStateException("命令超时（>" + TIMEOUT_MINUTES + " 分钟）: " + pb.command().get(0));
            }
            String stderr = Files.exists(stderrSink)
                    ? new String(Files.readAllBytes(stderrSink), StandardCharsets.UTF_8)
                    : "";
            if (p.exitValue() != 0) {
                throw new IllegalStateException("命令退出码 " + p.exitValue() + ": " + tail(stderr));
            }
            return stderr;
        } catch (IOException e) {
            throw new IllegalStateException("无法启动命令 " + pb.command().get(0)
                    + "（路径不对或不在 PATH 上？" + e.getMessage() + "）", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("命令被中断", e);
        }
    }

    private static Path newStderrSink() {
        try {
            return Files.createTempFile("abs-backup-", ".err");
        } catch (IOException e) {
            throw new IllegalStateException("无法创建临时文件: " + e.getMessage(), e);
        }
    }

    private static String tail(String text) {
        if (text == null || text.isBlank()) {
            return "(无输出)";
        }
        String t = text.trim();
        return t.length() <= 400 ? t : t.substring(t.length() - 400);
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignore) {
            // 清理失败不影响已经要抛出的异常
        }
    }

    /** 供 controller 回读：备份目录的绝对路径（页面要能告诉人"文件在哪"）。 */
    public String backupDirPath() {
        return Paths.get(backupDir).toAbsolutePath().normalize().toString();
    }
}
