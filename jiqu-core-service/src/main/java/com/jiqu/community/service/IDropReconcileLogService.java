package com.jiqu.community.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jiqu.community.dto.DropReconcileLogDto;
import com.jiqu.community.entity.DropReconcileLog;
import com.jiqu.community.kafka.message.LimitedDropMessage;
import com.jiqu.community.message.MessageExtend;


public interface IDropReconcileLogService extends IService<DropReconcileLog> {
    
    boolean saveReconcileLog(Integer logType,
                             Integer businessType,
                             String detail,
                             MessageExtend<LimitedDropMessage> message);
    
    boolean saveReconcileLog(Integer logType,
                             Integer businessType,
                             String detail,
                             Long traceId,
                             MessageExtend<LimitedDropMessage> message);
    
    
    boolean saveReconcileLog(DropReconcileLogDto dropReconcileLogDto);
}