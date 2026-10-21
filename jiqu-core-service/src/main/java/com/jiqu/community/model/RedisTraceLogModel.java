package com.jiqu.community.model;

import lombok.Data;


@Data
public class RedisTraceLogModel {

    private String logType;
    
    private Long ts;
    
    private String orderId;
    
    private String traceId;
    
    private String userId;
    
    private String dropId;
    
    private Integer beforeQty;
    
    private Integer changeQty;
    
    private Integer afterQty;
}
