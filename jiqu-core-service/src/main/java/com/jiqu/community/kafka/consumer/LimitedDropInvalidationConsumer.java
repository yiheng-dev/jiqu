package com.jiqu.community.kafka.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.cache.LimitedDropLocalCache;
import com.jiqu.community.consumer.AbstractConsumerHandler;
import com.jiqu.community.core.RedisKeyManage;
import com.jiqu.community.kafka.message.LimitedDropInvalidationMessage;
import com.jiqu.community.message.MessageExtend;
import com.jiqu.community.redis.RedisCache;
import com.jiqu.community.redis.RedisKeyBuild;
import com.jiqu.community.servicelock.LockType;
import com.jiqu.community.servicelock.annotion.ServiceLock;
import org.springframework.aop.framework.AopContext;
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
import static com.jiqu.community.constant.DistributedLockConstants.UPDATE_LIMITED_DROP_LOCK;


@Slf4j
@Component
public class LimitedDropInvalidationConsumer extends AbstractConsumerHandler<LimitedDropInvalidationMessage> {


    @Resource
    private LimitedDropLocalCache limitedDropLocalCache;
    
    @Resource
    private MeterRegistry meterRegistry;


    @Resource
    private RedisCache redisCache;

    public LimitedDropInvalidationConsumer() {
        super(LimitedDropInvalidationMessage.class);
    }
    
    @KafkaListener(
            topics = {SPRING_INJECT_PREFIX_DISTINCTION_NAME + "-" + LIMITED_DROP_CACHE_INVALIDATION_TOPIC},
            groupId = "${prefix.distinction.name:jiqu}-limited_drop_cache_invalidation-${random.uuid}"
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
            log.warn("收到缓存失效消息但载荷为空或dropId缺失, uuid={}", message.getUuid());
            return;
        }
        Long dropId = body.getDropId();
        
        ((LimitedDropInvalidationConsumer) AopContext.currentProxy()).delCache(dropId);
    }
    
    @ServiceLock(lockType= LockType.Write,name = UPDATE_LIMITED_DROP_LOCK,keys = {"#dropId"})
    public void delCache(Long dropId){
        RedisKeyBuild limitedDropRedisKey =
                RedisKeyBuild.createRedisKey(RedisKeyManage.LIMITED_DROP_TAG_KEY, dropId);
        limitedDropLocalCache.invalidate(limitedDropRedisKey.getRelKey());
        
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.LIMITED_DROP_TAG_KEY, dropId));
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_STOCK_TAG_KEY, dropId));
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.LIMITED_DROP_NULL_TAG_KEY, dropId));
        
    }
    
    @Override
    protected void afterConsumeFailure(final MessageExtend<LimitedDropInvalidationMessage> message, final Throwable throwable) {
        super.afterConsumeFailure(message, throwable);
        log.warn("删除Redis缓存失败 dropId={}", message.getMessageBody().getDropId(), throwable);
        safeInc(errorTag(throwable));
    }
    
    private void safeInc(String tagValue) {
        try {
            if (meterRegistry != null) {
                meterRegistry.counter("drop_invalidation_consume_failures", "error", tagValue).increment();
            }
        } catch (Exception ignore) {
        }
    }

    private String errorTag(Throwable t) {
        return t == null ? "unknown" : t.getClass().getSimpleName();
    }
}
