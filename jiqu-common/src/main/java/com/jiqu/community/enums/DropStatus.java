package com.jiqu.community.enums;

import lombok.Getter;


public enum DropStatus {
    /**
     * 优惠券状态 
     * */
    AVAILABLE(1, "上架"),
    UNAVAILABLE(2, "下架"),
    EXPIRED(3, "过期");
    
    ;
    
    @Getter
    private final Integer code;
    
    private String msg = "";
    
    DropStatus(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }
    
    public static String getMsg(Integer code) {
        for (DropStatus re : DropStatus.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re.msg;
            }
        }
        return "";
    }
    
    public static DropStatus getRc(Integer code) {
        for (DropStatus re : DropStatus.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re;
            }
        }
        return null;
    }
}
