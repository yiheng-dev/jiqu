package com.jiqu.community.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jiqu.community.dto.DelayDropReminderDto;
import com.jiqu.community.dto.Result;
import com.jiqu.community.dto.LimitedDropDto;
import com.jiqu.community.dto.UpdateLimitedDropDto;
import com.jiqu.community.dto.UpdateLimitedDropStockDto;
import com.jiqu.community.dto.DropDto;
import com.jiqu.community.dto.DropSubscribeBatchDto;
import com.jiqu.community.dto.DropSubscribeDto;
import com.jiqu.community.dto.DropCalendarVo;
import com.jiqu.community.entity.Drop;
import com.jiqu.community.vo.GetSubscribeStatusVo;

import java.util.List;


public interface IDropService extends IService<Drop> {

    Long addDrop(DropDto dropDto);
    
    Result<List<Drop>> queryDropOfStore(Long storeId);

    Long addLimitedDrop(LimitedDropDto limitedDropDto);
    
    void updateLimitedDrop(UpdateLimitedDropDto updateLimitedDropDto);
    
    void updateLimitedDropStock(UpdateLimitedDropStockDto updateLimitedDropDto);
    
    void subscribe(DropSubscribeDto dropSubscribeDto);
    
    void unsubscribe(DropSubscribeDto dropSubscribeDto);
    
    Integer getSubscribeStatus(DropSubscribeDto dropSubscribeDto);
    
    List<GetSubscribeStatusVo> getSubscribeStatusBatch(DropSubscribeBatchDto dropSubscribeBatchDto);
    
    void delayDropReminder(DelayDropReminderDto delayDropReminderDto);

    List<DropCalendarVo> calendar();
}
