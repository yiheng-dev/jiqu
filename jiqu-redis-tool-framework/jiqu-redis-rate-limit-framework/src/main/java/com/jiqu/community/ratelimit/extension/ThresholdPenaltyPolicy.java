package com.jiqu.community.ratelimit.extension;

import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.config.DropRateLimitConfigProperties;
import com.jiqu.community.core.RedisKeyManage;
import com.jiqu.community.enums.BaseCode;
import com.jiqu.community.redis.RedisCache;
import com.jiqu.community.redis.RedisKeyBuild;

import java.util.Objects;
import java.util.concurrent.TimeUnit;


@Slf4j
public class ThresholdPenaltyPolicy implements RateLimitPenaltyPolicy {

    private final RedisCache redisCache;
    private final DropRateLimitConfigProperties props;

    public ThresholdPenaltyPolicy(RedisCache redisCache, DropRateLimitConfigProperties props) {
        this.redisCache = redisCache;
        this.props = props;
    }

    @Override
    public void apply(RateLimitContext context, BaseCode reason) {
        try {
            if (reason == BaseCode.DROP_RATE_LIMIT_IP_EXCEEDED) {
                applyForIp(context);
            } else if (reason == BaseCode.DROP_RATE_LIMIT_USER_EXCEEDED) {
                applyForUser(context);
            }
        } catch (Exception e) {
            log.debug("Penalty policy apply failed: {}", e.getMessage());
        }
    }

    private void applyForIp(RateLimitContext ctx) {
        Long dropId = ctx.getDropId();
        String clientIp = ctx.getClientIp();
        if (Objects.isNull(dropId) || Objects.isNull(clientIp)) {
            return;
        }
        RedisKeyBuild violationKey = RedisKeyBuild.createRedisKey(
                RedisKeyManage.DROP_VIOLATION_IP_TAG_KEY, dropId, clientIp);
        long count = redisCache.incrBy(violationKey, 1L);
        if (count == 1L) {
            redisCache.expire(violationKey, props.getViolationWindowSeconds(), TimeUnit.SECONDS);
        }
        if (count >= props.getIpBlockThreshold()) {
            RedisKeyBuild blockKey = RedisKeyBuild.createRedisKey(
                    RedisKeyManage.DROP_BLOCK_IP_TAG_KEY, dropId, clientIp);
            redisCache.set(blockKey, "1", props.getIpBlockTtlSeconds(), TimeUnit.SECONDS);
            log.warn("Temporary banned IP: dropId={}, ip={}, ttlSeconds={}, violationCount={}",
                    dropId, clientIp, props.getIpBlockTtlSeconds(), count);
        }
    }

    private void applyForUser(RateLimitContext ctx) {
        Long dropId = ctx.getDropId();
        Long userId = ctx.getUserId();
        if (Objects.isNull(dropId) || Objects.isNull(userId)) {
            return;
        }
        RedisKeyBuild violationKey = RedisKeyBuild.createRedisKey(
                RedisKeyManage.DROP_VIOLATION_USER_TAG_KEY, dropId, userId);
        long count = redisCache.incrBy(violationKey, 1L);
        if (count == 1L) {
            redisCache.expire(violationKey, props.getViolationWindowSeconds(), TimeUnit.SECONDS);
        }
        if (count >= props.getUserBlockThreshold()) {
            RedisKeyBuild blockKey = RedisKeyBuild.createRedisKey(
                    RedisKeyManage.DROP_BLOCK_USER_TAG_KEY, dropId, userId);
            redisCache.set(blockKey, "1", props.getUserBlockTtlSeconds(), TimeUnit.SECONDS);
            log.warn("Temporary banned user: dropId={}, userId={}, ttlSeconds={}, violationCount={}",
                    dropId, userId, props.getUserBlockTtlSeconds(), count);
        }
    }
}