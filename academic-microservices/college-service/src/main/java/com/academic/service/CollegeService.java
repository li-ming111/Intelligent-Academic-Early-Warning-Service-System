package com.academic.service;

import com.academic.common.entity.College;

import java.util.List;

/**
 * 学院服务接口。
 *
 * <p>2026-10-01 第二十三轮收敛：原先这里有 9 个方法（getById / getByCode / save /
 * updateById / removeById / page / list / count(QueryWrapper) / count），**只服务
 * {@code CollegeController} 的 8 个端点**。那 7 个零调用方端点删掉之后，只剩
 * 注册页的学院下拉要用，于是只留一个「取全部」—— 其余 8 个方法连同实现一起删。
 *
 * <p>注意本接口<b>不</b>继承 MyBatis-Plus 的 {@code IService}（实现类也是逐个手写的
 * 1:1 转发），所以删方法不会动到任何框架默认实现。
 */
public interface CollegeService {
    /**
     * 取全部学院。注册页的学院下拉用，不排序、不分页。
     */
    List<College> listAll();
}
