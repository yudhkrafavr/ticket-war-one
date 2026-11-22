package com.ticket_war.war.config;

import com.ticket_war.war.dto.response.AdminBatchLeaderboardDetailResponse;
import com.ticket_war.war.dto.response.AdminLeaderboardResponse;
import com.ticket_war.war.dto.response.WaitingRoomResponse;
import com.ticket_war.war.dto.response.WarBatchResponse;
import com.ticket_war.war.dto.response.WarSessionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableCaching
public class WarCacheConfiguration implements CachingConfigurer {
    private static final Logger logger = LoggerFactory.getLogger(WarCacheConfiguration.class);

    private final RedisConnectionFactory connectionFactory;
    private final JsonMapper jsonMapper;

    public WarCacheConfiguration(RedisConnectionFactory connectionFactory, JsonMapper jsonMapper) {
        this.connectionFactory = connectionFactory;
        this.jsonMapper = jsonMapper;
    }

    private static final Duration SESSION_CACHE_TTL = Duration.ofMinutes(2);
    private static final Duration BATCH_CACHE_TTL = Duration.ofMinutes(2);
    private static final Duration WAITING_ROOM_CACHE_TTL = Duration.ofSeconds(1);
    private static final Duration LEADERBOARD_CACHE_TTL = Duration.ofMinutes(2);

    @Bean
    @Override
    public RedisCacheManager cacheManager() {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withCacheConfiguration("warSessionLists", jsonCache(
                        jsonMapper, listType(jsonMapper, WarSessionResponse.class), SESSION_CACHE_TTL))
                .withCacheConfiguration("warSessionDetails", jsonCache(
                        jsonMapper, WarSessionResponse.class, SESSION_CACHE_TTL))
                .withCacheConfiguration("warBatchLists", jsonCache(
                        jsonMapper, listType(jsonMapper, WarBatchResponse.class), BATCH_CACHE_TTL))
                .withCacheConfiguration("warBatchDetails", jsonCache(
                        jsonMapper, WarBatchResponse.class, BATCH_CACHE_TTL))
                .withCacheConfiguration("participantWaitingRooms", jsonCache(
                        jsonMapper, WaitingRoomResponse.class, WAITING_ROOM_CACHE_TTL))
                .withCacheConfiguration("adminLeaderboards", jsonCache(
                        jsonMapper, AdminLeaderboardResponse.class, LEADERBOARD_CACHE_TTL))
                .withCacheConfiguration("adminBatchLeaderboards", jsonCache(
                        jsonMapper, AdminBatchLeaderboardDetailResponse.class, LEADERBOARD_CACHE_TTL))
                .build();
    }

    @Override
    public CacheResolver cacheResolver() {
        return null;
    }

    @Override
    public KeyGenerator keyGenerator() {
        return null;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                logCacheFailure("read", cache, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                logCacheFailure("write", cache, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                logCacheFailure("eviction", cache, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                logCacheFailure("clear", cache, exception);
            }
        };
    }

    private static void logCacheFailure(String operation, Cache cache, RuntimeException exception) {
        logger.warn("Redis cache {} failed for '{}' ({}); continuing without the cache.",
                operation, cache.getName(), exception.getClass().getSimpleName());
    }

    private static JavaType listType(JsonMapper jsonMapper, Class<?> elementType) {
        return jsonMapper.getTypeFactory().constructCollectionType(List.class, elementType);
    }

    private static <T> RedisCacheConfiguration jsonCache(
            JsonMapper jsonMapper,
            Class<T> type,
            Duration ttl) {
        return jsonCache(jsonMapper, jsonMapper.getTypeFactory().constructType(type), ttl);
    }

    private static RedisCacheConfiguration jsonCache(
            JsonMapper jsonMapper,
            JavaType type,
            Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(jsonMapper, type)));
    }
}
