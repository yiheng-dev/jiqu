package com.jiqu.community.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.cache.LimitedDropLocalCache;
import com.jiqu.community.core.RedisKeyManage;
import com.jiqu.community.entity.LimitedDrop;
import com.jiqu.community.entity.Drop;
import com.jiqu.community.handler.BloomFilterHandlerFactory;
import com.jiqu.community.mapper.LimitedDropMapper;
import com.jiqu.community.model.LimitedDropFullModel;
import com.jiqu.community.redis.RedisCache;
import com.jiqu.community.redis.RedisKeyBuild;
import com.jiqu.community.service.ILimitedDropService;
import com.jiqu.community.service.IDropService;
import com.jiqu.community.servicelock.LockType;
import com.jiqu.community.servicelock.annotion.ServiceLock;
import com.jiqu.community.util.ServiceLockTool;
import org.redisson.api.RLock;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.jiqu.community.constant.Constant.BLOOM_FILTER_HANDLER_DROP;
import static com.jiqu.community.constant.DistributedLockConstants.UPDATE_LIMITED_DROP_LOCK;
import static com.jiqu.community.constant.DistributedLockConstants.UPDATE_LIMITED_DROP_STOCK_LOCK;
import static com.jiqu.community.utils.RedisConstants.CACHE_NULL_TTL;
import static com.jiqu.community.utils.RedisConstants.LOCK_LIMITED_DROP_KEY;
import static com.jiqu.community.utils.RedisConstants.LOCK_LIMITED_DROP_STOCK_KEY;


@Slf4j
@Service
public class LimitedDropServiceImpl extends ServiceImpl<LimitedDropMapper, LimitedDrop> implements ILimitedDropService {
    
    @Resource
    private ServiceLockTool serviceLockTool;
    
    @Resource
    private RedisCache redisCache;
    
    @Resource
    private BloomFilterHandlerFactory bloomFilterHandlerFactory;

    @Resource
    private LimitedDropLocalCache limitedDropLocalCache;
    
    @Resource
    private LimitedDropMapper limitedDropMapper;
    
    @Resource
    private IDropService dropService;
    
    @Override
    @ServiceLock(lockType= LockType.Read,name = UPDATE_LIMITED_DROP_LOCK,keys = {"#dropId"})
    public LimitedDropFullModel queryByDropId(Long dropId) {
        RedisKeyBuild limitedDropRedisKey =
                RedisKeyBuild.createRedisKey(RedisKeyManage.LIMITED_DROP_TAG_KEY, dropId);
        RedisKeyBuild limitedDropNullRedisKey =
                RedisKeyBuild.createRedisKey(RedisKeyManage.LIMITED_DROP_NULL_TAG_KEY, dropId);
        LimitedDropFullModel localCacheHit = limitedDropLocalCache.get(limitedDropRedisKey.getRelKey());
        if (Objects.nonNull(localCacheHit)) {
            return localCacheHit;
        }
        LimitedDropFullModel limitedDropFullModel =
                redisCache.get(limitedDropRedisKey, LimitedDropFullModel.class);
        if (Objects.nonNull(limitedDropFullModel)) {
            limitedDropLocalCache.put(limitedDropRedisKey.getRelKey(), limitedDropFullModel);
            return limitedDropFullModel;
        }
        log.info("查询发售优惠券 从Redis缓存没有查询到 发售优惠券的优惠券id : {}",dropId);
        if (!bloomFilterHandlerFactory.get(BLOOM_FILTER_HANDLER_DROP).contains(String.valueOf(dropId))) {
            log.info("查询发售优惠券 布隆过滤器判断不存在 发售优惠券id : {}",dropId);
            throw new RuntimeException("查询发售优惠券不存在");
        }
        Boolean existResult = redisCache.hasKey(limitedDropNullRedisKey);
        if (existResult){
            throw new RuntimeException("查询发售优惠券不存在");
        }
        RLock lock = serviceLockTool.getLock(LockType.Reentrant, LOCK_LIMITED_DROP_KEY, new String[]{String.valueOf(dropId)});
        lock.lock();
        try {
            limitedDropFullModel = redisCache.get(limitedDropRedisKey, LimitedDropFullModel.class);
            if (Objects.nonNull(limitedDropFullModel)) {
                limitedDropLocalCache.put(limitedDropRedisKey.getRelKey(), limitedDropFullModel);
                return limitedDropFullModel;
            }
            existResult = redisCache.hasKey(limitedDropNullRedisKey);
            if (existResult){
                throw new RuntimeException("查询优惠券不存在");
            }
            LimitedDrop limitedDrop = lambdaQuery().eq(LimitedDrop::getDropId,dropId).one();
            if (Objects.isNull(limitedDrop)) {
                redisCache.set(limitedDropNullRedisKey,
                        "这是一个空值",
                        CACHE_NULL_TTL,
                        TimeUnit.MINUTES);
                throw new RuntimeException("查询发售优惠券不存在");
            }
            long ttlSeconds = Math.max(
                    LocalDateTimeUtil.between(LocalDateTimeUtil.now(), limitedDrop.getEndTime()).getSeconds(),
                    1L
            );
            Drop drop = dropService.lambdaQuery().eq(Drop::getId, dropId).one();
            limitedDropFullModel = new LimitedDropFullModel();
            BeanUtils.copyProperties(limitedDrop, limitedDropFullModel);
            limitedDropFullModel.setStoreId(drop.getStoreId());
            limitedDropFullModel.setStatus(drop.getStatus());
            limitedDropFullModel.setStock(null);
            redisCache.set(
                    limitedDropRedisKey,
                    limitedDropFullModel,
                    ttlSeconds,
                    TimeUnit.SECONDS
            );
            limitedDropLocalCache.put(limitedDropRedisKey.getRelKey(), limitedDropFullModel);
            return limitedDropFullModel;
        }finally {
            lock.unlock();
        }
    }
    
    @Override
    @ServiceLock(lockType= LockType.Read,name = UPDATE_LIMITED_DROP_STOCK_LOCK,keys = {"#dropId"})
    public void loadDropStock(Long dropId){
        if (!bloomFilterHandlerFactory.get(BLOOM_FILTER_HANDLER_DROP).contains(String.valueOf(dropId))) {
            log.info("加载库存 布隆过滤器判断不存在 发售优惠券id : {}",dropId);
            throw new RuntimeException("查询发售优惠券不存在");
        }
        String stock = 
                redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_STOCK_TAG_KEY, dropId), String.class);
        if (Objects.nonNull(stock)) {
            return;
        }
        RLock lock = serviceLockTool.getLock(LockType.Reentrant, LOCK_LIMITED_DROP_STOCK_KEY, 
                new String[]{String.valueOf(dropId)});
        lock.lock();
        try {
            stock = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_STOCK_TAG_KEY, dropId), String.class);
            if (Objects.nonNull(stock)) {
                return;
            }
            LimitedDrop limitedDrop = lambdaQuery().eq(LimitedDrop::getDropId,dropId).one();
            if (Objects.nonNull(limitedDrop)) {
                long ttlSeconds = Math.max(
                        LocalDateTimeUtil.between(LocalDateTimeUtil.now(), limitedDrop.getEndTime()).getSeconds(),
                        1L
                );
                redisCache.set(
                        RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_STOCK_TAG_KEY, dropId),
                        String.valueOf(limitedDrop.getStock()),
                        ttlSeconds,
                        TimeUnit.SECONDS
                );
            }
        }finally {
            lock.unlock();
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackStock(final Long dropId) {
        return limitedDropMapper.rollbackStock(dropId) > 0;
    }
}
