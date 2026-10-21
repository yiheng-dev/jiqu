package com.jiqu.community.cache;

import jakarta.annotation.Resource;
import com.jiqu.community.core.RedisKeyManage;
import com.jiqu.community.kafka.message.LimitedDropInvalidationMessage;
import com.jiqu.community.kafka.producer.LimitedDropInvalidationProducer;
import com.jiqu.community.redis.RedisCache;
import com.jiqu.community.redis.RedisKeyBuild;
import com.jiqu.community.core.SpringUtil;
import org.springframework.stereotype.Component;

import static com.jiqu.community.constant.Constant.LIMITED_DROP_CACHE_INVALIDATION_TOPIC;


@Component
public class LimitedDropCacheInvalidationPublisher {

    @Resource
    private RedisCache redisCache;
    
    @Resource
    private LimitedDropInvalidationProducer invalidationProducer;
    
    @Resource
    private LimitedDropLocalCache limitedDropLocalCache;
    
    public void publishInvalidate(Long dropId, String reason) {
        RedisKeyBuild limitedDropRedisKey =
                RedisKeyBuild.createRedisKey(RedisKeyManage.LIMITED_DROP_TAG_KEY, dropId);
        limitedDropLocalCache.invalidate(limitedDropRedisKey.getRelKey());
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.LIMITED_DROP_TAG_KEY, dropId));
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_STOCK_TAG_KEY, dropId));
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.LIMITED_DROP_NULL_TAG_KEY, dropId));
        
        LimitedDropInvalidationMessage payload = new LimitedDropInvalidationMessage(dropId, reason);
        invalidationProducer.sendPayload(
                SpringUtil.getPrefixDistinctionName() + "-" + LIMITED_DROP_CACHE_INVALIDATION_TOPIC,
                payload
        );
    }
}