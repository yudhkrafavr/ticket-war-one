package com.ticket_war.war.config;

import com.ticket_war.war.dto.response.AdminBatchLeaderboardDetailResponse;
import com.ticket_war.war.dto.response.AdminLeaderboardResponse;
import com.ticket_war.war.dto.response.WaitingRoomResponse;
import com.ticket_war.war.dto.response.WarBatchResponse;
import com.ticket_war.war.dto.response.WarSessionResponse;
import org.springframework.cache.annotation.EnableCaching;
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
public class WarCacheConfiguration {

    private static final Duration SESSION_CACHE_TTL = Duration.ofMinutes(2);
    private static final Duration BATCH_CACHE_TTL = Duration.ofMinutes(2);
    private static final Duration WAITING_ROOM_CACHE_TTL = Duration.ofSeconds(1);
    private static final Duration LEADERBOARD_CACHE_TTL = Duration.ofMinutes(2);

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            JsonMapper jsonMapper) {
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
