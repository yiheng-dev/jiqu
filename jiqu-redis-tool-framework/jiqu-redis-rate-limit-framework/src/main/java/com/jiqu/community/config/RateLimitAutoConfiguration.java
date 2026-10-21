package com.jiqu.community.config;

import com.jiqu.community.execute.RedisRateLimitHandler;
import com.jiqu.community.lua.SlidingRateLimitOperate;
import com.jiqu.community.lua.TokenBucketRateLimitOperate;
import com.jiqu.community.ratelimit.extension.NoOpRateLimitEventListener;
import com.jiqu.community.ratelimit.extension.NoOpRateLimitPenaltyPolicy;
import com.jiqu.community.ratelimit.extension.RateLimitEventListener;
import com.jiqu.community.ratelimit.extension.RateLimitPenaltyPolicy;
import com.jiqu.community.ratelimit.extension.ThresholdPenaltyPolicy;
import com.jiqu.community.redis.RedisCache;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;


@EnableConfigurationProperties(DropRateLimitConfigProperties.class)
public class RateLimitAutoConfiguration {
    
    @Bean
    public SlidingRateLimitOperate slidingRateLimitOperate(RedisCache redisCache){
        return new SlidingRateLimitOperate(redisCache);
    }
    
    @Bean
    public TokenBucketRateLimitOperate tokenBucketRateLimitOperate(RedisCache redisCache){
        return new TokenBucketRateLimitOperate(redisCache);
    }

    @Bean
    public RateLimitEventListener rateLimitEventListener(){
        return new NoOpRateLimitEventListener();
    }

    @Bean
    public RateLimitPenaltyPolicy rateLimitPenaltyPolicy(DropRateLimitConfigProperties dropRateLimitConfigProperties,
                                                         RedisCache redisCache){
        
        Boolean enable = dropRateLimitConfigProperties.getEnablePenalty();
        if (Boolean.TRUE.equals(enable)) {
            return new ThresholdPenaltyPolicy(redisCache, dropRateLimitConfigProperties);
        }
        return new NoOpRateLimitPenaltyPolicy();
    }

    @Bean
    public RedisRateLimitHandler redisRateLimitHandler(DropRateLimitConfigProperties dropRateLimitConfigProperties,
                                                       RedisCache redisCache,
                                                       SlidingRateLimitOperate slidingRateLimitOperate,
                                                       TokenBucketRateLimitOperate tokenBucketRateLimitOperate,
                                                       RateLimitEventListener rateLimitEventListener,
                                                       RateLimitPenaltyPolicy rateLimitPenaltyPolicy) {
        return new RedisRateLimitHandler(
                dropRateLimitConfigProperties, 
                redisCache,
                slidingRateLimitOperate,
                tokenBucketRateLimitOperate,
                rateLimitEventListener,
                rateLimitPenaltyPolicy
        );
    }
}
