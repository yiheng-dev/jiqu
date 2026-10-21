package com.jiqu.community.kafka.producer;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.AbstractProducerHandler;
import com.jiqu.community.kafka.message.LimitedDropInvalidationMessage;
import com.jiqu.community.message.MessageExtend;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.support.PropertiesLoaderSupport;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class LimitedDropInvalidationProducer extends AbstractProducerHandler<MessageExtend<LimitedDropInvalidationMessage>> {
    
    private final static String RETRY_COUNT = "retryCount";
    
    private final static String DLQ = ".DLQ";
    
    @Autowired
    private PropertiesLoaderSupport propertiesLoaderSupport;
    
    public LimitedDropInvalidationProducer(final KafkaTemplate<String, MessageExtend<LimitedDropInvalidationMessage>> kafkaTemplate) {
        super(kafkaTemplate);
    }
   
    @Resource
    private MeterRegistry meterRegistry;
    
    @Value("${drop.cache.invalidate.retry.maxAttempts:3}")
    private int retryMaxAttempts;
    
    @Value("${drop.cache.invalidate.retry.initialBackoffMillis:200}")
    private long initialBackoffMillis;
    
    @Value("${drop.cache.invalidate.retry.maxBackoffMillis:800}")
    private long maxBackoffMillis;
    
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    
    
    @Override
    protected void afterSendFailure(final String topic, final MessageExtend<LimitedDropInvalidationMessage> message, final Throwable throwable) {
        final LimitedDropInvalidationMessage body = message.getMessageBody();
        final Long dropId = body.getDropId();
        final String reason = body.getReason();
        final String errMsg = throwable == null ? "unknown" : throwable.getMessage();
        log.error("LimitedDropInvalidation send failed, topic={}, uuid={}, key={}, dropId={}, reason={}, error= {}",
                topic, message.getUuid(), message.getKey(), dropId, reason, errMsg, throwable);
        if (topic.contains(DLQ)) {
            safeInc("drop_invalidation_dlq", "reason", "send_failures");
            return;
        }else {
            safeInc("drop_invalidation_send_failures", "topic", topic);
        }
        
        
        Map<String, String> headers = message.getHeaders();
        headers = headers == null ? new HashMap<>(8) : new HashMap<>(headers);
        int retryCount = 0;
        try {
            if (headers.containsKey(RETRY_COUNT)) {
                retryCount = Integer.parseInt(headers.get(RETRY_COUNT));
            }
        } catch (Exception ignore) {
        }

        if (retryCount < retryMaxAttempts) {
            long backoff = Math.min(initialBackoffMillis * (1L << retryCount), maxBackoffMillis);
            headers.put(RETRY_COUNT, String.valueOf(retryCount + 1));
            headers.put("lastError", truncate(errMsg));
            message.setHeaders(headers);
            log.warn("Retry sending cache invalidation, topic={}, uuid={}, dropId={}, retryCount={}, backoffMs={}",
                    topic, message.getUuid(), dropId, retryCount + 1, backoff);
            safeInc("drop_invalidation_send_retries", "topic", topic);
            sleepQuietly(backoff);
            sendRecord(topic, message);
            return;
        }

        final String dlqReason = "send_invalid_cache_broadcast_failed: " + truncate(errMsg);
        try {
            sendToDlq(topic, body, dlqReason);
            log.warn("Send cache invalidation to DLQ, originalTopic={}, uuid={}, dropId={}, dlqReason={}",
                    topic, message.getUuid(), dropId, dlqReason);
            auditLog.warn("DLQ_PUBLISH|topic={}|uuid={}|key={}|dropId={}|reason={}",
                    topic, message.getUuid(), message.getKey(), dropId, dlqReason);
            safeInc("drop_invalidation_send_dlq", "topic", topic);
        } catch (Exception e) {
            log.error("Send cache invalidation to DLQ failed, originalTopic={}, uuid={}, dropId={}, error={}",
                    topic, message.getUuid(), dropId, e.getMessage(), e);
            safeInc("drop_invalidation_send_dlq_failures", "topic", topic);
        }
    }
    
    @Override
    protected void afterSendSuccess(SendResult<String, MessageExtend<LimitedDropInvalidationMessage>> result) {
        super.afterSendSuccess(result);
        String topic = result.getRecordMetadata().topic();
        MessageExtend<LimitedDropInvalidationMessage> message = result.getProducerRecord().value();
        boolean dlqReplay = message != null && message.getHeaders() != null && "1".equals(message.getHeaders().getOrDefault("dlqReplayCount", "0"));
        safeInc("drop_invalidation_send_success", "topic", topic);
        if (dlqReplay) {
            safeInc("drop_invalidation_dlq_replay_success", "topic", topic);
            auditLog.info("DLQ_REPLAY_SUCCESS|topic={}|uuid={}|key={}|dropId={}",
                    topic, message.getUuid(), message.getKey(), message.getMessageBody().getDropId());
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 256 ? s : s.substring(0, 256);
    }
    
    private void sleepQuietly(long backoffMs) {
        try {
            TimeUnit.MILLISECONDS.sleep(backoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void safeInc(String name, String tagKey, String tagValue) {
        try {
            if (meterRegistry != null) {
                meterRegistry.counter(name, tagKey, tagValue).increment();
            }
        } catch (Exception ignore) {
        }
    }
}