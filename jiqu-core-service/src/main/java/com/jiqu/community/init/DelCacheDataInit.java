package com.jiqu.community.init;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.cache.LimitedDropLocalCache;
import com.jiqu.community.core.RedisKeyManage;
import com.jiqu.community.entity.LimitedDrop;
import com.jiqu.community.entity.Store;
import com.jiqu.community.redis.RedisCache;
import com.jiqu.community.redis.RedisKeyBuild;
import com.jiqu.community.service.ILimitedDropService;
import com.jiqu.community.service.IStoreService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;


@Slf4j
@Order(3)
@Component
public class DelCacheDataInit {
    
    @Resource
    private RedisCache redisCache;
    
    @Resource
    private IStoreService storeService;
    
    @Resource
    private ILimitedDropService limitedDropService;
    
    @Resource
    private LimitedDropLocalCache limitedDropLocalCache;
    
    @PostConstruct
    public void init(){
        log.info("==========删除缓存中的数据==========");
        List<Store> storeList = storeService.list();
        for (final Store store : storeList) {
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_STORE_KEY,store.getId()));
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_STORE_KEY_NULL,store.getId()));
        }
        List<LimitedDrop> limitedDropList = limitedDropService.list();
        for (final LimitedDrop limitedDrop : limitedDropList) {
            RedisKeyBuild limitedDropRedisKey =
                    RedisKeyBuild.createRedisKey(RedisKeyManage.LIMITED_DROP_TAG_KEY, limitedDrop.getDropId());
            limitedDropLocalCache.invalidate(limitedDropRedisKey.getRelKey());
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_STOCK_TAG_KEY,limitedDrop.getDropId()));
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_USER_TAG_KEY,limitedDrop.getDropId()));
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.LIMITED_DROP_TAG_KEY,limitedDrop.getDropId()));
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.LIMITED_DROP_NULL_TAG_KEY,limitedDrop.getDropId()));
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_TRACE_LOG_TAG_KEY,limitedDrop.getDropId()));
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_SUBSCRIBE_USER_TAG_KEY,limitedDrop.getDropId()));
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_SUBSCRIBE_ZSET_TAG_KEY,limitedDrop.getDropId()));
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_SUBSCRIBE_STATUS_TAG_KEY,limitedDrop.getDropId()));
        }
    }
}
