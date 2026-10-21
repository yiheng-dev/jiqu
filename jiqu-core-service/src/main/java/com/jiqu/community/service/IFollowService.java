package com.jiqu.community.service;

import com.jiqu.community.dto.Result;
import com.jiqu.community.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IFollowService extends IService<Follow> {

    Result follow(Long followUserId, Boolean isFollow);

    Result isFollow(Long followUserId);

    Result followCommons(Long id);

    Result followCount();
}
