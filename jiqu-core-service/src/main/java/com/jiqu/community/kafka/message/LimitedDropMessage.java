package com.jiqu.community.kafka.message;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;



@Data
@AllArgsConstructor
@NoArgsConstructor
public class LimitedDropMessage {

    private Long userId;
    
    private Long dropId;
    
    private Long orderId;

    private Long traceId;

    private Integer beforeQty;
    
    private Integer changeQty;
    
    private Integer afterQty;
    
    private Boolean autoIssue;
}
