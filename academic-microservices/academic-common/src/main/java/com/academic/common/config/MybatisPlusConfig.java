package com.academic.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置（全服务共用，放在 academic-common 里）。
 *
 * <p><b>为什么必须有这个类</b>：MyBatis-Plus 的分页**不是** {@code BaseMapper.selectPage}
 * 自己实现的，而是靠 {@link PaginationInnerInterceptor} 改写 SQL（拼 {@code LIMIT}）
 * 并回填 {@code total}。全仓此前**没有任何一处注册过拦截器**（grep 全仓 0 命中），
 * 于是 6 个服务里 11 个 {@code selectPage} 调用点全是空转 ——
 * 实测（2026-09-30，student-service 日志）{@code selectPage} 被当成 {@code selectList} 执行，
 * 发出去的 SQL 是：
 * <pre>
 *   NotificationMapper.selectList : Preparing: SELECT ... FROM notifications
 *                                   WHERE (user_id = ? AND is_deleted = ?) ORDER BY created_at DESC
 * </pre>
 * <b>没有 LIMIT</b>。所以 {@code /students/notification-center/27/list?page=1&pageSize=1}
 * 会同时给出 `pageSize: 1`、`total: 0`、`list` 2 条 —— 一个自相矛盾的响应：
 * 说好只取 1 条却返回了全部，说好告诉你总数却是 0。任何"加载更多"都会越翻越多重复行。
 *
 * <p><b>覆盖范围是实测过的，不是推断</b>：11 个调用点分布在 6 个服务
 * （auth 3：角色/权限/角色权限；college 2：学院/专业；course 2：课程/教师课程；
 * message 1；student 2：申诉历史/通知列表；user 1）。本类放在
 * {@code com.academic.common.config}，与 {@code GatewayAuthFilter} 同包，
 * 而所有服务的启动类都在 {@code com.academic}，所以扫得到；
 * 并在 student-service 之外**另起了一个进程实测**（2026-09-30，college-service）：
 * {@code GET /colleges/list?page=1&size=1} → {@code total=4, pages=4, data 1 条}，
 * 日志里同时出现 {@code SELECT COUNT(*) AS total FROM colleges} 与 {@code ... LIMIT ?}
 * （这两条都只有在拦截器生效时才可能发出）；{@code page=2} 下发 {@code LIMIT ?,?} 且换了行，
 * {@code page=99} 因为 {@code overflow=false} 返回 0 条。
 *
 * <p><b>对前端的影响（逐条查过调用方）</b>：
 * <ul>
 *   <li>小程序 {@code pages/student/notification-center.vue} **真的在用**这个端点
 *       （{@code size=50}），而且它本来就只读 {@code list} 不读 {@code total}
 *       （见该文件注释），所以这次改动对它只有好处：{@code total} 从恒 0 变成真值。
 *       唯一的新行为是"超过 50 条会被截断"——以前不会，因为以前根本没有 LIMIT；
 *       当前每个学生的通知量级是 2 条。</li>
 *   <li>PC 端 {@code views/student/NotificationCenter.vue} 与
 *       {@code views/student/AppealManagement.vue} 也调这两个端点，但这两页读响应用的是
 *       {@code response.data?.code}/{@code response.data.data}，而 {@code api/client.js}
 *       的响应拦截器**已经把内层 data 解包返回**了 —— 分支恒假、没有 else，
 *       {@code list}/{@code total} 一次都没被赋值，**页面本来就是空的**。
 *       也就是说本类不会让这两页"突然少几行"，它们现在一行都没有。
 *       这条前端缺陷另有记录与探针（{@code testsprite/local-check/student-notify-empty.mjs}），
 *       **本次未修**：它属于"响应解包约定"这一个横切问题，4 个页面 43 处，要单独做。</li>
 *   <li>auth / college / course / user 的分页端点：全仓前端**无调用**
 *       （{@code /users/list} 是休眠端点，用户管理页走的是 admin-service 自己手写切片的
 *       {@code /admin/users/page}，不走 {@code selectPage}）。</li>
 * </ul>
 *
 * <p>{@code maxLimit = 500} 只对**正数**页码生效：超过 500 一律按 500 处理。
 *
 * <p>⚠️ 实测（2026-09-30）它**挡不住 {@code pageSize=-1}**：MyBatis-Plus 把
 * {@code size < 0} 当作"不要分页"的信号，拦截器直接放行，SQL 里既不拼 LIMIT
 * 也不查 count，于是又回到 {@code total: 0} + 全部行。也就是说
 * {@code ?pageSize=-1} 仍可绕过分页把整表拉出来。
 * 没在这层堵它，是因为拦截器没有"强制改正负数 size"的开关 —— 要堵得改调用方
 * （把入参夹紧到 [1, maxLimit]）。当前所有分页接口的默认值都是正数
 * （如 {@code /students/notification-center/{id}/list} 默认 pageSize=10），
 * 只有调用方**主动**传负数才会走到，不构成默认路径上的风险。
 */
@Configuration
public class MybatisPlusConfig {

    /** 单页上限（仅对正数 pageSize 生效，见类注释里那条实测出来的例外）。 */
    private static final long MAX_PAGE_SIZE = 500L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        // 页码越界时返回空列表，而不是"回到第一页"——后者会让前端以为还有数据、无限翻下去
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
