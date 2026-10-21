package com.jiqu.community.kafka.producer;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.AbstractProducerHandler;
import com.jiqu.community.enums.LimitedDropOrderOperate;
import com.jiqu.community.kafka.message.LimitedDropMessage;
import com.jiqu.community.kafka.redis.RedisDropData;
import com.jiqu.community.message.MessageExtend;
import com.jiqu.community.toolkit.SnowflakeIdGenerator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class LimitedDropProducer extends AbstractProducerHandler<MessageExtend<LimitedDropMessage>> {
    
    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;
    
    
    @Resource
    private RedisDropData redisDropData;
    
    public LimitedDropProducer(final KafkaTemplate<String,MessageExtend<LimitedDropMessage>> kafkaTemplate) {
        super(kafkaTemplate);
    }
    
    @Override
    protected void afterSendFailure(final String topic, final MessageExtend<LimitedDropMessage> message, final Throwable throwable) {
        super.afterSendFailure(topic, message, throwable);
        long traceId = snowflakeIdGenerator.nextId();
        redisDropData.rollbackRedisDropData(
                LimitedDropOrderOperate.YES,
                traceId,
                message.getMessageBody().getDropId(),
                message.getMessageBody().getUserId(),
                message.getMessageBody().getOrderId(),
                message.getMessageBody().getAfterQty(),
                message.getMessageBody().getChangeQty(),
                message.getMessageBody().getBeforeQty());
    }
}
