package com.jiqu.community.controller;


import jakarta.annotation.Resource;
import com.jiqu.community.dto.Result;
import com.jiqu.community.entity.StoreCategory;
import com.jiqu.community.service.IStoreCategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/store-category")
public class StoreCategoryController {
    @Resource
    private IStoreCategoryService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        List<StoreCategory> typeList = typeService
                .query().orderByAsc("sort").list();
        return Result.ok(typeList);
    }
}
