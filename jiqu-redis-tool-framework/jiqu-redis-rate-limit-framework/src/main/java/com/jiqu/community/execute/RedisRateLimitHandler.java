package com.jiqu.community.execute;

import jakarta.servlet.http.HttpServletRequest;
import com.jiqu.community.config.DropRateLimitConfigProperties;
import com.jiqu.community.core.RedisKeyManage;
import com.jiqu.community.enums.BaseCode;
import com.jiqu.community.exception.JiquFrameException;
import com.jiqu.community.lua.SlidingRateLimitOperate;
import com.jiqu.community.lua.TokenBucketRateLimitOperate;
import com.jiqu.community.ratelimit.extension.RateLimitContext;
import com.jiqu.community.ratelimit.extension.RateLimitEventListener;
import com.jiqu.community.ratelimit.extension.RateLimitPenaltyPolicy;
import com.jiqu.community.ratelimit.extension.RateLimitScene;
import com.jiqu.community.redis.RedisCache;
import com.jiqu.community.redis.RedisKeyBuild;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class RedisRateLimitHandler implements RateLimitHandler {

    private final DropRateLimitConfigProperties dropRateLimitConfigProperties;
    private final RedisCache redisCache;
    private final SlidingRateLimitOperate slidingRateLimitOperate;
    private final TokenBucketRateLimitOperate tokenBucketRateLimitOperate;
    private final RateLimitEventListener rateLimitEventListener;
    private final RateLimitPenaltyPolicy rateLimitPenaltyPolicy;

    public RedisRateLimitHandler(DropRateLimitConfigProperties dropRateLimitConfigProperties,
                                 RedisCache redisCache,
                                 SlidingRateLimitOperate slidingRateLimitOperate,
                                 TokenBucketRateLimitOperate tokenBucketRateLimitOperate,
                                 RateLimitEventListener rateLimitEventListener,
                                 RateLimitPenaltyPolicy rateLimitPenaltyPolicy) {
        this.dropRateLimitConfigProperties = dropRateLimitConfigProperties;
        this.redisCache = redisCache;
        this.slidingRateLimitOperate = slidingRateLimitOperate;
        this.tokenBucketRateLimitOperate = tokenBucketRateLimitOperate;
        this.rateLimitEventListener = rateLimitEventListener;
        this.rateLimitPenaltyPolicy = rateLimitPenaltyPolicy;
    }

    @Override
    public void execute(Long dropId,
                        Long userId,
                        RateLimitScene scene) {
        String clientIp = resolveClientIp();

        if (isWhitelisted(userId, clientIp)) {
            return;
        }
        checkBans(dropId, userId, clientIp);
        int ipLimitWindowMillis = resolveIpWindow(scene);
        int ipLimitMaxAttempts = resolveIpMaxAttempts(scene);
        int userLimitWindowMillis = resolveUserWindow(scene);
        int userLimitMaxAttempts = resolveUserMaxAttempts(scene);
        boolean useSliding = resolveSliding();
        List<String> keys = buildRateLimitKeys(dropId, userId, clientIp, useSliding);
        String[] args = buildArgs(ipLimitWindowMillis, ipLimitMaxAttempts, userLimitWindowMillis, userLimitMaxAttempts);

        RateLimitContext ctx = buildContext(dropId, userId, clientIp, keys, useSliding,
                ipLimitWindowMillis, ipLimitMaxAttempts, userLimitWindowMillis, userLimitMaxAttempts);
        safeBeforeExecute(ctx);
        Integer result = executeLua(useSliding, keys, args);
        ctx.setResult(result);
        handleResult(ctx);
    }
    
    private int resolveIpWindow(RateLimitScene scene) {
        DropRateLimitConfigProperties.EndpointLimit ep = 
                scene == RateLimitScene.ISSUE_TOKEN 
                        ? 
                        dropRateLimitConfigProperties.getIssue() 
                        : 
                        dropRateLimitConfigProperties.getDrop();
        
        Integer v = 
                ep != null
                        ? 
                        ep.getIpWindowMillis() 
                        : 
                        null;
        
        return v != null 
                ? 
                v 
                : 
                dropRateLimitConfigProperties.getIpWindowMillis();
    }
    private int resolveIpMaxAttempts(RateLimitScene scene) {
        DropRateLimitConfigProperties.EndpointLimit ep = 
                scene == RateLimitScene.ISSUE_TOKEN 
                        ? 
                        dropRateLimitConfigProperties.getIssue() 
                        : 
                        dropRateLimitConfigProperties.getDrop();
        
        Integer v = 
                ep != null 
                        ? 
                        ep.getIpMaxAttempts() 
                        : 
                        null;
        return v != null 
                ? 
                v 
                : 
                dropRateLimitConfigProperties.getIpMaxAttempts();
    }
    private int resolveUserWindow(RateLimitScene scene) {
        DropRateLimitConfigProperties.EndpointLimit ep = 
                scene == RateLimitScene.ISSUE_TOKEN 
                        ? 
                        dropRateLimitConfigProperties.getIssue() 
                        : 
                        dropRateLimitConfigProperties.getDrop();
        
        Integer v = 
                ep != null 
                        ? 
                        ep.getUserWindowMillis() 
                        : 
                        null;
        
        return v != null 
                ? 
                v 
                : 
                dropRateLimitConfigProperties.getUserWindowMillis();
    }
    private int resolveUserMaxAttempts(RateLimitScene scene) {
        DropRateLimitConfigProperties.EndpointLimit ep = 
                scene == RateLimitScene.ISSUE_TOKEN 
                        ? 
                        dropRateLimitConfigProperties.getIssue() 
                        : 
                        dropRateLimitConfigProperties.getDrop();
        
        Integer v = 
                ep != null 
                        ? 
                        ep.getUserMaxAttempts() 
                        : 
                        null;
        
        return v != null 
                ? 
                v 
                : 
                dropRateLimitConfigProperties.getUserMaxAttempts();
    }

    private boolean resolveSliding() {
        return dropRateLimitConfigProperties.getEnableSlidingWindow();
    }
    
    private String resolveClientIp(){
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                String[] parts = xff.split(",");
                if (parts.length > 0) {
                    String ip = parts[0].trim();
                    if (!ip.isEmpty()) {
                        return ip;
                    }
                }
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isEmpty()) {
                return realIp;
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isWhitelisted(Long userId, 
                                  String clientIp) {
        try {
            return (clientIp != null && dropRateLimitConfigProperties.getIpWhitelist() != null
                    && dropRateLimitConfigProperties.getIpWhitelist().contains(clientIp))
                    || (userId != null && dropRateLimitConfigProperties.getUserWhitelist() != null
                    && dropRateLimitConfigProperties.getUserWhitelist().contains(userId));
        } catch (Exception e) {
            return false;
        }
    }

    private void checkBans(Long dropId, 
                           Long userId, 
                           String clientIp) {
        
        if (Objects.nonNull(clientIp)) {
            boolean ipBlocked = Boolean.TRUE.equals(redisCache.hasKey(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_BLOCK_IP_TAG_KEY, dropId, clientIp)));
            if (ipBlocked) {
                throw new JiquFrameException(BaseCode.DROP_RATE_LIMIT_IP_EXCEEDED);
            }
        }
        boolean userBlocked = Boolean.TRUE.equals(redisCache.hasKey(
                RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_BLOCK_USER_TAG_KEY, dropId, userId)));
        if (userBlocked) {
            throw new JiquFrameException(BaseCode.DROP_RATE_LIMIT_USER_EXCEEDED);
        }
    }

    private List<String> buildRateLimitKeys(Long dropId, 
                                            Long userId, 
                                            String clientIp, 
                                            boolean useSliding) {
        List<String> keys = new ArrayList<>(2);
        if (Objects.nonNull(clientIp)) {
            String ipKey = useSliding
                    ? RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_LIMIT_IP_SW_TAG_KEY, dropId, clientIp).getRelKey()
                    : RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_LIMIT_IP_TB_TAG_KEY, dropId, clientIp).getRelKey();
            keys.add(ipKey);
        }
        String userKey = useSliding
                ? RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_LIMIT_USER_SW_TAG_KEY, dropId, userId).getRelKey()
                : RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_LIMIT_USER_TB_TAG_KEY, dropId, userId).getRelKey();
        keys.add(userKey);
        return keys;
    }

    private String[] buildArgs(int ipWindowMillis, 
                               int ipMaxAttempts, 
                               int userWindowMillis, 
                               int userMaxAttempts) {
        String[] args = new String[4];
        args[0] = String.valueOf(ipWindowMillis);
        args[1] = String.valueOf(ipMaxAttempts);
        args[2] = String.valueOf(userWindowMillis);
        args[3] = String.valueOf(userMaxAttempts);
        return args;
    }

    private RateLimitContext buildContext(Long dropId, 
                                          Long userId, 
                                          String clientIp, 
                                          List<String> keys,
                                          boolean useSliding,
                                          int ipWindowMillis, int ipMaxAttempts,
                                          int userWindowMillis, int userMaxAttempts) {
        return new RateLimitContext(
                dropId,
                userId,
                clientIp,
                keys,
                useSliding,
                ipWindowMillis,
                ipMaxAttempts,
                userWindowMillis,
                userMaxAttempts
        );
    }

    private void safeBeforeExecute(RateLimitContext ctx) {
        rateLimitEventListener.onBeforeExecute(ctx);
    }

    private Integer executeLua(boolean useSliding, List<String> keys, String[] args) {
        return useSliding 
                ?
                slidingRateLimitOperate.execute(keys, args).intValue() 
                :
                tokenBucketRateLimitOperate.execute(keys, args).intValue();
    }

    private void handleResult(RateLimitContext ctx) {
        Integer result = ctx.getResult();
        if (BaseCode.SUCCESS.getCode().equals(result)) {
            rateLimitEventListener.onAllowed(ctx);
            return;
        }
        if (BaseCode.DROP_RATE_LIMIT_IP_EXCEEDED.getCode().equals(result)) {
            rateLimitEventListener.onBlocked(ctx, BaseCode.DROP_RATE_LIMIT_IP_EXCEEDED);
            rateLimitPenaltyPolicy.apply(ctx, BaseCode.DROP_RATE_LIMIT_IP_EXCEEDED);
            throw new JiquFrameException(BaseCode.DROP_RATE_LIMIT_IP_EXCEEDED);
        }
        if (BaseCode.DROP_RATE_LIMIT_USER_EXCEEDED.getCode().equals(result)) {
            rateLimitEventListener.onBlocked(ctx, BaseCode.DROP_RATE_LIMIT_USER_EXCEEDED);
            rateLimitPenaltyPolicy.apply(ctx, BaseCode.DROP_RATE_LIMIT_USER_EXCEEDED);
            throw new JiquFrameException(BaseCode.DROP_RATE_LIMIT_USER_EXCEEDED);
        }
        throw new JiquFrameException("操作频繁，请稍后再试");
    }
}
