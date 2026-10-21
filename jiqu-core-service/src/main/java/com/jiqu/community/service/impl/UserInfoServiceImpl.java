package com.jiqu.community.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.core.RedisKeyManage;
import com.jiqu.community.entity.UserInfo;
import com.jiqu.community.enums.BaseCode;
import com.jiqu.community.exception.JiquFrameException;
import com.jiqu.community.mapper.UserInfoMapper;
import com.jiqu.community.redis.RedisCache;
import com.jiqu.community.redis.RedisKeyBuild;
import com.jiqu.community.service.IUserInfoService;
import com.jiqu.community.servicelock.LockType;
import com.jiqu.community.servicelock.annotion.ServiceLock;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static com.jiqu.community.constant.DistributedLockConstants.UPDATE_USER_INFO_LOCK;


@Slf4j
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

    @Resource
    private RedisCache redisCache;
    
    @Override
    @ServiceLock(lockType= LockType.Read,name = UPDATE_USER_INFO_LOCK,keys = {"#userId"})
    public UserInfo getByUserId(Long userId){
        UserInfo userInfo = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.USER_INFO_KEY, userId), UserInfo.class);
        if (Objects.nonNull(userInfo)){
            return userInfo;
        }
        userInfo = lambdaQuery().eq(UserInfo::getUserId, userId).one();
        if (Objects.isNull(userInfo)) {
            throw new JiquFrameException(BaseCode.USER_NOT_EXIST);
        }
        redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.USER_INFO_KEY, userId), userInfo);
        return userInfo;
    }
    
}
