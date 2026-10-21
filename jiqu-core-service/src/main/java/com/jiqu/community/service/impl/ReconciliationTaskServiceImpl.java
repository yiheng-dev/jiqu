package com.jiqu.community.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.core.RedisKeyManage;
import com.jiqu.community.entity.LimitedDrop;
import com.jiqu.community.entity.DropOrder;
import com.jiqu.community.entity.DropReconcileLog;
import com.jiqu.community.enums.LogType;
import com.jiqu.community.enums.ReconciliationStatus;
import com.jiqu.community.model.RedisTraceLogModel;
import com.jiqu.community.redis.RedisCache;
import com.jiqu.community.redis.RedisKeyBuild;
import com.jiqu.community.service.IReconciliationTaskService;
import com.jiqu.community.service.ILimitedDropService;
import com.jiqu.community.service.IDropOrderService;
import com.jiqu.community.service.IDropReconcileLogService;
import com.jiqu.community.servicelock.LockType;
import com.jiqu.community.servicelock.annotion.ServiceLock;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.jiqu.community.constant.DistributedLockConstants.UPDATE_LIMITED_DROP_STOCK_LOCK;
import static com.jiqu.community.kafka.consumer.LimitedDropConsumer.MESSAGE_DELAY_TIME;


@Service
@Slf4j
public class ReconciliationTaskServiceImpl implements IReconciliationTaskService {
    
    @Resource
    private ILimitedDropService limitedDropService;
    
    @Resource
    private IDropOrderService dropOrderService;
    
    @Resource
    private IDropReconcileLogService dropReconcileLogService;
    
    @Resource
    private RedisCache redisCache;
    
    @Override
    public void reconciliationTaskExecute() {
        List<LimitedDrop> limitedDropList = limitedDropService.lambdaQuery().list();
        for (LimitedDrop limitedDrop : limitedDropList) {
            reconciliationTaskExecute(limitedDrop.getDropId());
        }
    }
    
    public void reconciliationTaskExecute(Long dropId){
        Map<String, RedisTraceLogModel> redisTraceLogMap = loadRedisTraceLogMap(dropId);
        redisDeductTraceWithoutDbOrder(dropId, redisTraceLogMap);
        
        List<DropOrder> dropOrderList = loadPendingOrders(dropId);
        RedisKeyBuild traceLogKey = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_TRACE_LOG_TAG_KEY, dropId);
        long ttlSeconds = resolveTraceTtlSeconds(traceLogKey, dropId);
        for (DropOrder dropOrder : dropOrderList) {
            List<DropReconcileLog> logs = loadReconcileLogs(dropOrder.getId());
            if (CollectionUtil.isEmpty(logs)) {
                ((ReconciliationTaskServiceImpl) AopContext.currentProxy())
                        .markOrderStatus(dropOrder.getId(), ReconciliationStatus.ABNORMAL);
                continue;
            }
            boolean anyMissing = backfillMissingTraceLogs(logs, redisTraceLogMap, traceLogKey, ttlSeconds);
            
            int dbLogCount = logs.size();
            boolean markConsistent = true;
            if (dbLogCount == 1 || dbLogCount == 2) {
                if (anyMissing) {
                    ((IReconciliationTaskService) AopContext.currentProxy()).delRedisStock(dropId);
                }
            } else {
                ((ReconciliationTaskServiceImpl) AopContext.currentProxy())
                        .markOrderStatus(dropOrder.getId(), ReconciliationStatus.ABNORMAL);
                markConsistent = false;
            }
            if (markConsistent) {
                ((ReconciliationTaskServiceImpl) AopContext.currentProxy())
                        .markOrderStatus(dropOrder.getId(), ReconciliationStatus.CONSISTENT);
            }
        }
    }
    
    @Override
    @ServiceLock(lockType= LockType.Write,name = UPDATE_LIMITED_DROP_STOCK_LOCK,keys = {"#dropId"})
    public void delRedisStock(Long dropId){
        RedisKeyBuild stockKey = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_STOCK_TAG_KEY, dropId);
        redisCache.del(stockKey);
    }

    private List<DropOrder> loadPendingOrders(Long dropId) {
        return dropOrderService.lambdaQuery()
                .eq(DropOrder::getDropId, dropId)
                .le(DropOrder::getCreateTime, LocalDateTimeUtil.offset(LocalDateTimeUtil.now(), 2, ChronoUnit.MINUTES))
                .eq(DropOrder::getReconciliationStatus, ReconciliationStatus.PENDING.getCode())
                .orderByAsc(DropOrder::getCreateTime)
                .list();
    }

    private List<DropReconcileLog> loadReconcileLogs(Long orderId) {
        return dropReconcileLogService.lambdaQuery()
                .eq(DropReconcileLog::getOrderId, orderId)
                .orderByAsc(DropReconcileLog::getCreateTime)
                .list();
    }

    private Map<String, RedisTraceLogModel> loadRedisTraceLogMap(Long dropId) {
        return redisCache.getAllMapForHash(
                RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_TRACE_LOG_TAG_KEY, dropId),
                RedisTraceLogModel.class
        );
    }

    private long resolveTraceTtlSeconds(RedisKeyBuild traceLogKey, Long dropId) {
        Long ttlSeconds = redisCache.getExpire(traceLogKey, TimeUnit.SECONDS);
        if (ttlSeconds != null && ttlSeconds > 0) {
            return ttlSeconds;
        }
        LimitedDrop drop = limitedDropService.lambdaQuery()
                .eq(LimitedDrop::getDropId, dropId)
                .one();
        long computedTtl = 3600L;
        if (drop != null && drop.getEndTime() != null) {
            LocalDateTime now = LocalDateTimeUtil.now();
            long secondsUntilEnd = Math.max(0L, Duration.between(now, drop.getEndTime()).getSeconds());
            computedTtl = Math.max(1L, secondsUntilEnd + Duration.ofDays(1).getSeconds());
        }
        return computedTtl;
    }
    

    private void redisDeductTraceWithoutDbOrder(Long dropId, Map<String, RedisTraceLogModel> redisTraceLogMap) {
        if (dropId == null || CollectionUtil.isEmpty(redisTraceLogMap)) {
            return;
        }
        RedisKeyBuild traceLogKey = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_TRACE_LOG_TAG_KEY, dropId);
        RedisKeyBuild dropUserKey = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_USER_TAG_KEY, dropId);
        boolean delRedisStockHasHappened = false;
        long now = System.currentTimeMillis();
        for (Entry<String, RedisTraceLogModel> redisTraceLogModelEntry : redisTraceLogMap.entrySet()) {
            String traceId = redisTraceLogModelEntry.getKey();
            RedisTraceLogModel redisTraceLogModel = redisTraceLogModelEntry.getValue();
            if (redisTraceLogModel == null) {
                continue;
            }
            if (!String.valueOf(LogType.DEDUCT.getCode()).equals(redisTraceLogModel.getLogType())) {
                continue;
            }
            String orderId = redisTraceLogModel.getOrderId();
            DropReconcileLog dropReconcileLog = 
                    dropReconcileLogService.lambdaQuery()
                            .eq(DropReconcileLog::getOrderId, Long.parseLong(orderId))
                            .eq(DropReconcileLog::getTraceId, Long.parseLong(traceId))
                            .one();
            if (Objects.isNull(dropReconcileLog)){
                Long traceTs = redisTraceLogModel.getTs();
                if (traceTs != null && now - traceTs < (MESSAGE_DELAY_TIME + 2000)) {
                    continue;
                }
                log.error("发现Redis扣减流水存在，但DB订单不存在的情况，dropId={}, orderId={}", dropId, orderId);
                if (!delRedisStockHasHappened) {
                    ((IReconciliationTaskService) AopContext.currentProxy()).delRedisStock(dropId);
                    delRedisStockHasHappened = true;
                }
                redisCache.delForHash(traceLogKey, traceId);
                redisCache.removeForSet(dropUserKey, redisTraceLogModel.getUserId());
            }
        }
    }

    private boolean backfillMissingTraceLogs(List<DropReconcileLog> logs,
                                             Map<String, RedisTraceLogModel> redisTraceLogMap,
                                             RedisKeyBuild traceLogKey,
                                             long ttlSeconds) {
        boolean anyMissing = false;
        for (DropReconcileLog log : logs) {
            String traceIdStr = String.valueOf(log.getTraceId());
            RedisTraceLogModel existed = redisTraceLogMap.get(traceIdStr);
            if (existed != null) {
                continue;
            }
            anyMissing = true;
            RedisTraceLogModel model = new RedisTraceLogModel();
            model.setLogType(String.valueOf(log.getLogType()));
            model.setTs(LocalDateTimeUtil.toEpochMilli(log.getCreateTime()));
            model.setOrderId(String.valueOf(log.getOrderId()));
            model.setTraceId(traceIdStr);
            model.setUserId(String.valueOf(log.getUserId()));
            model.setDropId(String.valueOf(log.getDropId()));
            model.setBeforeQty(log.getBeforeQty());
            model.setChangeQty(log.getChangeQty());
            model.setAfterQty(log.getAfterQty());
            redisCache.putHash(traceLogKey, traceIdStr, model);
            Long currentTtl = redisCache.getExpire(traceLogKey, TimeUnit.SECONDS);
            if (currentTtl == null || currentTtl <= 0) {
                redisCache.expire(traceLogKey, ttlSeconds, TimeUnit.SECONDS);
            }
        }
        return anyMissing;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markOrderStatus(Long orderId, ReconciliationStatus status) {
        dropOrderService.lambdaUpdate()
                .set(DropOrder::getReconciliationStatus, status.getCode())
                .set(DropOrder::getUpdateTime, LocalDateTime.now())
                .eq(DropOrder::getId, orderId)
                .update();
        dropReconcileLogService.lambdaUpdate()
                .set(DropReconcileLog::getReconciliationStatus, status.getCode())
                .set(DropReconcileLog::getUpdateTime, LocalDateTime.now())
                .eq(DropReconcileLog::getOrderId, orderId)
                .update();
    }
}
