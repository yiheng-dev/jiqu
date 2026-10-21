package com.jiqu.community.kafka.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class LimitedDropInvalidationMessage {
    
    private Long dropId;
    
    private String reason;
}