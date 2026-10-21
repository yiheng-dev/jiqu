package com.jiqu.community.service.impl;

import cn.hutool.core.util.IdUtil;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.core.RedisKeyManage;
import com.jiqu.community.lua.DropAccessTokenOperate;
import com.jiqu.community.redis.RedisCache;
import com.jiqu.community.redis.RedisKeyBuild;
import com.jiqu.community.service.IDropAccessTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class DropAccessTokenServiceImpl implements IDropAccessTokenService {

    @Value("${drop.access.token.enabled:true}")
    private boolean enabled;

    @Value("${drop.access.token.ttl-seconds:30}")
    private long ttlSeconds;

    @Resource
    private RedisCache redisCache;
    
    @Resource
    private MeterRegistry meterRegistry;

    private DropAccessTokenOperate operate;

    @PostConstruct
    public void init() {
        operate = new DropAccessTokenOperate(redisCache);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String issueAccessToken(Long dropId, Long userId) {
        String token = IdUtil.simpleUUID();
        boolean ok = redisCache.setIfAbsent(
                RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_ACCESS_TOKEN_TAG_KEY, dropId, userId), 
                token, 
                ttlSeconds, 
                TimeUnit.SECONDS);
        if (!ok) {
            String existing = redisCache.get(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_ACCESS_TOKEN_TAG_KEY, dropId, userId), 
                    String.class);
            safeInc("drop_access_token_issue_conflict", "component", "service_impl");
            return existing != null ? existing : token;
        }
        safeInc("drop_access_token_issue_success", "component", "service_impl");
        log.info("获取到令牌成功！令牌：{}", token);
        return token;
    }

    @Override
    public boolean validateAndConsume(Long dropId, Long userId, String token) {
        String key = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_ACCESS_TOKEN_TAG_KEY, dropId, userId).getRelKey();
        boolean success = operate.validateAndConsume(key, token);
        safeInc(success ? "drop_access_token_consume_success" : "drop_access_token_consume_fail",
                "component", "service_impl");
        return success;
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