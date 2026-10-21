package com.jiqu.community.init;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.entity.LimitedDrop;
import com.jiqu.community.entity.Store;
import com.jiqu.community.handler.BloomFilterHandlerFactory;
import com.jiqu.community.service.ILimitedDropService;
import com.jiqu.community.service.IStoreService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.jiqu.community.constant.Constant.BLOOM_FILTER_HANDLER_STORE;
import static com.jiqu.community.constant.Constant.BLOOM_FILTER_HANDLER_DROP;


@Slf4j
@Order(1)
@Component
public class BloomFilterDataInit {
    
    @Resource
    private IStoreService storeService;
    
    @Resource
    private ILimitedDropService limitedDropService;
    
    @Resource
    private BloomFilterHandlerFactory bloomFilterHandlerFactory;

    @PostConstruct
    public void init() {
        log.info("==========初始化潮流店铺的布隆过滤器==========");
        List<Store> storeList = storeService.list();
        for (Store store : storeList) {
            bloomFilterHandlerFactory.get(BLOOM_FILTER_HANDLER_STORE).add(String.valueOf(store.getId()));
        }
        log.info("==========初始化优惠券的布隆过滤器==========");
        List<LimitedDrop> limitedDroplist = limitedDropService.list();
        for (LimitedDrop limitedDrop : limitedDroplist) {
            bloomFilterHandlerFactory.get(BLOOM_FILTER_HANDLER_DROP).add(String.valueOf(limitedDrop.getDropId()));
        }
    }
}
