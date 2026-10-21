package com.jiqu.community.execute;
import com.jiqu.community.ratelimit.extension.RateLimitScene;


public interface RateLimitHandler {
   
    void execute(Long dropId, Long userId, RateLimitScene scene);
}
