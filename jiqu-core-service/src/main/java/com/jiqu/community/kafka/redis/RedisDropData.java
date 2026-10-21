package com.jiqu.community.kafka.redis;

import cn.hutool.core.collection.ListUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.core.RedisKeyManage;
import com.jiqu.community.entity.RollbackFailureLog;
import com.jiqu.community.enums.BaseCode;
import com.jiqu.community.enums.LogType;
import com.jiqu.community.enums.LimitedDropOrderOperate;
import com.jiqu.community.lua.LimitedDropRollBackOperate;
import com.jiqu.community.redis.RedisKeyBuild;
import com.jiqu.community.service.IRollbackFailureLogService;
import com.jiqu.community.service.IRollbackAlertService;
import com.jiqu.community.toolkit.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;



@Slf4j
@Component
public class RedisDropData {
    
    @Resource
    private LimitedDropRollBackOperate limitedDropRollBackOperate;
    
    @Resource
    private IRollbackFailureLogService rollbackFailureLogService;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;
    
    @Resource
    private MeterRegistry meterRegistry;
    
    @Resource
    private IRollbackAlertService rollbackAlertService;
    
    @Value("${drop.rollback.retry.maxAttempts:3}")
    private int retryMaxAttempts;
    
    @Value("${drop.rollback.retry.initialBackoffMillis:200}")
    private long initialBackoffMillis;
    
    @Value("${drop.rollback.retry.maxBackoffMillis:1000}")
    private long maxBackoffMillis;
    
    public void rollbackRedisDropData(LimitedDropOrderOperate limitedDropOrderOperate,
                                         Long traceId,
                                         Long dropId,
                                         Long userId,
                                         Long orderId,
                                         Integer beforeQty,
                                         Integer changeQty,
                                         Integer afterQty) {
        List<String> keys = ListUtil.of(
                RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_STOCK_TAG_KEY, dropId).getRelKey(),
                RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_USER_TAG_KEY, dropId).getRelKey(),
                RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_TRACE_LOG_TAG_KEY, dropId).getRelKey()
        );
        String[] args = new String[9];
        args[0] = String.valueOf(dropId);
        args[1] = String.valueOf(userId);
        args[2] = String.valueOf(orderId);
        args[3] = String.valueOf(limitedDropOrderOperate.getCode());
        args[4] = String.valueOf(traceId);
        args[5] = String.valueOf(LogType.RESTORE.getCode());
        args[6] = String.valueOf(beforeQty);
        args[7] = String.valueOf(changeQty);
        args[8] = String.valueOf(afterQty);
        
        Integer finalCode = luaRollbackWithResultCode(keys, args, retryMaxAttempts, initialBackoffMillis, maxBackoffMillis);
        boolean ok = finalCode != null && finalCode.equals(BaseCode.SUCCESS.getCode());
        if (!ok) {
            String reason = BaseCode.getMsg(finalCode == null ? -1 : finalCode);
            log.error("Redis回滚最终失败|dropId={}|userId={}|orderId={}|traceId={} reason={}", dropId, userId, orderId, traceId, reason);
            saveRollbackFailureLog(dropId, userId, orderId, traceId, "redis rollback failed after retries: " + reason, finalCode);
            safeInc("drop_rollback_retry_give_up", "component", "redis_drop_data");
        }
    }
    
    private Integer luaRollbackWithResultCode(
            List<String> keys,
            String[] args,
            int maxAttempts,
            long initialBackoffMs,
            long maxBackoffMs) {
        int attempt = 0;
        long backoff = Math.max(50, initialBackoffMs);
        Integer lastCode = null;
        while (true) {
            try {
                Integer result = limitedDropRollBackOperate.execute(keys, args);
                lastCode = result;
                if (result != null && result.equals(BaseCode.SUCCESS.getCode())) {
                    safeInc("drop_rollback_retry_success", "component", "redis_drop_data");
                    return result;
                }
                String reason = BaseCode.getMsg(result == null ? -1 : result);
                log.warn("Redis回滚失败，准备重试|attempt={} reason={}", attempt + 1, reason);
            } catch (Exception e) {
                lastCode = -1;
                log.warn("Redis回滚异常，准备重试|attempt={} error={}", attempt + 1, e.getMessage());
            }
            attempt++;
            if (attempt >= maxAttempts) {
                break;
            }
            sleepQuietly(withJitter(backoff));
            backoff = Math.min(backoff * 2, Math.max(backoff, maxBackoffMs));
        }
        return lastCode;
    }
    
    private long withJitter(long base) {
        long jitter = Math.round(base * 0.15 * Math.random());
        return base + jitter;
    }
    
    private void sleepQuietly(long backoffMs) {
        try {
            TimeUnit.MILLISECONDS.sleep(backoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void saveRollbackFailureLog(Long dropId, Long userId, Long orderId, Long traceId, String detail, Integer resultCode) {
        try {
            RollbackFailureLog logEntity = new RollbackFailureLog();
            logEntity.setId(snowflakeIdGenerator.nextId())
                    .setOrderId(orderId)
                    .setUserId(userId)
                    .setDropId(dropId)
                    .setDetail(detail)
                    .setResultCode(resultCode)
                    .setTraceId(traceId)
                    .setRetryAttempts(retryMaxAttempts)
                    .setSource("redis_drop_data")
                    .setCreateTime(LocalDateTime.now())
                    .setUpdateTime(LocalDateTime.now());
            rollbackFailureLogService.save(logEntity);
            safeInc("drop_rollback_failure", "component", "redis_drop_data");
            safeInc("drop_rollback_failure", "reason", "retry_exhausted");
            rollbackAlertService.sendRollbackAlert(logEntity);
        } catch (Exception e) {
            log.warn("保存回滚失败日志异常", e);
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
