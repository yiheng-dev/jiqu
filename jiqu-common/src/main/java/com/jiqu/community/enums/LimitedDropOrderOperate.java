package com.jiqu.community.enums;

import lombok.Getter;


public enum LimitedDropOrderOperate {
    /**
     * 是否删除发售优惠券订单记录
     * */
    NO(0, "不删除"),
    YES(1, "删除"),
    ;
    
    @Getter
    private final Integer code;
    
    private String msg = "";
    
    LimitedDropOrderOperate(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }
    
    public static String getMsg(Integer code) {
        for (LimitedDropOrderOperate re : LimitedDropOrderOperate.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re.msg;
            }
        }
        return "";
    }
    
    public static LimitedDropOrderOperate getRc(Integer code) {
        for (LimitedDropOrderOperate re : LimitedDropOrderOperate.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re;
            }
        }
        return null;
    }
}
