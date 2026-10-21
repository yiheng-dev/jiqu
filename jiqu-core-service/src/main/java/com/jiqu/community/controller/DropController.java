package com.jiqu.community.controller;


import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import com.jiqu.community.dto.DelayDropReminderDto;
import com.jiqu.community.dto.GetLimitedDropDto;
import com.jiqu.community.dto.Result;
import com.jiqu.community.dto.DropCalendarVo;
import com.jiqu.community.dto.LimitedDropDto;
import com.jiqu.community.dto.UpdateLimitedDropDto;
import com.jiqu.community.dto.UpdateLimitedDropStockDto;
import com.jiqu.community.dto.DropDto;
import com.jiqu.community.dto.DropSubscribeBatchDto;
import com.jiqu.community.dto.DropSubscribeDto;
import com.jiqu.community.entity.Drop;
import com.jiqu.community.model.LimitedDropFullModel;
import com.jiqu.community.service.ILimitedDropService;
import com.jiqu.community.service.IDropService;
import com.jiqu.community.vo.GetSubscribeStatusVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;



@RestController
@RequestMapping("/drop")
public class DropController {

    @Resource
    private IDropService dropService;
    
    @Resource
    private ILimitedDropService limitedDropService;
    
    @PostMapping("/get")
    public Result<LimitedDropFullModel> get(@Valid @RequestBody GetLimitedDropDto getLimitedDropDto) {
        return Result.ok(limitedDropService.queryByDropId(getLimitedDropDto.getDropId()));
    }
    
    @PostMapping("/drop")
    public Result<Long> addLimitedDrop(@Valid @RequestBody LimitedDropDto limitedDropDto) {
        final Long dropId = dropService.addLimitedDrop(limitedDropDto);
        return Result.ok(dropId);
    }

    @PostMapping("/update/drop")
    public Result<Void> updateLimitedDrop(@Valid @RequestBody UpdateLimitedDropDto updateLimitedDropDto) {
        dropService.updateLimitedDrop(updateLimitedDropDto);
        return Result.ok();
    }
    
    @PostMapping("/update/drop/stock")
    public Result<Void> updateLimitedDropStock(@Valid @RequestBody UpdateLimitedDropStockDto updateLimitedDropDto) {
        dropService.updateLimitedDropStock(updateLimitedDropDto);
        return Result.ok();
    }

    @PostMapping
    public Result<Long> addDrop(@Valid @RequestBody DropDto dropDto) {
        final Long dropId = dropService.addDrop(dropDto);
        return Result.ok(dropId);
    }
    
    @GetMapping("/list/{storeId}")
    public Result<List<Drop>> queryDropOfStore(@PathVariable("storeId") Long storeId) {
       return dropService.queryDropOfStore(storeId);
    }

    @GetMapping("/calendar")
    public Result<List<DropCalendarVo>> calendar() {
        return Result.ok(dropService.calendar());
    }
    
    @PostMapping("/subscribe")
    public Result<Void> subscribe(@Valid @RequestBody DropSubscribeDto dropSubscribeDto){
        dropService.subscribe(dropSubscribeDto);
        return Result.ok();
    }
    
    @PostMapping("/unsubscribe")
    public Result<Void> unsubscribe(@Valid @RequestBody DropSubscribeDto dropSubscribeDto){
        dropService.unsubscribe(dropSubscribeDto);
        return Result.ok();
    }
    
    @PostMapping("/get/subscribe/status")
    public Result<Integer> getSubscribeStatus(@Valid @RequestBody DropSubscribeDto dropSubscribeDto){
        return Result.ok(dropService.getSubscribeStatus(dropSubscribeDto));
    }
    
    @PostMapping("/get/subscribe/status/batch")
    public Result<List<GetSubscribeStatusVo>> getSubscribeStatusBatch(@Valid @RequestBody DropSubscribeBatchDto dropSubscribeBatchDto){
        return Result.ok(dropService.getSubscribeStatusBatch(dropSubscribeBatchDto));
    }
    
    @PostMapping("/delay/drop/reminder")
    public Result<Void> delayDropReminder(@Valid @RequestBody DelayDropReminderDto delayDropReminderDto){
        dropService.delayDropReminder(delayDropReminderDto);
        return Result.ok();
    }
}
