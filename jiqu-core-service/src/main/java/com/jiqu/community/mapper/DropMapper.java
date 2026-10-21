package com.jiqu.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiqu.community.entity.Drop;
import org.apache.ibatis.annotations.Param;

import java.util.List;


public interface DropMapper extends BaseMapper<Drop> {

    List<Drop> queryDropOfStore(@Param("storeId") Long storeId);
}
