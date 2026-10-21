package com.jiqu.community.config;

import com.jiqu.community.constant.LockInfoType;
import com.jiqu.community.core.ManageLocker;
import com.jiqu.community.lockinfo.LockInfoHandle;
import com.jiqu.community.lockinfo.factory.LockInfoHandleFactory;
import com.jiqu.community.lockinfo.impl.ServiceLockInfoHandle;
import com.jiqu.community.servicelock.aspect.ServiceLockAspect;
import com.jiqu.community.servicelock.factory.ServiceLockFactory;
import com.jiqu.community.util.ServiceLockTool;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;


public class ServiceLockAutoConfiguration {
    
    @Bean(LockInfoType.SERVICE_LOCK)
    public LockInfoHandle serviceLockInfoHandle(){
        return new ServiceLockInfoHandle();
    }
    
    @Bean
    public ManageLocker manageLocker(RedissonClient redissonClient){
        return new ManageLocker(redissonClient);
    }
    
    @Bean
    public ServiceLockFactory serviceLockFactory(ManageLocker manageLocker){
        return new ServiceLockFactory(manageLocker);
    }
    
    @Bean
    public ServiceLockAspect serviceLockAspect(LockInfoHandleFactory lockInfoHandleFactory,ServiceLockFactory serviceLockFactory){
        return new ServiceLockAspect(lockInfoHandleFactory,serviceLockFactory);
    }
    
    @Bean
    public ServiceLockTool serviceLockTooL(LockInfoHandleFactory lockInfoHandleFactory,ServiceLockFactory serviceLockFactory){
        return new ServiceLockTool(lockInfoHandleFactory,serviceLockFactory);
    }
}
