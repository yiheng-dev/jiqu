package com.jiqu.community.kafka.consumer;

import com.alibaba.fastjson.JSON;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.consumer.AbstractConsumerHandler;
import com.jiqu.community.kafka.message.LimitedDropInvalidationMessage;
import com.jiqu.community.message.MessageExtend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

import static com.jiqu.community.constant.Constant.LIMITED_DROP_CACHE_INVALIDATION_TOPIC;
import static com.jiqu.community.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;


@Slf4j
@Component
public class LimitedDropInvalidationDlqConsumer extends AbstractConsumerHandler<LimitedDropInvalidationMessage> {
    
    @Resource
    private MeterRegistry meterRegistry;
    
    
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

 
    public LimitedDropInvalidationDlqConsumer() {
        super(LimitedDropInvalidationMessage.class);
    }
    
    @KafkaListener(
            topics = {SPRING_INJECT_PREFIX_DISTINCTION_NAME + "-" + LIMITED_DROP_CACHE_INVALIDATION_TOPIC + ".DLQ"},
            groupId = "${prefix.distinction.name:jiqu}-limited_drop_cache_invalidation_dlq-${random.uuid}"
    )
    public void onMessage(String value,
                          @Headers Map<String, Object> headers,
                          @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
                          Acknowledgment acknowledgment) {
        consumeRaw(value, key, headers);
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }
    
    @Override
    protected void doConsume(MessageExtend<LimitedDropInvalidationMessage> message) {
        LimitedDropInvalidationMessage body = message.getMessageBody();
        if (Objects.isNull(body.getDropId())) {
            log.warn("DLQ消息载荷为空或dropId缺失, uuid={}", message.getUuid());
            safeInc("drop_invalidation_dlq_replay_skipped", "reason", "invalid_payload");
            return;
        }
        
        safeInc("drop_invalidation_dlq", "reason", "invalid_payload");

        auditLog.error("DROP_INVALIDATION_DLQ | message={}", JSON.toJSONString(message));
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
