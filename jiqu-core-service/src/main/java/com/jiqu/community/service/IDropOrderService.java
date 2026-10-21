package com.jiqu.community.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jiqu.community.dto.CancelDropOrderDto;
import com.jiqu.community.dto.GetDropOrderByDropIdDto;
import com.jiqu.community.dto.GetDropOrderDto;
import com.jiqu.community.dto.Result;
import com.jiqu.community.entity.DropOrder;
import com.jiqu.community.kafka.message.LimitedDropMessage;
import com.jiqu.community.message.MessageExtend;


public interface IDropOrderService extends IService<DropOrder> {

    Result<Long> limitedDrop(Long dropId);

    void createDropOrderV1(DropOrder dropOrder);
    
    boolean createDropOrderV2(MessageExtend<LimitedDropMessage> message);
    
    Long getLimitedDropOrder(GetDropOrderDto getDropOrderDto);
    
    Boolean cancel(CancelDropOrderDto cancelDropOrderDto);
    
    boolean autoIssueDropToEarliestSubscriber(final Long dropId, final Long excludeUserId);
    
    Long getLimitedDropOrderIdByDropId(GetDropOrderByDropIdDto getDropOrderByDropIdDto);
}
