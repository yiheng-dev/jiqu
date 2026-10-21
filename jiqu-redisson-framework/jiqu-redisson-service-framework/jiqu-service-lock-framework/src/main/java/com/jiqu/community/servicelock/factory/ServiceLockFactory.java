package com.jiqu.community.servicelock.factory;

import com.jiqu.community.core.ManageLocker;
import com.jiqu.community.servicelock.LockType;
import com.jiqu.community.servicelock.ServiceLocker;
import lombok.AllArgsConstructor;


@AllArgsConstructor
public class ServiceLockFactory {
    
    private final ManageLocker manageLocker;
    

    public ServiceLocker getLock(LockType lockType){
        ServiceLocker lock;
        switch (lockType) {
            case Fair:
                lock = manageLocker.getFairLocker();
                break;
            case Write:
                lock = manageLocker.getWriteLocker();
                break;
            case Read:
                lock = manageLocker.getReadLocker();
                break;
            default:
                lock = manageLocker.getReentrantLocker();
                break;
        }
        return lock;
    }
}
