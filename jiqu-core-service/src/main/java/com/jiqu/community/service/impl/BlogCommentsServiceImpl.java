package com.jiqu.community.service.impl;

import com.jiqu.community.entity.BlogComments;
import com.jiqu.community.mapper.BlogCommentsMapper;
import com.jiqu.community.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
