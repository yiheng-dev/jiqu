package com.jiqu.community.service;

import com.jiqu.community.dto.Result;
import com.jiqu.community.entity.Store;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IStoreService extends IService<Store> {

    Result saveStore(Store store);
    
    Result queryById(Long id);

    Result update(Store store);

    Result queryStoreByType(Integer typeId, Integer current, Double x, Double y);
}
