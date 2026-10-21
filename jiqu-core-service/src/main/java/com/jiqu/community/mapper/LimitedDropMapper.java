package com.jiqu.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import com.jiqu.community.entity.LimitedDrop;


public interface LimitedDropMapper extends BaseMapper<LimitedDrop> {
   
    @Update("UPDATE tb_seckill_voucher SET stock = stock + 1,update_time = NOW() WHERE drop_id = #{dropId}")
    Integer rollbackStock(@Param("dropId")Long dropId);

}
