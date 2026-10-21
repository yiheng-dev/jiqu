package com.jiqu.community.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jiqu.community.entity.UserInfo;


public interface IUserInfoService extends IService<UserInfo> {
    
    /**
     * 通过用户id查询用户信息
     * @param userId 用户ID
     * @return 结果
     */
    UserInfo getByUserId(Long userId);

}
