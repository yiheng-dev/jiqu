package com.jiqu.community.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jiqu.community.dto.LoginFormDTO;
import com.jiqu.community.dto.Result;
import com.jiqu.community.entity.User;
import jakarta.servlet.http.HttpSession;



public interface IUserService extends IService<User> {

    Result<String> sendCode(String phone, HttpSession session);

    Result<String> login(LoginFormDTO loginForm, HttpSession session);

    void logout(String token);

    Result<Void> sign();

    Result<Integer> signCount();

}
