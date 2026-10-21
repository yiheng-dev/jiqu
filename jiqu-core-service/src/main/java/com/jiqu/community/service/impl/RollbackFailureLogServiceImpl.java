package com.jiqu.community.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jiqu.community.entity.RollbackFailureLog;
import com.jiqu.community.mapper.RollbackFailureLogMapper;
import com.jiqu.community.service.IRollbackFailureLogService;
import org.springframework.stereotype.Service;


@Service
public class RollbackFailureLogServiceImpl extends ServiceImpl<RollbackFailureLogMapper, RollbackFailureLog>
        implements IRollbackFailureLogService {
}