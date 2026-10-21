package com.jiqu.community.config;

import com.jiqu.community.constant.LockInfoType;
import com.jiqu.community.handle.RedissonDataHandle;
import com.jiqu.community.locallock.LocalLockCache;
import com.jiqu.community.lockinfo.LockInfoHandle;
import com.jiqu.community.lockinfo.factory.LockInfoHandleFactory;
import com.jiqu.community.lockinfo.impl.RepeatExecuteLimitLockInfoHandle;
import com.jiqu.community.repeatexecutelimit.aspect.RepeatExecuteLimitAspect;
import com.jiqu.community.servicelock.factory.ServiceLockFactory;
import org.springframework.context.annotation.Bean;


public class RepeatExecuteLimitAutoConfiguration {
    
    @Bean(LockInfoType.REPEAT_EXECUTE_LIMIT)
    public LockInfoHandle repeatExecuteLimitHandle(){
        return new RepeatExecuteLimitLockInfoHandle();
    }
    
    @Bean
    public RepeatExecuteLimitAspect repeatExecuteLimitAspect(LocalLockCache localLockCache,
                                                             LockInfoHandleFactory lockInfoHandleFactory,
                                                             ServiceLockFactory serviceLockFactory,
                                                             RedissonDataHandle redissonDataHandle){
        return new RepeatExecuteLimitAspect(localLockCache, lockInfoHandleFactory,serviceLockFactory,redissonDataHandle);
    }
}
    