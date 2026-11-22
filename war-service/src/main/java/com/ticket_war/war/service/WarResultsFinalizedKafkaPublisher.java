package com.ticket_war.war.service;

import com.ticket_war.war.event.WarResultsFinalizedApplicationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class WarResultsFinalizedKafkaPublisher {

    private static final Logger logger = LoggerFactory.getLogger(WarResultsFinalizedKafkaPublisher.class);
    private static final List<String> CACHES_TO_CLEAR = List.of(
            "warSessionLists",
            "warSessionDetails",
            "warBatchLists",
            "warBatchDetails",
            "participantWaitingRooms",
            "adminLeaderboards",
            "adminBatchLeaderboards");

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CacheManager cacheManager;
    private final String topic;

    public WarResultsFinalizedKafkaPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            CacheManager cacheManager,
            @Value("${spring.kafka.template.default-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.cacheManager = cacheManager;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(WarResultsFinalizedApplicationEvent event) {
        clearReadCaches();
        String sessionKey = event.message().warSessionId().toString();
        try {
            kafkaTemplate.send(topic, sessionKey, event.message())
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            logger.error("Failed to publish WAR_RESULTS_FINALIZED for session {}",
                                    sessionKey, failure);
                        }
                    });
        } catch (RuntimeException exception) {
            logger.error("Failed to publish WAR_RESULTS_FINALIZED for session {}", sessionKey, exception);
        }
    }

    private void clearReadCaches() {
        for (String cacheName : CACHES_TO_CLEAR) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
