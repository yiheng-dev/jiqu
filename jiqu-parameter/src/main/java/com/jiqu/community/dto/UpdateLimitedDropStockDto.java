package com.jiqu.community.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;


@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class UpdateLimitedDropStockDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 优惠券id
     */
    @NotNull
    private Long dropId;
    
    /**
     * 初始库存
     * */
    @Min(1)
    @NotNull
    private Integer initStock;
}
