package com.jiqu.community.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import com.jiqu.community.dto.Result;
import com.jiqu.community.entity.Store;
import com.jiqu.community.service.IStoreService;
import com.jiqu.community.utils.SystemConstants;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/store")
public class StoreController {

    @Resource
    public IStoreService storeService;

    /**
     * 根据id查询潮流店铺信息
     * @param id 潮流店铺id
     * @return 潮流店铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryStoreById(@PathVariable("id") Long id) {
        return storeService.queryById(id);
    }

    /**
     * 新增潮流店铺信息
     * @param store 潮流店铺数据
     * @return 潮流店铺id
     */
    @PostMapping
    public Result saveStore(@RequestBody Store store) {
        return storeService.saveStore(store);
    }

    /**
     * 更新潮流店铺信息
     * @param store 潮流店铺数据
     * @return 无
     */
    @PutMapping
    public Result updateStore(@RequestBody Store store) {
        // 写入数据库
        return storeService.update(store);
    }

    /**
     * 根据潮流店铺类型分页查询潮流店铺信息
     * @param typeId 潮流店铺类型
     * @param current 页码
     * @return 潮流店铺列表
     */
    @GetMapping("/of/type")
    public Result queryStoreByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ) {
       return storeService.queryStoreByType(typeId, current, x, y);
    }

    /**
     * 根据潮流店铺名称关键字分页查询潮流店铺信息
     * @param name 潮流店铺名称关键字
     * @param current 页码
     * @return 潮流店铺列表
     */
    @GetMapping("/of/name")
    public Result queryStoreByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Store> page = storeService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
