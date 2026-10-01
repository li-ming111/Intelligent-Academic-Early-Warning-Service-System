package com.academic.service.impl;

import com.academic.entity.SupportResource;
import com.academic.exception.ResourceNotFoundException;
import com.academic.mapper.SupportResourceMapper;
import com.academic.service.SupportResourceService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 支持资源（support_resource）的业务实现。
 *
 * <p><b>2026-09-30 第九轮修的四处（与 warning-service 同一批缺陷类型）：</b>
 * <ol>
 *   <li><b>失败被包成成功</b>：查详情/改/删三条路径原先把"资源不存在"写成返回值里的一段
 *       {@code {status:"error"}}，而 controller 无条件包 {@code ApiResponse.success(...)}
 *       ⇒ HTTP 200 + {@code code=200}。改为抛 {@link ResourceNotFoundException}。</li>
 *   <li><b>GET 在写、而且写错了列</b>：{@code getResourceDetail} 是"查看详情"，原来累加的是
 *       {@code downloads}；真正的浏览计数 {@code views} 在全仓**没有任何写入方**
 *       （种子里那 4 个值 150/200/80/120 永远不会变）。</li>
 *   <li><b>列表的 status 筛选被静默否掉</b>：先是按调用方给的 status 建条件，紧接着又无条件
 *       AND 一个 {@code status=1} ⇒ {@code ?status=0} 恒返回空。</li>
 *   <li><b>写路径静默丢字段</b>：{@code add}/{@code update} 只读 8 个 key，
 *       {@code content}/{@code author}/{@code fileSize}/{@code fileType} 从来没读过，
 *       调用方传了也被无声丢掉（而 {@code content}/{@code author} 在种子里是有值的：
 *       27~45 字正文 + 作者名，读路径也一直返回它们）。</li>
 * </ol>
 *
 * <!-- 注：本模块的 GlobalExceptionHandler 没有 404 分支，RuntimeException 一律翻成 500，
 *      所以"不存在"必须由 controller 显式 catch 成 404。 -->
 */
@Slf4j
@Service
public class SupportResourceServiceImpl implements SupportResourceService {

    /** 在架状态。列表/搜索默认只看它。 */
    private static final int STATUS_ACTIVE = 1;

    /** 软删除标记 —— {@code deleteResource} 是置 0，不是真删。 */
    private static final int STATUS_DELETED = 0;

    /** 新增时的两个计数初值。 */
    private static final int INITIAL_COUNTER = 0;

    private final SupportResourceMapper supportResourceMapper;

    public SupportResourceServiceImpl(SupportResourceMapper supportResourceMapper) {
        this.supportResourceMapper = supportResourceMapper;
    }

    @Override
    public Object getResourceList(Map<String, Object> params) {
        log.info("Getting resource list: {}", params);

        QueryWrapper<SupportResource> queryWrapper = new QueryWrapper<>();

        // ⚠️ 原来这里对 status 是两套互相打架的写法：先按 params 里的 status 建条件，
        // 紧接着又**无条件** AND 一个 status=1 —— 于是 /resources/list?status=0 恒返回空
        // （status=0 AND status=1），调用方的筛选被静默否掉。现在：给了就听给的，没给才默认在架。
        String status = str(params, "status");
        if (status != null) {
            queryWrapper.eq("status", status);
        } else {
            queryWrapper.eq("status", STATUS_ACTIVE);
        }

        // 空串一律当"没筛"（原来 containsKey 判空串会 eq("", ...) ⇒ 同样静默返回空）。
        String type = str(params, "type");
        if (type != null) {
            queryWrapper.eq("type", type);
        }
        String category = str(params, "category");
        if (category != null) {
            queryWrapper.eq("category", category);
        }

        queryWrapper.orderByDesc("created_at");
        List<SupportResource> resources = supportResourceMapper.selectList(queryWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("resources", resources);
        result.put("total", resources.size());

        return result;
    }

    @Override
    public Object getResourceDetail(String resourceId) {
        log.info("Getting resource detail: {}", resourceId);

        Long id = parseResourceId(resourceId);
        SupportResource resource = supportResourceMapper.selectById(id);

        if (resource == null) {
            // 原来 return 一段 {status:"error", message:"资源不存在"} 的 map，controller
            // 无条件包成 ApiResponse.success(...) —— 失败报成成功。抛异常 → controller 翻成 404。
            throw new ResourceNotFoundException("资源不存在: " + id);
        }

        // ⚠️ 这是一次 GET（查看详情），却在写库 —— 而且写的是 downloads。
        // 真正的浏览计数 views 全仓没有任何写入方（grep "views" 在 academic-microservices 下
        // 只命中实体字段声明，无 reader 无 writer），种子里那几个数永远不会变。
        // 净效果：标着"下载次数"的列统计的是"详情被打开的次数"，而"浏览次数"是个死列 ——
        // 又一个"报告与事实不符"。现在按语义各归各位：打开详情记 views。
        // 副作用（有意留的）：downloads 从此没有写入方，停在种子值。真想要一个"下载"计数，
        // 得另开一个显式的下载端点 —— 那是设计决定，不在这里凭空造。
        resource.setViews(resource.getViews() != null ? resource.getViews() + 1 : 1);
        supportResourceMapper.updateById(resource);

        Map<String, Object> result = new HashMap<>();
        result.put("id", resource.getId());
        result.put("title", resource.getTitle());
        result.put("type", resource.getType());
        result.put("category", resource.getCategory());
        result.put("description", resource.getDescription());
        // content / author 原来不在详情里返回（而列表返回的是整个实体，反倒有）。
        result.put("content", resource.getContent());
        result.put("author", resource.getAuthor());
        result.put("url", resource.getUrl());
        result.put("filePath", resource.getFilePath());
        result.put("fileSize", resource.getFileSize());
        result.put("fileType", resource.getFileType());
        result.put("views", resource.getViews());
        result.put("downloads", resource.getDownloads());
        result.put("createdAt", resource.getCreatedAt());
        result.put("updatedAt", resource.getUpdatedAt());

        // 遗留问题（本轮**有意没改**，等拍板）：这里不过滤 status，所以被"删除"（置 0）的资源
        // 仍能用 id 直接查到、而且每查一次 views 还会涨。软删除到底该不该对详情接口生效，
        // 取决于是否要保留"管理员按 id 查已下架资源"的能力 —— 没有调用方可以佐证意图，
        // 所以只记录、不擅自改语义。已写进手册 §7 待定项。

        return result;
    }

    @Override
    public Object addResource(Map<String, Object> request) {
        log.info("Adding resource: {}", request);

        String title = str(request, "title");
        if (title == null) {
            // 原来是 return {status:"error",...} ⇒ controller 包成 success。改成抛异常 → 400。
            throw new IllegalArgumentException("资源标题不能为空");
        }

        // 下面这 4 个 key 原来压根没读：调用方传了 content/author/fileSize/fileType 都被静默
        // 丢掉（而 content/author 是库里有值、读路径也在返回的列）。
        String type = str(request, "type");
        String category = str(request, "category");
        String description = str(request, "description");
        String content = str(request, "content");
        String url = str(request, "url");
        String filePath = str(request, "filePath");
        String fileType = str(request, "fileType");
        String author = str(request, "author");
        String fileSizeRaw = str(request, "fileSize");
        // 新增路径：没给 fileSize 就留 null（列可空，updateById/insert 的 NOT_NULL 策略会把它
        // 排除在 INSERT 之外）；给了但不是数字则**明确报错**，不静默当没填。
        Long fileSize = fileSizeRaw == null ? null : parseLong(fileSizeRaw, "fileSize");

        SupportResource resource = new SupportResource()
                .setTitle(title)
                .setType(type)
                .setCategory(category)
                .setDescription(description)
                .setContent(content)
                .setUrl(url)
                .setFilePath(filePath)
                .setFileSize(fileSize)
                .setFileType(fileType)
                .setAuthor(author)
                .setViews(INITIAL_COUNTER)
                .setDownloads(INITIAL_COUNTER)
                .setStatus(STATUS_ACTIVE)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());

        supportResourceMapper.insert(resource);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "资源添加成功");
        result.put("resourceId", resource.getId());

        return result;
    }

    @Override
    public Object updateResource(String resourceId, Map<String, Object> request) {
        log.info("Updating resource: {} {}", resourceId, request);

        Long id = parseResourceId(resourceId);
        SupportResource resource = supportResourceMapper.selectById(id);

        if (resource == null) {
            throw new ResourceNotFoundException("资源不存在: " + id);
        }

        // 原来每个字段都是 `containsKey(k) ? request.get(k).toString() : 不动` ——
        // 传 "title": null 时 containsKey 仍为 true，null.toString() 直接 NPE，
        // 冒成 500 + 一句没有上下文的 Java 异常。
        // 现在每个字段三态分明：没给这个 key → 不动；给了 null → **明确报错**；给了值 → 原样写入。
        // 为什么 null 不能"当没填"：MyBatis-Plus 的 updateById 是 NOT_NULL 策略，null 会被
        // 静默排除在 SET 之外，于是接口报"更新成功"却一个字节都没写 —— 正是这一轮在修的那种话。
        if (has(request, "title")) {
            String title = value(request, "title");
            if (title.trim().isEmpty()) {
                throw new IllegalArgumentException("资源标题不能为空");
            }
            resource.setTitle(title);
        }
        if (has(request, "type")) {
            resource.setType(value(request, "type"));
        }
        if (has(request, "category")) {
            resource.setCategory(value(request, "category"));
        }
        if (has(request, "description")) {
            resource.setDescription(value(request, "description"));
        }
        if (has(request, "content")) {
            resource.setContent(value(request, "content"));
        }
        if (has(request, "url")) {
            resource.setUrl(value(request, "url"));
        }
        if (has(request, "filePath")) {
            resource.setFilePath(value(request, "filePath"));
        }
        if (has(request, "fileType")) {
            resource.setFileType(value(request, "fileType"));
        }
        if (has(request, "author")) {
            resource.setAuthor(value(request, "author"));
        }
        if (has(request, "fileSize")) {
            resource.setFileSize(parseLong(value(request, "fileSize"), "fileSize"));
        }

        // updated_at 不显式写也会动（活库 DDL 带 ON UPDATE CURRENT_TIMESTAMP，2026-09-30 打靶确认）；
        // 显式写是为了让时间戳由应用决定、不依赖 DB 侧行为。
        resource.setUpdatedAt(LocalDateTime.now());

        supportResourceMapper.updateById(resource);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "资源更新成功");
        result.put("resourceId", resourceId);

        return result;
    }

    @Override
    public Object deleteResource(String resourceId) {
        log.info("Deleting resource: {}", resourceId);

        Long id = parseResourceId(resourceId);
        SupportResource resource = supportResourceMapper.selectById(id);

        if (resource == null) {
            // 原来同上：{status:"error"} 被包成 success。抛异常 → 404。
            throw new ResourceNotFoundException("资源不存在: " + id);
        }

        resource.setStatus(STATUS_DELETED);
        resource.setUpdatedAt(LocalDateTime.now());
        supportResourceMapper.updateById(resource);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "资源删除成功");
        result.put("resourceId", resourceId);

        return result;
    }

    @Override
    public Object searchResources(Map<String, Object> request) {
        log.info("Searching resources: {}", request);

        String keyword = str(request, "keyword");
        String type = str(request, "type");

        QueryWrapper<SupportResource> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", STATUS_ACTIVE);

        if (keyword != null) {
            queryWrapper.and(qw -> qw.like("title", keyword).or().like("description", keyword));
        }
        if (type != null) {
            queryWrapper.eq("type", type);
        }

        queryWrapper.orderByDesc("created_at");
        List<SupportResource> resources = supportResourceMapper.selectList(queryWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("resources", resources);
        result.put("total", resources.size());

        return result;
    }

    // ==================== 取值/校验的小工具 ====================
    //
    // 口径与 warning-service 的同名工具一致：空白 → 视为没填（null），
    // 填了但不是数字 → **明确报错**，绝不静默当没填 —— 静默丢弃正是这一轮在扫的缺陷类型。

    /** 取字符串：null / 全空白都算没填。params 本身为 null 也安全。 */
    private static String str(Map<String, Object> request, String key) {
        Object v = request == null ? null : request.get(key);
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /** 请求里到底有没有这个键（用于区分"没给"和"给了 null"）。 */
    private static boolean has(Map<String, Object> request, String key) {
        return request != null && request.containsKey(key);
    }

    /**
     * 取一个"已经确认存在"的字段值。
     *
     * <p>值为 null 时抛异常而不是返回 null：updateById 的 NOT_NULL 策略会把 null 静默跳过，
     * 若这里放过去，接口就会报"更新成功"却没写 —— 宁可报错。
     */
    private static String value(Map<String, Object> request, String key) {
        Object v = request.get(key);
        if (v == null) {
            throw new IllegalArgumentException("字段 " + key + " 不能为 null：更新接口只更新非 null 字段，"
                    + "传 null 会被静默忽略（当前实现无法用这种方式把列清空）");
        }
        return v.toString();
    }

    /** 字符串 → Long。空串/非数字都明确报错，绝不静默当没填。 */
    private static Long parseLong(String raw, String key) {
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("字段 " + key + " 不是数字: " + raw);
        }
    }

    /** 路径上的资源ID。写不出数字时给一句人话，而不是裸的 NumberFormatException。 */
    private static Long parseResourceId(String resourceId) {
        if (resourceId == null || resourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("资源ID不能为空");
        }
        try {
            return Long.valueOf(resourceId.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("资源ID不是数字: " + resourceId);
        }
    }
}
