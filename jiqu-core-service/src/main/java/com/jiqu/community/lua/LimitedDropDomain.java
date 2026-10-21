package com.jiqu.community.lua;

import lombok.Data;


@Data
public class LimitedDropDomain {

    private Integer code;
    
    private Integer beforeQty;
    
    private Integer deductQty;
    
    private Integer afterQty;

}
