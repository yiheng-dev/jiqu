package com.jiqu.community.init;

import cn.hutool.core.date.LocalDateTimeUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.entity.LimitedDrop;
import com.jiqu.community.service.ILimitedDropService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;


@Slf4j
@Order(2)
@Component
public class LimitedDropDataRenewalInit {
    
    @Resource
    private ILimitedDropService limitedDropService;
    
    @PostConstruct
    public void init(){
        updateBeginAndEndTime();
        //将库存数量恢复
        //renewalStock();
    }
    
    public void updateBeginAndEndTime(){
        log.info("==========更新优惠券的开始时间和结束时间==========");
        //查询优惠券结束时间小于2天前的节目演出数据
        List<LimitedDrop> limitedDropList =
                limitedDropService.lambdaQuery()
                        .le(LimitedDrop::getEndTime,
                                LocalDateTimeUtil.offset(LocalDateTimeUtil.now(), 2, ChronoUnit.DAYS))
                        .list();
        for (LimitedDrop limitedDrop : limitedDropList) {
            LocalDateTime oldBeginTime = limitedDrop.getBeginTime();
            LocalDateTime oldEndTime = limitedDrop.getEndTime();
            //将现有的开始时间加上15天作为新的开始时间
            LocalDateTime newBeginTime = LocalDateTimeUtil.offset(oldBeginTime, 15, ChronoUnit.DAYS);
            //将现有的结束时间加上15天作为新的结束时间
            LocalDateTime newEndTime = LocalDateTimeUtil.offset(oldEndTime, 15, ChronoUnit.DAYS);
            LocalDateTime nowTime = LocalDateTimeUtil.now();
            //如果新的结束时间还是小于当前时间，则继续再1天，直到新的结束时间大于当前时间为止
            while (newEndTime.isBefore(nowTime)) {
                newBeginTime = LocalDateTimeUtil.offset(newBeginTime,1,ChronoUnit.DAYS);
                newEndTime = LocalDateTimeUtil.offset(newEndTime,1,ChronoUnit.DAYS);
            }
            //执行更新
            limitedDropService.lambdaUpdate()
                    .set(LimitedDrop::getBeginTime, newBeginTime)
                    .set(LimitedDrop::getEndTime, newEndTime)
                    .set(LimitedDrop::getUpdateTime,LocalDateTimeUtil.now())
                    .eq(LimitedDrop::getId,limitedDrop.getId())
                    .eq(LimitedDrop::getDropId,limitedDrop.getDropId())
                    .update();
        }
    }
    
    public void renewalStock(){
        log.info("==========将优惠券的库存数量恢复==========");
        //将库存数量恢复
        List<LimitedDrop> limitedDropList = limitedDropService.list();
        for (LimitedDrop limitedDrop : limitedDropList) {
            if (!limitedDrop.getInitStock().equals(limitedDrop.getStock())) {
                //执行更新
                limitedDropService.lambdaUpdate()
                        .set(LimitedDrop::getStock,limitedDrop.getInitStock())
                        .set(LimitedDrop::getUpdateTime,LocalDateTimeUtil.now())
                        .eq(LimitedDrop::getId,limitedDrop.getId())
                        .eq(LimitedDrop::getDropId,limitedDrop.getDropId())
                        .update();
            }
        }
    }
}
