package com.jiqu.community.cache;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.jiqu.community.model.LimitedDropFullModel;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;


@Component
public class LimitedDropLocalCache {
    
    private final Cache<String, LimitedDropFullModel> cache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfter(new Expiry<String, LimitedDropFullModel>() {
                @Override
                public long expireAfterCreate(String key, LimitedDropFullModel value, long currentTime) {
                    long ttlSeconds = 60L;
                    if (value != null && value.getEndTime() != null) {
                        ttlSeconds = Math.max(
                                LocalDateTimeUtil.between(LocalDateTimeUtil.now(), value.getEndTime()).getSeconds(),
                                1L
                        );
                    }
                    return TimeUnit.NANOSECONDS.convert(ttlSeconds, TimeUnit.SECONDS);
                }
                
                @Override
                public long expireAfterUpdate(String key, LimitedDropFullModel value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
                
                @Override
                public long expireAfterRead(String key, LimitedDropFullModel value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();
    
    public LimitedDropFullModel get(String dropId) {
        return cache.getIfPresent(dropId);
    }
    
    public void put(String dropId, LimitedDropFullModel drop) {
        if (dropId != null && drop != null) {
            cache.put(dropId, drop);
        }
    }
    
    public void invalidate(String dropId) {
        cache.invalidate(dropId);
    }
}