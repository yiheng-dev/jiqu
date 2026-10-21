package com.jiqu.community.service.impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.core.RedisKeyManage;
import com.jiqu.community.entity.RollbackFailureLog;
import com.jiqu.community.redis.RedisCache;
import com.jiqu.community.redis.RedisKeyBuild;
import com.jiqu.community.service.IRollbackAlertService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class RollbackAlertServiceImpl implements IRollbackAlertService {

    @Value("${drop.rollback.alert.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${drop.rollback.alert.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${drop.rollback.alert.sms.to:}")
    private String smsTo;

    @Value("${drop.rollback.alert.email.to:}")
    private String emailTo;

    @Value("${drop.rollback.alert.dedup.window.seconds:300}")
    private long dedupWindowSeconds;

    @Resource
    private RedisCache redisCache;

    @Override
    public void sendRollbackAlert(RollbackFailureLog logEntity) {
        try {
            if (!shouldNotify(logEntity.getDropId())) {
                return;
            }
            String content = formatContent(logEntity);
            if (smsEnabled && smsTo != null && !smsTo.isEmpty()) {
                log.warn("[ROLLBACK_SMS] to={} content={} ", smsTo, content);
            }
            if (emailEnabled && emailTo != null && !emailTo.isEmpty()) {
                log.warn("[ROLLBACK_EMAIL] to={} content={} ", emailTo, content);
            }
        } catch (Exception e) {
            log.warn("发送回滚失败通知异常", e);
        }
    }

    private boolean shouldNotify(Long dropId) {
        try {
            return redisCache.setIfAbsent(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_ROLLBACK_ALERT_DEDUP_KEY,dropId),
                    "1", 
                    dedupWindowSeconds, 
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            return true;
        }
    }

    private String formatContent(RollbackFailureLog rollbackFailureLog) {
        String time = 
                rollbackFailureLog.getCreateTime() == null ? 
                        "" 
                        : 
                        rollbackFailureLog.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return String.format("回滚失败告警 | dropId=%s userId=%s orderId=%s traceId=%s attempts=%s source=%s time=%s detail=%s", 
                rollbackFailureLog.getDropId(), 
                rollbackFailureLog.getUserId(), 
                rollbackFailureLog.getOrderId(), 
                rollbackFailureLog.getTraceId(), 
                rollbackFailureLog.getRetryAttempts(), 
                rollbackFailureLog.getSource(),
                time,
                rollbackFailureLog.getDetail());
    }
}