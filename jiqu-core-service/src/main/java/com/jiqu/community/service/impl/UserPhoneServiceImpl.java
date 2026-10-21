package com.jiqu.community.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.entity.UserPhone;
import com.jiqu.community.mapper.UserPhoneMapper;
import com.jiqu.community.service.IUserPhoneService;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class UserPhoneServiceImpl extends ServiceImpl<UserPhoneMapper, UserPhone> implements IUserPhoneService {
    
}
