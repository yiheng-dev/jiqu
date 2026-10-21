package com.jiqu.community.ratelimit.extension;

import com.jiqu.community.enums.BaseCode;


public class NoOpRateLimitPenaltyPolicy implements RateLimitPenaltyPolicy {
    @Override
    public void apply(RateLimitContext ctx, BaseCode reason) {
        // no-op
    }
}