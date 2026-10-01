package com.academic.service;

import java.util.Map;

/**
 * 支持资源（support_resource）服务。
 *
 * <p><b>失败怎么表达（2026-09-30 起的约定）：</b>全部**抛异常**，不再把
 * {@code {status:"error", message:...}} 塞在返回值里 —— 那种写法会被 controller 无条件包进
 * {@code ApiResponse.success(...)}，变成 HTTP 200 + {@code code=200}，
 * 前端 axios 拦截器（只认 {@code code === 200} 为成功）会把它当成功数据交给调用方。
 *
 * <ul>
 *   <li>{@link IllegalArgumentException} — 参数问题（缺 id / 不是数字 / 标题为空 / 传了 null），
 *       controller 翻成 {@code code=400}。</li>
 *   <li>{@code com.academic.exception.ResourceNotFoundException} — 目标资源不存在，
 *       controller 翻成 {@code code=404}。</li>
 * </ul>
 *
 * <p>返回值里的 {@code status:"success"} 只在成功时出现，不再承载失败语义。
 */
public interface SupportResourceService {
    /**
     * 获取支持资源列表
     *
     * <p>默认只返回在架资源（{@code status=1}）；{@code params} 里显式给了 {@code status} 就以它为准
     * （传 {@code status=0} 可以查到已软删除的）。
     */
    Object getResourceList(Map<String, Object> params);

    /**
     * 获取资源详情
     *
     * <p>⚠️ 这是唯一一个**会写库的读接口**：它会把该资源的 {@code views} 加一。
     *
     * @throws com.academic.exception.ResourceNotFoundException 资源不存在
     * @throws IllegalArgumentException 资源ID为空或不是数字
     */
    Object getResourceDetail(String resourceId);

    /**
     * 新增支持资源
     *
     * @throws IllegalArgumentException 标题为空，或 fileSize 不是数字
     */
    Object addResource(Map<String, Object> request);

    /**
     * 更新支持资源
     *
     * <p>字段三态：请求里没这个 key → 不动；给了 {@code null} → 报错（updateById 的 NOT_NULL
     * 策略会静默跳过 null，放过去就成了"报成功却没写"）；给了值 → 写入。
     *
     * @throws com.academic.exception.ResourceNotFoundException 资源不存在
     * @throws IllegalArgumentException 资源ID非法 / 标题为空 / 某字段显式传了 null 或非数字
     */
    Object updateResource(String resourceId, Map<String, Object> request);

    /**
     * 删除支持资源（软删除：置 {@code status=0}，不删行）
     *
     * @throws com.academic.exception.ResourceNotFoundException 资源不存在
     * @throws IllegalArgumentException 资源ID为空或不是数字
     */
    Object deleteResource(String resourceId);

    /**
     * 搜索支持资源（只在在架资源里找）
     */
    Object searchResources(Map<String, Object> request);
}
