package com.jiqu.community.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.cache.LimitedDropCacheInvalidationPublisher;
import com.jiqu.community.context.DelayQueueContext;
import com.jiqu.community.core.RedisKeyManage;
import com.jiqu.community.core.SpringUtil;
import com.jiqu.community.delay.message.DelayedDropReminderMessage;
import com.jiqu.community.dto.DelayDropReminderDto;
import com.jiqu.community.dto.Result;
import com.jiqu.community.dto.LimitedDropDto;
import com.jiqu.community.dto.UpdateLimitedDropDto;
import com.jiqu.community.dto.UpdateLimitedDropStockDto;
import com.jiqu.community.dto.DropDto;
import com.jiqu.community.dto.DropSubscribeBatchDto;
import com.jiqu.community.dto.DropSubscribeDto;
import com.jiqu.community.dto.DropCalendarVo;
import com.jiqu.community.entity.LimitedDrop;
import com.jiqu.community.entity.Drop;
import com.jiqu.community.enums.BaseCode;
import com.jiqu.community.enums.StockUpdateType;
import com.jiqu.community.enums.SubscribeStatus;
import com.jiqu.community.exception.JiquFrameException;
import com.jiqu.community.handler.BloomFilterHandlerFactory;
import com.jiqu.community.mapper.DropMapper;
import com.jiqu.community.redis.RedisCache;
import com.jiqu.community.redis.RedisKeyBuild;
import com.jiqu.community.service.ILimitedDropService;
import com.jiqu.community.service.IDropOrderService;
import com.jiqu.community.service.IDropService;
import com.jiqu.community.servicelock.LockType;
import com.jiqu.community.servicelock.annotion.ServiceLock;
import com.jiqu.community.toolkit.SnowflakeIdGenerator;
import com.jiqu.community.utils.UserHolder;
import com.jiqu.community.vo.GetSubscribeStatusVo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.jiqu.community.constant.Constant.BLOOM_FILTER_HANDLER_DROP;
import static com.jiqu.community.constant.Constant.DELAY_DROP_REMINDER;
import static com.jiqu.community.constant.DistributedLockConstants.UPDATE_LIMITED_DROP_LOCK;
import static com.jiqu.community.constant.DistributedLockConstants.UPDATE_LIMITED_DROP_STOCK_LOCK;
import static com.jiqu.community.service.impl.DropOrderServiceImpl.DROP_ORDER_EXECUTOR;
import static com.jiqu.community.utils.RedisConstants.DROP_STOCK_KEY;


@Slf4j
@Service
public class DropServiceImpl extends ServiceImpl<DropMapper, Drop> implements IDropService {

    @Resource
    private ILimitedDropService limitedDropService;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;
    
    @Resource
    private BloomFilterHandlerFactory bloomFilterHandlerFactory;
    
    @Resource
    private RedisCache redisCache;
    
    @Resource
    private LimitedDropCacheInvalidationPublisher limitedDropCacheInvalidationPublisher;
    
    @Resource
    private IDropOrderService dropOrderService;
    
    @Resource
    private DelayQueueContext delayQueueContext;

    @Value("${drop.reminder.ahead.seconds:120}")
    private long reminderAheadSeconds;
    
    @Override
    public Long addDrop(DropDto dropDto) {
        Drop one = lambdaQuery().orderByDesc(Drop::getId).one();
        long newId = 1L;
        if (one != null) {
            newId = one.getId() + 1;
        }
        Drop drop = new Drop();
        BeanUtil.copyProperties(dropDto, drop);
        drop.setId(newId);
        save(drop);
        bloomFilterHandlerFactory.get(BLOOM_FILTER_HANDLER_DROP).add(drop.getId().toString());
        return drop.getId();
    }
    
    @Override
    public Result<List<Drop>> queryDropOfStore(Long storeId) {
        // 查询优惠券信息
        List<Drop> drops = getBaseMapper().queryDropOfStore(storeId);
        // 返回结果
        return Result.ok(drops);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addLimitedDrop(LimitedDropDto limitedDropDto) {
        //return doAddLimitedDropV1(limitedDropDto);
        return doAddLimitedDropV2(limitedDropDto);
    }
    
    @Override
    @ServiceLock(lockType= LockType.Write,name = UPDATE_LIMITED_DROP_LOCK,keys = {"#updateLimitedDropDto.dropId"})
    @Transactional(rollbackFor = Exception.class)
    public void updateLimitedDrop(UpdateLimitedDropDto updateLimitedDropDto) {
        Long dropId = updateLimitedDropDto.getDropId();
        boolean updatedDrop = false;
        var dropUpdate = this.lambdaUpdate().eq(Drop::getId, dropId);
        if (updateLimitedDropDto.getTitle() != null) {
            dropUpdate.set(Drop::getTitle, updateLimitedDropDto.getTitle());
            updatedDrop = true;
        }
        if (updateLimitedDropDto.getSubTitle() != null) {
            dropUpdate.set(Drop::getSubTitle, updateLimitedDropDto.getSubTitle());
            updatedDrop = true;
        }
        if (updateLimitedDropDto.getRules() != null) {
            dropUpdate.set(Drop::getRules, updateLimitedDropDto.getRules());
            updatedDrop = true;
        }
        if (updateLimitedDropDto.getPayValue() != null) {
            dropUpdate.set(Drop::getPayValue, updateLimitedDropDto.getPayValue());
            updatedDrop = true;
        }
        if (updateLimitedDropDto.getActualValue() != null) {
            dropUpdate.set(Drop::getActualValue, updateLimitedDropDto.getActualValue());
            updatedDrop = true;
        }
        if (updateLimitedDropDto.getType() != null) {
            dropUpdate.set(Drop::getType, updateLimitedDropDto.getType());
            updatedDrop = true;
        }
        if (updateLimitedDropDto.getStatus() != null) {
            dropUpdate.set(Drop::getStatus, updateLimitedDropDto.getStatus());
            updatedDrop = true;
        }
        if (updatedDrop) {
            dropUpdate.set(Drop::getUpdateTime, LocalDateTimeUtil.now()).update();
        }
        
        boolean updatedLimitedDrop = false;
        var limitedDropUpdate = limitedDropService.lambdaUpdate().eq(LimitedDrop::getDropId, dropId);
        if (updateLimitedDropDto.getBeginTime() != null) {
            limitedDropUpdate.set(LimitedDrop::getBeginTime, updateLimitedDropDto.getBeginTime());
            updatedLimitedDrop = true;
        }
        if (updateLimitedDropDto.getEndTime() != null) {
            limitedDropUpdate.set(LimitedDrop::getEndTime, updateLimitedDropDto.getEndTime());
            updatedLimitedDrop = true;
        }
        if (updatedLimitedDrop) {
            limitedDropUpdate.set(LimitedDrop::getUpdateTime, LocalDateTimeUtil.now()).update();
        }
        if (updatedDrop || updatedLimitedDrop) {
            dropUpdate.update();
            limitedDropUpdate.update();
            limitedDropCacheInvalidationPublisher.publishInvalidate(dropId, "update");
        }
    }
    
    @Override
    @ServiceLock(lockType= LockType.Write,name = UPDATE_LIMITED_DROP_STOCK_LOCK,keys = {"#updateLimitedDropDto.dropId"})
    @Transactional(rollbackFor = Exception.class)
    public void updateLimitedDropStock(UpdateLimitedDropStockDto updateLimitedDropDto) {
        LimitedDrop limitedDrop = limitedDropService.lambdaQuery()
                .eq(LimitedDrop::getDropId, updateLimitedDropDto.getDropId()).one();
        if (Objects.isNull(limitedDrop)) {
            throw new JiquFrameException(BaseCode.LIMITED_DROP_NOT_EXIST);
        }
        Integer oldStock = limitedDrop.getStock();
        Integer oldInitStock = limitedDrop.getInitStock();
        Integer newInitStock = updateLimitedDropDto.getInitStock();
        int changeStock = newInitStock - oldInitStock;
        if (changeStock == 0) {
            return;
        }
        int newStock = oldStock + changeStock;
        if (newStock < 0 ) {
            throw new JiquFrameException(BaseCode.AFTER_LIMITED_DROP_REMAIN_STOCK_NOT_NEGATIVE_NUMBER);
        }
        StockUpdateType stockUpdateType = StockUpdateType.INCREASE;
        if (changeStock < 0) {
            stockUpdateType = StockUpdateType.DECREASE;
        }
        limitedDropService.lambdaUpdate()
                .set(LimitedDrop::getStock, newStock)
                .set(LimitedDrop::getInitStock, newInitStock)
                .set(LimitedDrop::getUpdateTime, LocalDateTimeUtil.now())
                .eq(LimitedDrop::getDropId, limitedDrop.getDropId())
                .update();
        String oldRedisStockStr = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_STOCK_TAG_KEY, 
                updateLimitedDropDto.getDropId()), String.class);
        Integer newRedisStock = null;
        if (StrUtil.isBlank(oldRedisStockStr)) {
            redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_STOCK_TAG_KEY,
                    updateLimitedDropDto.getDropId()),String.valueOf(newInitStock));
        }else {
            int oldRedisStock = Integer.parseInt(oldRedisStockStr);
            newRedisStock = oldRedisStock + changeStock;
            if (newRedisStock < 0 ) {
                throw new JiquFrameException(BaseCode.AFTER_LIMITED_DROP_REMAIN_STOCK_NOT_NEGATIVE_NUMBER);
            }
            redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_STOCK_TAG_KEY,
                    updateLimitedDropDto.getDropId()),String.valueOf(newRedisStock));
        }
        log.info("修改库存成功！修改库存类型：{},修改前：数据库初始库存：{},redis旧库存：{},修改后：数据库初始库存：{},redis新库存：{}",
                stockUpdateType.getMsg(),
                oldInitStock,
                StrUtil.isBlank(oldRedisStockStr) ? null : oldRedisStockStr,
                newInitStock,
                newRedisStock
                );
        //如果是增加库存,尝试将资格自动分配给订阅队列中最早的未购用户
        if (stockUpdateType == StockUpdateType.INCREASE) {
            DROP_ORDER_EXECUTOR.execute(() -> dropOrderService
                    .autoIssueDropToEarliestSubscriber(limitedDrop.getDropId(),null));
        }
    }
    
    @Override
    public void subscribe(final DropSubscribeDto dropSubscribeDto) {
        Long dropId = dropSubscribeDto.getDropId();
        Long userId = UserHolder.getUser().getId();
        String userIdStr = String.valueOf(userId);
        
        //计算统一 TTL（过期秒数）
        Long ttlSeconds = redisCache.getExpire(
                RedisKeyBuild.createRedisKey(RedisKeyManage.LIMITED_DROP_TAG_KEY, dropId),
                TimeUnit.SECONDS
        );
        if (Objects.isNull(ttlSeconds) || ttlSeconds <= 0) {
            LimitedDrop sv = limitedDropService.lambdaQuery()
                    .eq(LimitedDrop::getDropId, dropId)
                    .one();
            if (Objects.nonNull(sv) && Objects.nonNull(sv.getEndTime())) {
                ttlSeconds = Math.max(
                        LocalDateTimeUtil.between(LocalDateTimeUtil.now(), sv.getEndTime()).getSeconds(),
                        1L
                );
            } else {
                ttlSeconds = 3600L;
            }
        }
        //检查是否已购买，判断用户是否在 DROP_USER_TAG_KEY:{dropId} 集合中（已购集合）
        boolean purchased = Boolean.TRUE.equals(redisCache.isMemberForSet(
                RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_USER_TAG_KEY, dropId),
                userIdStr
        ));
        
        
        RedisKeyBuild statusKey = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_SUBSCRIBE_STATUS_TAG_KEY, dropId);
        if (purchased) {
            redisCache.putHash(statusKey, userIdStr, SubscribeStatus.SUCCESS.getCode(), ttlSeconds, TimeUnit.SECONDS);
            redisCache.expire(statusKey, ttlSeconds, TimeUnit.SECONDS);
            return;
        }
        
        // 加入订阅集合（SET），幂等
        RedisKeyBuild setKey = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_SUBSCRIBE_USER_TAG_KEY, dropId);
        Long added = redisCache.addForSet(setKey, userIdStr);
        redisCache.expire(setKey, ttlSeconds, TimeUnit.SECONDS);
        
        // 加入订阅队列（ZSET），仅首次加入时写入顺序分数
        RedisKeyBuild zsetKey = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_SUBSCRIBE_ZSET_TAG_KEY, dropId);
        if (Objects.nonNull(added) && added > 0) {
            redisCache.addForSortedSet(zsetKey, userIdStr, (double) System.currentTimeMillis(), ttlSeconds, TimeUnit.SECONDS);
        } else {
            // 已存在则仅对齐TTL
            redisCache.expire(zsetKey, ttlSeconds, TimeUnit.SECONDS);
        }
        
        // 更新订阅状态为 SUBSCRIBED（如已是 SUCCESS 则不降级）
        Integer prev = redisCache.getForHash(statusKey, userIdStr, Integer.class);
        if (!SubscribeStatus.SUCCESS.getCode().equals(prev)) {
            redisCache.putHash(statusKey, userIdStr, SubscribeStatus.SUBSCRIBED.getCode(), ttlSeconds, TimeUnit.SECONDS);
        }
        redisCache.expire(statusKey, ttlSeconds, TimeUnit.SECONDS);
    }
    
    @Override
    public void unsubscribe(final DropSubscribeDto dropSubscribeDto) {
        Long dropId = dropSubscribeDto.getDropId();
        Long userId = UserHolder.getUser().getId();
        String userIdStr = String.valueOf(userId);

        RedisKeyBuild setKey = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_SUBSCRIBE_USER_TAG_KEY, dropId);
        RedisKeyBuild zsetKey = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_SUBSCRIBE_ZSET_TAG_KEY, dropId);
        RedisKeyBuild statusKey = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_SUBSCRIBE_STATUS_TAG_KEY, dropId);
        
        // 从订阅集合与队列移除
        redisCache.removeForSet(setKey, userIdStr);
        redisCache.delForSortedSet(zsetKey, userIdStr);
        
        // 已购则维持 SUCCESS，否则置为 UNSUBSCRIBED
        boolean purchased = Boolean.TRUE.equals(redisCache.isMemberForSet(
                RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_USER_TAG_KEY, dropId),
                userIdStr
        ));
        Long ttlSeconds = redisCache.getExpire(
                RedisKeyBuild.createRedisKey(RedisKeyManage.LIMITED_DROP_TAG_KEY, dropId),
                TimeUnit.SECONDS
        );
        if (ttlSeconds == null || ttlSeconds <= 0) {
            ttlSeconds = 3600L;
        }
        redisCache.putHash(
                statusKey, 
                userIdStr,
                purchased ? SubscribeStatus.SUCCESS.getCode() : SubscribeStatus.UNSUBSCRIBED.getCode(),
                ttlSeconds, TimeUnit.SECONDS);
        redisCache.expire(statusKey, ttlSeconds, TimeUnit.SECONDS);
    }
    
    @Override
    public Integer getSubscribeStatus(final DropSubscribeDto dropSubscribeDto) {
        Long dropId = dropSubscribeDto.getDropId();
        Long userId = UserHolder.getUser().getId();
        String userIdStr = String.valueOf(userId);

        RedisKeyBuild statusKey = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_SUBSCRIBE_STATUS_TAG_KEY, dropId);
        Integer st = redisCache.getForHash(statusKey, userIdStr, Integer.class);
        if (st != null) {
            return st;
        }
        
        boolean purchased = Boolean.TRUE.equals(redisCache.isMemberForSet(
                RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_USER_TAG_KEY, dropId),
                userIdStr
        ));
        if (purchased) {
            Long ttlSeconds = redisCache.getExpire(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.LIMITED_DROP_TAG_KEY, dropId),
                    TimeUnit.SECONDS
            );
            if (ttlSeconds == null || ttlSeconds <= 0) {
                ttlSeconds = 3600L;
            }
            redisCache.putHash(statusKey, userIdStr, SubscribeStatus.SUCCESS.getCode(), ttlSeconds, TimeUnit.SECONDS);
            redisCache.expire(statusKey, ttlSeconds, TimeUnit.SECONDS);
            return SubscribeStatus.SUCCESS.getCode();
        }
        
        boolean inQueue = Boolean.TRUE.equals(redisCache.isMemberForSet(
                RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_SUBSCRIBE_USER_TAG_KEY, dropId),
                userIdStr
        ));
        return inQueue ? SubscribeStatus.SUBSCRIBED.getCode() : SubscribeStatus.UNSUBSCRIBED.getCode();
    }
    
    @Override
    public List<GetSubscribeStatusVo> getSubscribeStatusBatch(final DropSubscribeBatchDto dropSubscribeBatchDto) {
        Long userId = UserHolder.getUser().getId();
        String userIdStr = String.valueOf(userId);
        List<GetSubscribeStatusVo> res = new ArrayList<>();
        for (Long dropId : dropSubscribeBatchDto.getDropIdList()) {
            // 优先使用HASH缓存
            RedisKeyBuild statusKey = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_SUBSCRIBE_STATUS_TAG_KEY, dropId);
            Integer st = redisCache.getForHash(statusKey, userIdStr, Integer.class);
            if (st != null) {
                res.add(new GetSubscribeStatusVo(dropId, st));
                continue;
            }
            boolean purchased = Boolean.TRUE.equals(redisCache.isMemberForSet(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_USER_TAG_KEY, dropId),
                    userIdStr
            ));
            if (purchased) {
                Long ttlSeconds = redisCache.getExpire(
                        RedisKeyBuild.createRedisKey(RedisKeyManage.LIMITED_DROP_TAG_KEY, dropId),
                        TimeUnit.SECONDS
                );
                if (ttlSeconds == null || ttlSeconds <= 0) {
                    ttlSeconds = 3600L;
                }
                redisCache.putHash(statusKey, userIdStr, SubscribeStatus.SUCCESS.getCode(), ttlSeconds, TimeUnit.SECONDS);
                redisCache.expire(statusKey, ttlSeconds, TimeUnit.SECONDS);
                res.add(new GetSubscribeStatusVo(dropId, SubscribeStatus.SUCCESS.getCode()));
                continue;
            }
            boolean inQueue = Boolean.TRUE.equals(redisCache.isMemberForSet(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_SUBSCRIBE_USER_TAG_KEY, dropId),
                    userIdStr
            ));
            res.add(new GetSubscribeStatusVo(dropId, inQueue ? SubscribeStatus.SUBSCRIBED.getCode() : SubscribeStatus.UNSUBSCRIBED.getCode()));
        }
        return res;
    }
    
    public Long doAddLimitedDropV1(LimitedDropDto limitedDropDto) {
        DropDto dropDto = new DropDto();
        BeanUtil.copyProperties(limitedDropDto, dropDto);
        Long dropId = addDrop(dropDto);
        LimitedDrop limitedDrop = new LimitedDrop();
        limitedDrop.setId(snowflakeIdGenerator.nextId());
        limitedDrop.setDropId(dropId);
        limitedDrop.setStock(limitedDropDto.getStock());
        limitedDrop.setBeginTime(limitedDropDto.getBeginTime());
        limitedDrop.setEndTime(limitedDropDto.getEndTime());
        limitedDropService.save(limitedDrop);
        stringRedisTemplate.opsForValue().set(DROP_STOCK_KEY + dropId, limitedDrop.getStock().toString());
        long ttlSeconds = Math.max(
                LocalDateTimeUtil.between(LocalDateTimeUtil.now(), limitedDrop.getEndTime()).getSeconds(),
                1L
        );
        limitedDrop.setStock(null);
        redisCache.set(
                RedisKeyBuild.createRedisKey(RedisKeyManage.LIMITED_DROP_TAG_KEY, dropId),
                limitedDrop,
                ttlSeconds,
                TimeUnit.SECONDS
        );
        return dropId;
    }
    
    public Long doAddLimitedDropV2(LimitedDropDto limitedDropDto) {
        DropDto dropDto = new DropDto();
        BeanUtil.copyProperties(limitedDropDto, dropDto);
        Long dropId = addDrop(dropDto);
        LimitedDrop limitedDrop = new LimitedDrop();
        limitedDrop.setId(snowflakeIdGenerator.nextId());
        limitedDrop.setDropId(dropId);
        limitedDrop.setInitStock(limitedDropDto.getStock());
        limitedDrop.setStock(limitedDropDto.getStock());
        limitedDrop.setBeginTime(limitedDropDto.getBeginTime());
        limitedDrop.setEndTime(limitedDropDto.getEndTime());
        limitedDropService.save(limitedDrop);
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
        limitedDrop.setStock(null);
        redisCache.set(
                RedisKeyBuild.createRedisKey(RedisKeyManage.LIMITED_DROP_TAG_KEY, dropId),
                limitedDrop,
                ttlSeconds,
                TimeUnit.SECONDS
        );
        sendDelayedDropReminder(limitedDrop);
        return dropId;
    }
    
    public void sendDelayedDropReminder(LimitedDrop limitedDrop){
        LocalDateTime beginTime = limitedDrop.getBeginTime();
        if (beginTime == null) {
            log.warn("[DELAY_REMINDER] beginTime为空，跳过调度 dropId={}", limitedDrop.getDropId());
            return;
        }
        long secondsUntilBegin = Math.max(
                LocalDateTimeUtil.between(LocalDateTimeUtil.now(), beginTime).getSeconds(),
                0L
        );
        long delaySeconds = secondsUntilBegin - Math.max(reminderAheadSeconds, 0L);
        if (delaySeconds <= 0) {
            log.info("[DELAY_REMINDER] beginTime过近或已开始，不进行延迟调度 dropId={} beginTime={} delaySeconds={}",
                    limitedDrop.getDropId(), beginTime, delaySeconds);
            return;
        }
        
        DelayedDropReminderMessage msg = new DelayedDropReminderMessage(
                limitedDrop.getDropId(),
                beginTime
        );
        String content = JSON.toJSONString(msg);

        String topic = SpringUtil.getPrefixDistinctionName() + "-" + DELAY_DROP_REMINDER;
        delayQueueContext.sendMessage(topic, content, delaySeconds, TimeUnit.SECONDS);
        log.info("[DELAY_REMINDER] 已调度提醒消息 dropId={} delaySeconds={} topic={}", limitedDrop.getDropId(), delaySeconds, topic);
    }
    
    @Override
    public void delayDropReminder(DelayDropReminderDto delayDropReminderDto) {
        LimitedDrop limitedDrop = limitedDropService.lambdaQuery().eq(LimitedDrop::getDropId, 
                delayDropReminderDto.getDropId()).one();
        if (Objects.isNull(limitedDrop)) {
            throw new JiquFrameException(BaseCode.LIMITED_DROP_NOT_EXIST);
        }
        DelayedDropReminderMessage msg = new DelayedDropReminderMessage(
                limitedDrop.getDropId(),
                limitedDrop.getBeginTime()
        );
        String content = JSON.toJSONString(msg);
        String topic = SpringUtil.getPrefixDistinctionName() + "-" + DELAY_DROP_REMINDER;
        Integer delaySeconds = delayDropReminderDto.getDelaySeconds();
        delayQueueContext.sendMessage(topic, content, delayDropReminderDto.getDelaySeconds(), TimeUnit.SECONDS);
        log.info("[测试延迟发送] 已调度提醒消息 dropId={} delaySeconds={} topic={}", limitedDrop.getDropId(), delaySeconds, topic);
    }

    @Override
    public List<DropCalendarVo> calendar() {
        List<LimitedDrop> limitedDrops = limitedDropService.lambdaQuery()
                .gt(LimitedDrop::getEndTime, LocalDateTime.now())
                .orderByAsc(LimitedDrop::getBeginTime)
                .last("LIMIT 50")
                .list();
        if (limitedDrops == null || limitedDrops.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> dropIds = limitedDrops.stream()
                .map(LimitedDrop::getDropId).distinct().collect(Collectors.toList());
        Map<Long, Drop> dropMap = listByIds(dropIds).stream()
                .collect(Collectors.toMap(Drop::getId, Function.identity(), (a, b) -> a));
        LocalDateTime now = LocalDateTime.now();
        return limitedDrops.stream().map(limitedDrop -> {
            DropCalendarVo vo = new DropCalendarVo();
            Drop drop = dropMap.get(limitedDrop.getDropId());
            vo.setDropId(limitedDrop.getDropId());
            vo.setTitle(drop == null ? "" : drop.getTitle());
            vo.setSubTitle(drop == null ? "" : drop.getSubTitle());
            vo.setBeginTime(limitedDrop.getBeginTime());
            vo.setEndTime(limitedDrop.getEndTime());
            vo.setInitStock(limitedDrop.getInitStock());
            vo.setStock(limitedDrop.getStock());
            vo.setStatus(limitedDrop.getBeginTime() != null
                    && limitedDrop.getBeginTime().isAfter(now) ? "upcoming" : "ongoing");
            return vo;
        }).collect(Collectors.toList());
    }
}
