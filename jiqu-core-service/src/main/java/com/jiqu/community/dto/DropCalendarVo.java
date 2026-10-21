package com.jiqu.community.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 发售日历视图对象
 */
@Data
public class DropCalendarVo implements Serializable {

    private Long dropId;

    private String title;

    private String subTitle;

    private LocalDateTime beginTime;

    private LocalDateTime endTime;

    private Integer initStock;

    private Integer stock;

    /**
     * ongoing-进行中 upcoming-即将发售
     */
    private String status;
}
