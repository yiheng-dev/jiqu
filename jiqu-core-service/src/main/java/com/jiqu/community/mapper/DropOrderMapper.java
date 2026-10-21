package com.jiqu.community.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import com.jiqu.community.entity.DropOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;


public interface DropOrderMapper extends BaseMapper<DropOrder> {
    
    /**
     * 根据优惠券id和用户id删除数据
     * @param dropId 优惠券id
     * @param userId 用户id
     * @return 删除数量
     */
    @Delete("DELETE FROM tb_voucher_order where drop_id = #{dropId} and user_id = #{userId}")
    Integer deleteDropOrder(@Param("dropId")Long dropId, @Param("userId")Long userId);

}
