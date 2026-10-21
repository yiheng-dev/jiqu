package com.jiqu.community.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jiqu.community.entity.LimitedDrop;
import com.jiqu.community.model.LimitedDropFullModel;


public interface ILimitedDropService extends IService<LimitedDrop> {
    
    LimitedDropFullModel queryByDropId(Long dropId);
    
    void loadDropStock(Long dropId);
    
    boolean rollbackStock(Long dropId);
}
