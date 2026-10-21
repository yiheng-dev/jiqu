package com.jiqu.community.delay.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class DelayedDropReminderMessage {
    
    private Long dropId;
    
    private LocalDateTime beginTime;
}