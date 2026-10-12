package com.academic.service.impl;

import com.academic.common.entity.AcademicWarning;
import com.academic.common.entity.CommunicationLog;
import com.academic.common.entity.Student;
import com.academic.common.notify.NotificationPreferences;
import com.academic.common.notify.StudentNotifier;
import com.academic.entity.ClassInfo;
import com.academic.mapper.ClassInfoMapper;
import com.academic.mapper.CommunicationLogMapper;
import com.academic.mapper.StudentMapper;
import com.academic.mapper.WarningMapper;
import com.academic.service.CounselorNotificationService;
import com.academic.service.StudentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 辅导员通知服务实现。
 *
 * <p>⚠️ 本类的写方法**不能在方法内部 try/catch**：事务边界在 service 方法上，
 * controller 的 catch 在事务边界之外。在方法内吞掉异常会让 {@code @Transactional}
 * 变成一个只会回滚自己半截的假事务（本项目在班级管理那组写路径上踩过同样的坑）。
 */
@Slf4j
@Service
public class CounselorNotificationServiceImpl implements CounselorNotificationService {

    private static final String KEY_ALL = "all";
    private static final String KEY_WARNING_STUDENTS = "warning-students";

    @Autowired
    private CommunicationLogMapper communicationLogMapper;

    // 发通知共用实现（id 空间转换 + 偏好闸 + 落库），见 academic-common。
    // 本类不再自己 insert notifications —— 学生关掉的开关必须真的生效（W2）。
    @Autowired
    private StudentNotifier studentNotifier;

    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private ClassInfoMapper classInfoMapper;

    @Autowired
    private WarningMapper warningMapper;

    // ==================== 发送 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> send(Map<String, Object> data) {
        Map<String, Object> payload = data == null ? Collections.emptyMap() : data;

        String title = text(payload.get("title"));
        String body = firstNotEmpty(text(payload.get("content")), text(payload.get("message")));
        if (body.isEmpty()) {
            throw new IllegalArgumentException("通知内容为空：请求体里 content / message 至少要有一个");
        }
        String category = resolveCategory(payload.get("type"));

        Long counselorId = firstLong(payload.get("counselorId"), payload.get("counselor_id"));

        List<String> scopes = new ArrayList<>();
        for (Object o : asList(payload.get("recipients"))) {
            String s = text(o);
            if (!s.isEmpty()) {
                scopes.add(s);
            }
        }
        // ⚠️ 两套 id 空间**分开收**（W38，2026-10-10）。`student_ids` 一族语义是
        // `student_profile.id`（名册/成绩页聚合出来的就是它）；`userIds`/`user_ids` 语义是
        // `users.id`（学生名册页发送框传的就是它）。此前两者混进一个列表，再靠
        // `findStudent` "先按 profile.id 查、查不到再按 user_id 查"兜底 —— 只因活库
        // `student_profile.id ≤ 26 < users.id ∈ [27,52]` 恰好不重叠才对；一旦重叠就会
        // **静默发给另一个学生**。现在按声明的键各查各的空间。
        List<Object> profileIds = new ArrayList<>();
        for (String key : new String[]{"student_ids", "studentIds", "studentId", "student_id"}) {
            profileIds.addAll(asList(payload.get(key)));
        }
        List<Object> userIds = new ArrayList<>();
        for (String key : new String[]{"userIds", "user_ids"}) {
            userIds.addAll(asList(payload.get(key)));
        }

        Resolution res = resolve(counselorId, scopes, profileIds, userIds);
        if (res.students.isEmpty()) {
            String why = res.skipped.isEmpty()
                    ? "请求体里没有任何接收人（receivers/student_ids 都是空的）"
                    : "接收人一个都没解析出来：" + String.join("；", res.skipped);
            throw new IllegalArgumentException(why);
        }

        // 同一批共用一个时间戳：通知历史靠 (category, content, created_at) 把一次群发归并成一条。
        // 秒级截断 —— 这样一次群发的所有行时间戳完全相同，归并是精确的。
        LocalDateTime now = LocalDateTime.now().withNano(0);
        String storedText = title.isEmpty() ? body : title + "\n" + body;
        // 名册页那个发送框只收集正文、没有标题字段，而通知中心的列表要靠 title 显示，
        // 所以这里用正文首行兜一个**显示用**标题（不改动正文）。
        String displayTitle = title.isEmpty() ? displayTitle(body) : title;

        int logged = 0;
        int delivered = 0;
        List<String> undeliverable = new ArrayList<>();
        // 学生自己在设置里关掉了这类通知 —— 不是故障，但要说出来，
        // 否则辅导员会以为"已向 30 人发送"，实际只到了 20 人。
        List<String> muted = new ArrayList<>();

        for (Student s : res.students) {
            CommunicationLog cl = new CommunicationLog();
            cl.setCounselorId(counselorOf(s, counselorId));
            // communication_logs.student_id 是 student_profile.id
            cl.setStudentId(s.getId());
            cl.setContent(storedText);
            cl.setCategory(category);
            cl.setStatus(0);
            cl.setCreatedAt(now);
            cl.setUpdatedAt(now);
            communicationLogMapper.insert(cl);
            logged++;

            if (s.getUserId() == null) {
                // 学生档案没绑 users.id —— notifications.user_id 是 NOT NULL，写进去只会炸，
                // 如实报出来而不是悄悄少发一个。
                undeliverable.add(s.getId() + "(" + s.getName() + ")");
                continue;
            }
            // 走共用发通知实现：学生把该类通知关掉时返回 false（不写库），
            // 而不是像以前那样照发不误。communication_logs 上面已经写了 ——
            // 那是辅导员自己的发送记录，无论学生收不收都该留痕。
            boolean sent = studentNotifier.notifyUser(s.getUserId(), s.getId(),
                    preferenceCategory(category), null, displayTitle, body, null);
            if (sent) {
                delivered++;
            } else {
                muted.add(s.getId() + "(" + s.getName() + ")");
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", "已向 " + delivered + " 名学生发送通知");
        resp.put("category", category);
        resp.put("recipients", res.students.size());
        resp.put("delivered", delivered);
        resp.put("recorded", logged);
        resp.put("skipped", res.skipped);
        resp.put("undeliverable", undeliverable);
        resp.put("muted", muted);
        resp.put("scopeNote", "receivers=warning-students 的口径：名下有 status=0（未处理）预警的学生");
        resp.put("muteNote", "muted = 学生已在设置里关闭该类通知，通知未写入其通知中心"
                + "（communication_logs 仍留痕，因为那是辅导员的发送记录）");
        return resp;
    }

    /**
     * 辅导员群发的 category（{@code warning/important/normal}）→ 偏好判定用的类别。
     *
     * <p>{@code warning} 用 {@link NotificationPreferences.Category#WARNING}（读
     * {@code subscribe_warnings} / {@code warning_notification}）；
     * 其余两种是普通群发，用 {@link NotificationPreferences.Category#CLASS}
     * （读 {@code user_settings.class_notification}）。
     *
     * <p>刻意<b>不</b>把 important 也当成 WARNING：辅导员发一条"重要通知"不等于系统预警，
     * 混用会让关了预警的学生连班级通知都收不到。
     */
    private static NotificationPreferences.Category preferenceCategory(String category) {
        return "warning".equalsIgnoreCase(category)
                ? NotificationPreferences.Category.WARNING
                : NotificationPreferences.Category.CLASS;
    }

    // ==================== 历史 ====================

    @Override
    public Map<String, Object> history(Long counselorId, int page, int size) {
        int safePage = page < 1 ? 1 : page;
        int safeSize = size < 1 ? 10 : size;

        LambdaQueryWrapper<CommunicationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommunicationLog::getCounselorId, counselorId)
                .in(CommunicationLog::getCategory, NOTIFICATION_CATEGORIES)
                .orderByDesc(CommunicationLog::getCreatedAt)
                .orderByDesc(CommunicationLog::getId);
        List<CommunicationLog> rows = communicationLogMapper.selectList(wrapper);

        // 同一次群发 = 同一 (category, content) + 同一时间戳，归并成一条，接收人数=行数
        Map<String, Map<String, Object>> groups = new LinkedHashMap<>();
        for (CommunicationLog row : rows) {
            String key = row.getCategory() + "\u0000" + row.getContent() + "\u0000" + row.getCreatedAt();
            Map<String, Object> g = groups.get(key);
            if (g == null) {
                g = new LinkedHashMap<>();
                g.put("id", row.getId());
                g.put("content", row.getContent());
                g.put("category", row.getCategory());
                g.put("createdAt", row.getCreatedAt());
                g.put("recipientCount", 0);
                g.put("studentIds", new ArrayList<Long>());
                groups.put(key, g);
            }
            g.put("recipientCount", ((Number) g.get("recipientCount")).intValue() + 1);
            @SuppressWarnings("unchecked")
            List<Long> ids = (List<Long>) g.get("studentIds");
            ids.add(row.getStudentId());
        }

        List<Map<String, Object>> all = new ArrayList<>(groups.values());
        int total = all.size();
        int from = (safePage - 1) * safeSize;
        List<Map<String, Object>> list = from >= total
                ? new ArrayList<>()
                : new ArrayList<>(all.subList(from, Math.min(total, from + safeSize)));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", total);
        resp.put("page", safePage);
        resp.put("size", safeSize);
        resp.put("list", list);
        resp.put("note", "一条 = 一次发送；同一批（内容与时间戳相同）归并，recipientCount 是实际写入的接收人数");
        return resp;
    }

    // ==================== 周计数 ====================

    @Override
    public Map<String, Object> weeklyCount(Long counselorId) {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDateTime thisWeekStart = monday.atStartOfDay();
        LocalDateTime lastWeekStart = monday.minusWeeks(1).atStartOfDay();

        long thisWeek = countSince(counselorId, thisWeekStart, null);
        long lastWeek = countSince(counselorId, lastWeekStart, thisWeekStart);
        long total = countSince(counselorId, null, null);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("thisWeek", thisWeek);
        resp.put("lastWeek", lastWeek);
        resp.put("total", total);
        resp.put("weekStart", thisWeekStart.toLocalDate().toString());
        resp.put("note", "统计口径：communication_logs 里 category 属于 " + NOTIFICATION_CATEGORIES
                + " 的行数（按发送时间）；一次群发发给 N 个学生算 N 条");
        return resp;
    }

    private long countSince(Long counselorId, LocalDateTime from, LocalDateTime toExclusive) {
        LambdaQueryWrapper<CommunicationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommunicationLog::getCounselorId, counselorId)
                .in(CommunicationLog::getCategory, NOTIFICATION_CATEGORIES);
        if (from != null) {
            wrapper.ge(CommunicationLog::getCreatedAt, from);
        }
        if (toExclusive != null) {
            wrapper.lt(CommunicationLog::getCreatedAt, toExclusive);
        }
        return communicationLogMapper.selectCount(wrapper);
    }

    // ==================== 接收人解析 ====================

    private static final class Resolution {
        final List<Student> students = new ArrayList<>();
        final List<String> skipped = new ArrayList<>();
    }

    private Resolution resolve(Long counselorId, List<String> scopes,
                               List<Object> profileIds, List<Object> userIds) {
        Resolution res = new Resolution();
        Set<Long> seen = new HashSet<>();

        boolean wantsAll = false;
        boolean wantsWarning = false;
        // scopes 里的裸数字 id 与两个显式列表同族，历史上一律按"哪个空间都试"解析；
        // 保留这条宽松口径（recipients 是老的自由文本入口，无 key 可依）。见下面的
        // studentByIdEitherSpace。
        List<Long> scopeIds = new ArrayList<>();
        for (String scope : scopes) {
            if (KEY_ALL.equalsIgnoreCase(scope)) {
                wantsAll = true;
            } else if (KEY_WARNING_STUDENTS.equalsIgnoreCase(scope)) {
                wantsWarning = true;
            } else {
                Long id = toLong(scope);
                if (id == null) {
                    res.skipped.add("无法识别的接收范围：" + scope);
                } else {
                    scopeIds.add(id);
                }
            }
        }

        List<Student> roster = counselorId == null ? null : studentService.getByCounselorId(counselorId);

        if (wantsAll || wantsWarning) {
            if (roster == null) {
                throw new IllegalArgumentException(
                        "缺少 counselorId / counselor_id，无法解析 recipients=[" + String.join(",", scopes) + "]");
            }
            if (wantsAll) {
                for (Student s : roster) {
                    addStudent(res, seen, s);
                }
            }
            if (wantsWarning) {
                Set<Long> pending = pendingWarningStudentIds(roster);
                for (Student s : roster) {
                    if (pending.contains(s.getId())) {
                        addStudent(res, seen, s);
                    }
                }
            }
        }

        // ⚠️ 三个来源**各查各的空间**（W38）：`student_ids` ⇒ student_profile.id，
        // `userIds`/`user_ids` ⇒ users.id，裸 scope 数字 ⇒ 两套都试（老的宽松入口）。
        // 别再把它们合并去查 —— 那正是 id 空间靠"顺序侥幸"的病根。
        for (Object raw : profileIds) {
            Long pid = toLong(raw);
            if (pid == null) {
                res.skipped.add("不是合法 id 的接收人：" + text(raw));
                continue;
            }
            addWanted(res, seen, roster, pid, Space.PROFILE);
        }
        for (Object raw : userIds) {
            Long uid = toLong(raw);
            if (uid == null) {
                res.skipped.add("不是合法 id 的接收人：" + text(raw));
                continue;
            }
            addWanted(res, seen, roster, uid, Space.USER);
        }
        for (Long id : scopeIds) {
            addWanted(res, seen, roster, id, Space.EITHER);
        }
        return res;
    }

    /** 显式 id 的 id 空间 —— 由它来自哪个键决定，不再靠"兜底顺序"猜。 */
    private enum Space { PROFILE, USER, EITHER }

    /** 按声明的 id 空间查人、做名册过滤、加入结果（三处 explicit 来源共用这一份）。 */
    private void addWanted(Resolution res, Set<Long> seen, List<Student> roster, Long id, Space space) {
        Student s = switch (space) {
            case PROFILE -> findByProfileId(id);
            case USER -> findByUserId(id);
            case EITHER -> findStudent(id);
        };
        if (s == null) {
            String how = space == Space.PROFILE ? "student_profile.id"
                    : space == Space.USER ? "users.id" : "student_profile.id / users.id";
            res.skipped.add("找不到学生（按 " + how + "）：" + id);
            return;
        }
        if (roster != null && roster.stream().noneMatch(x -> x.getId().equals(s.getId()))) {
            res.skipped.add("学生 " + s.getId() + "(" + s.getName() + ") 不在该辅导员名下，已跳过");
            return;
        }
        addStudent(res, seen, s);
    }

    private void addStudent(Resolution res, Set<Long> seen, Student s) {
        if (s == null || s.getId() == null || !seen.add(s.getId())) {
            return;
        }
        res.students.add(s);
    }

    /** 按 {@code student_profile.id} 找（{@code student_ids} 一族声明的空间）。 */
    private Student findByProfileId(Long profileId) {
        return studentMapper.selectById(profileId);
    }

    /** 按 {@code users.id} 找（{@code userIds}/{@code user_ids} 声明的空间）。 */
    private Student findByUserId(Long userId) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Student::getUserId, userId).last("LIMIT 1");
        List<Student> byUserId = studentMapper.selectList(wrapper);
        return byUserId.isEmpty() ? null : byUserId.get(0);
    }

    /**
     * 两套空间都试（先 profile.id、再 users.id）——**只给裸 scope 数字用**。
     *
     * <p>W38 前这是**所有**显式接收人共用的解析，于是 `student_ids` 与 `userIds`
     * 混在一起也"能不碰巧发错人"只是活库两个 id 空间不重叠的运气。现在只有
     * {@code recipients:[<裸数字>]}` 这个没有 key 可依的老入口还走它。
     */
    private Student findStudent(Long id) {
        Student byProfileId = findByProfileId(id);
        return byProfileId != null ? byProfileId : findByUserId(id);
    }

    private Set<Long> pendingWarningStudentIds(List<Student> roster) {
        List<Long> ids = roster.stream().map(Student::getId).filter(java.util.Objects::nonNull).collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptySet();
        }
        LambdaQueryWrapper<AcademicWarning> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AcademicWarning::getStudentId)
                .in(AcademicWarning::getStudentId, ids)
                .eq(AcademicWarning::getStatus, 0);
        return warningMapper.selectList(wrapper).stream()
                .map(AcademicWarning::getStudentId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** classes.counselor_id 存的是 users.id；名册页不传 counselorId 时由学生所在班级反推发送人。 */
    private Long counselorOf(Student s, Long fallback) {
        if (fallback != null) {
            return fallback;
        }
        if (s.getClassId() == null) {
            return null;
        }
        ClassInfo clazz = classInfoMapper.selectById(s.getClassId());
        return clazz == null ? null : clazz.getCounselorId();
    }

    // ==================== 小工具 ====================

    private static String resolveCategory(Object raw) {
        String s = text(raw).toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return "normal";
        }
        if (!NOTIFICATION_CATEGORIES.contains(s)) {
            // 不静默改成 normal：那会让这条记录在"通知历史"（按 category 过滤）里消失。
            throw new IllegalArgumentException("未知的通知类型：" + s + "，可选 " + NOTIFICATION_CATEGORIES);
        }
        return s;
    }

    private static String displayTitle(String body) {
        String firstLine = body.split("\r?\n", 2)[0].trim();
        return firstLine.length() <= 30 ? firstLine : firstLine.substring(0, 30) + "…";
    }

    private static String text(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String firstNotEmpty(String a, String b) {
        return a == null || a.isEmpty() ? (b == null ? "" : b) : a;
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long firstLong(Object a, Object b) {
        Long x = toLong(a);
        return x != null ? x : toLong(b);
    }

    private static List<Object> asList(Object o) {
        if (o == null) {
            return Collections.emptyList();
        }
        if (o instanceof List) {
            return (List<Object>) o;
        }
        if (o instanceof Object[]) {
            return Arrays.asList((Object[]) o);
        }
        String s = String.valueOf(o).trim();
        // "1,2,3" 也认，但空串不认（不然会变成一个空接收人）
        if (s.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(Arrays.asList((Object[]) s.split(",")));
    }
}
