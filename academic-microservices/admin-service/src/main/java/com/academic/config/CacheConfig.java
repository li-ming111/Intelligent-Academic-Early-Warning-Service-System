package com.academic.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * 缓存配置类
 *
 * ⚠️ 两条约束，都跟"缓存的失败不该改变业务的结论"有关，改之前先读：
 *
 * 1. **不要加 `.transactionAware()`。** 它把 `@CacheEvict` 推迟到事务提交**之后**执行
 *    （`TransactionAwareCacheDecorator.afterCommit`）。而 `afterCommit` 是在
 *    `AbstractPlatformTransactionManager.processCommit` 里同步调用的，没有 try/catch：
 *    Redis 不在时 `RedisCache.clear` 抛出的 `RedisConnectionFailureException` 会从
 *    `commit()` 里一路穿出来 —— **此时数据已经提交，回滚不了**，却让
 *    `GlobalExceptionHandler` 把它当成 `DataAccessException`，回给管理员一句
 *    HTTP 500「数据库操作失败」。2026-09-30 实测：Redis 停着建账号，`users` 35→36
 *    且 `student_profile` 也建好了，管理员看到的却是失败。顺带一提，异常从
 *    `processCommit` 穿出还会跳过 `cleanupAfterCompletion`，连接也回不了池。
 *    去掉之后 evict 立刻执行，走正常切面路径 —— 见下一条，那条路径是有保护的。
 *
 * 2. **`errorHandler()` 不能省。** 它才是让"Redis 挂了"退化成"没有缓存"的那道闸。
 *    不加就是 Spring 默认的 `SimpleCacheErrorHandler`，它把异常原样抛回调用方 ——
 *    于是缓存后端一挂，所有碰缓存的读接口一起 500。
 *
 * 顺带记一笔现状：本服务的 `@Cacheable` 方法（`UserServiceImpl.getByUsername/
 * getUsersByRole/searchUsers/getRecentUsers`、`CollegeServiceImpl.getAllColleges/
 * getCollegeById`）**调用点数为 0**，而且 `getByUsername` 唯一的调用者是同类的
 * `validateLogin`/`createUser` —— 自调用不走代理，注解本来也不生效。其他服务
 * 也不碰 `users`/`colleges` 这两个 cache 名。也就是说这层缓存目前只写不读、
 * 读的那半是死的，真正跑起来的只有 `@CacheEvict`。这不是本次要修的东西，
 * 但别把它当成"项目在用 Redis 缓存"的证据。
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                warn("读", cache, key, e);
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                warn("写", cache, key, e);
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                warn("清除", cache, key, e);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                warn("清空", cache, null, e);
            }

            private void warn(String op, Cache cache, Object key, RuntimeException e) {
                log.warn("缓存{}失败，按未命中处理（不影响业务）：cache={}, key={}, cause={}",
                        op, cache.getName(), key, e.getMessage());
                log.debug("缓存{}失败详情", op, e);
            }
        };
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30)) // 默认缓存过期时间 30 分钟
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues(); // 禁止缓存 null 值

        RedisCacheConfiguration userCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) // 用户缓存 10 分钟
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        RedisCacheConfiguration collegeCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1)) // 学院缓存 1 小时
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        RedisCacheConfiguration warningCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5)) // 预警缓存 5 分钟
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // 这里原先有 .transactionAware()，2026-09-30 去掉 —— 原因见类注释第 1 条。
        // 别看着"提交后才清缓存更正确"就加回来：本服务的缓存只被 @CacheEvict 清空过、
        // 从没被写入过，那个正确性换来的是一挂 Redis 就把成功的写报成失败。
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(config)
                .withCacheConfiguration("users", userCacheConfig)
                .withCacheConfiguration("colleges", collegeCacheConfig)
                .withCacheConfiguration("warnings", warningCacheConfig)
                .withCacheConfiguration("default", config)
                .build();
    }
}
