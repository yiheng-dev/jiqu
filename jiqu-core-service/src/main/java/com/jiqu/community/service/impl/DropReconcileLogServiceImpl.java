package com.jiqu.community.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import com.jiqu.community.dto.DropReconcileLogDto;
import com.jiqu.community.entity.DropReconcileLog;
import com.jiqu.community.enums.LogType;
import com.jiqu.community.kafka.message.LimitedDropMessage;
import com.jiqu.community.mapper.DropReconcileLogMapper;
import com.jiqu.community.message.MessageExtend;
import com.jiqu.community.service.IDropReconcileLogService;
import com.jiqu.community.toolkit.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Service
public class DropReconcileLogServiceImpl extends ServiceImpl<DropReconcileLogMapper, DropReconcileLog>
        implements IDropReconcileLogService {
    
    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveReconcileLog(final Integer logType, final Integer businessType, final String detail, final MessageExtend<LimitedDropMessage> message) {
        LimitedDropMessage messageBody = message.getMessageBody();
        DropReconcileLogDto dropReconcileLogDto = new DropReconcileLogDto();
        dropReconcileLogDto.setOrderId(messageBody.getOrderId());
        dropReconcileLogDto.setUserId(messageBody.getUserId());
        dropReconcileLogDto.setDropId(messageBody.getDropId());
        dropReconcileLogDto.setMessageId(message.getUuid());
        dropReconcileLogDto.setDetail(detail);
        dropReconcileLogDto.setBeforeQty(messageBody.getBeforeQty());
        dropReconcileLogDto.setChangeQty(messageBody.getChangeQty());
        dropReconcileLogDto.setAfterQty(messageBody.getAfterQty());
        dropReconcileLogDto.setTraceId(messageBody.getTraceId());
        dropReconcileLogDto.setLogType(logType);
        dropReconcileLogDto.setBusinessType(businessType);
        if (dropReconcileLogDto.getLogType().equals(LogType.RESTORE.getCode())) {
            dropReconcileLogDto.setBeforeQty(messageBody.getAfterQty());
            dropReconcileLogDto.setAfterQty(messageBody.getBeforeQty());
        }
        return saveReconcileLog(dropReconcileLogDto);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveReconcileLog(final Integer logType, final Integer businessType, final String detail, final Long traceId, final MessageExtend<LimitedDropMessage> message) {
        LimitedDropMessage messageBody = message.getMessageBody();
        DropReconcileLogDto dropReconcileLogDto = new DropReconcileLogDto();
        dropReconcileLogDto.setOrderId(messageBody.getOrderId());
        dropReconcileLogDto.setUserId(messageBody.getUserId());
        dropReconcileLogDto.setDropId(messageBody.getDropId());
        dropReconcileLogDto.setMessageId(message.getUuid());
        dropReconcileLogDto.setDetail(detail);
        dropReconcileLogDto.setBeforeQty(messageBody.getBeforeQty());
        dropReconcileLogDto.setChangeQty(messageBody.getChangeQty());
        dropReconcileLogDto.setAfterQty(messageBody.getAfterQty());
        dropReconcileLogDto.setTraceId(traceId);
        dropReconcileLogDto.setLogType(logType);
        dropReconcileLogDto.setBusinessType(businessType);
        if (dropReconcileLogDto.getLogType().equals(LogType.RESTORE.getCode())) {
            dropReconcileLogDto.setBeforeQty(messageBody.getAfterQty());
            dropReconcileLogDto.setAfterQty(messageBody.getBeforeQty());
        }
        return saveReconcileLog(dropReconcileLogDto);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveReconcileLog(DropReconcileLogDto dropReconcileLogDto) {
        DropReconcileLog logEntity = new DropReconcileLog();
        logEntity.setId(snowflakeIdGenerator.nextId())
                .setOrderId(dropReconcileLogDto.getOrderId())
                .setUserId(dropReconcileLogDto.getUserId())
                .setDropId(dropReconcileLogDto.getDropId())
                .setMessageId(dropReconcileLogDto.getMessageId())
                .setBusinessType(dropReconcileLogDto.getBusinessType())
                .setDetail(dropReconcileLogDto.getDetail())
                .setTraceId(dropReconcileLogDto.getTraceId())
                .setLogType(dropReconcileLogDto.getLogType())
                .setCreateTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now())
                .setBeforeQty(dropReconcileLogDto.getBeforeQty())
                .setChangeQty(dropReconcileLogDto.getChangeQty())
                .setAfterQty(dropReconcileLogDto.getAfterQty());
        return save(logEntity);
    }
}