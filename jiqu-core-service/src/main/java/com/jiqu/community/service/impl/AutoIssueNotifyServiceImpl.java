package com.jiqu.community.service.impl;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.core.RedisKeyManage;
import com.jiqu.community.redis.RedisCache;
import com.jiqu.community.redis.RedisKeyBuild;
import com.jiqu.community.service.IAutoIssueNotifyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class AutoIssueNotifyServiceImpl implements IAutoIssueNotifyService {

    @Value("${drop.autoissue.notify.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${drop.autoissue.notify.app.enabled:false}")
    private boolean appEnabled;

    @Value("${drop.autoissue.notify.sms.to:}")
    private String smsTo;

    @Value("${drop.autoissue.notify.dedup.window.seconds:300}")
    private long dedupWindowSeconds;

    @Resource
    private RedisCache redisCache;

    @Override
    public void sendAutoIssueNotify(Long dropId, Long userId, Long orderId) {
        try {
            if (!shouldNotify(dropId, userId)) {
                return;
            }
            String content = String.format("自动发券成功 | dropId=%s userId=%s orderId=%s", dropId, userId, orderId);
            if (smsEnabled && StrUtil.isNotBlank(smsTo)) {
                log.info("[AUTOISSUE_SMS] to={} content={}", smsTo, content);
            }
            if (appEnabled) {
                log.info("[AUTOISSUE_APP] userId={} content={}", userId, content);
            }
        } catch (Exception e) {
            log.warn("发送自动发券通知异常", e);
        }
    }

    private boolean shouldNotify(Long dropId, Long userId) {
        try {
            return redisCache.setIfAbsent(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_AUTO_ISSUE_NOTIFY_DEDUP_KEY, dropId, userId),
                    "1",
                    dedupWindowSeconds,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            return true;
        }
    }
}