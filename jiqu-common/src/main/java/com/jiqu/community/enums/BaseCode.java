package com.jiqu.community.enums;

import lombok.Getter;


public enum BaseCode {
    /**
     * 基础code码
     * */
    SUCCESS(0, "OK"),
    
    LIMITED_DROP_NOT_EXIST(10001, "发售优惠券不存在"),
    
    LIMITED_DROP_NOT_BEGIN(10002, "发售优惠券未开始"),
    
    LIMITED_DROP_IS_OVER(10003, "发售优惠券已结束"),
    
    LIMITED_DROP_STOCK_NOT_EXIST(10004, "发售优惠券库存不存在"),
    
    LIMITED_DROP_STOCK_INSUFFICIENT(10005, "发售优惠券库存不足"),
    
    LIMITED_DROP_CLAIM(10006, "发售优惠券已领取"),
    
    DROP_RATE_LIMIT_IP_EXCEEDED(10007, "请求过于频繁，请稍后再试"),
    
    DROP_RATE_LIMIT_USER_EXCEEDED(10008, "操作过于频繁，请稍后再试"),
    
    LIMITED_DROP_ORDER_NOT_EXIST(10009, "优惠券订单不存在"),
    
    AFTER_LIMITED_DROP_REMAIN_STOCK_NOT_NEGATIVE_NUMBER(10010,"修改后的剩余库存不能为负数"),
    
    DROP_UNAVAILABLE(10011,"优惠券已下架"),
    
    DROP_EXPIRED(10012,"优惠券已过期"),
    
    DROP_ORDER_EXIST(10013,"优惠券订单已存在"),
    
    DROP_ORDER_CANCEL(10014,"优惠券订单已取消"),
    
    USER_NOT_EXIST(20000, "用户不存在"),
    
    USER_ALREADY_PURCHASE(20001, "用户已经购买"),
    ;
    
    @Getter
    private final Integer code;
    
    private String msg = "";
    
    BaseCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }
    
    public static String getMsg(Integer code) {
        for (BaseCode re : BaseCode.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re.msg;
            }
        }
        return "";
    }
    
    public static BaseCode getRc(Integer code) {
        for (BaseCode re : BaseCode.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re;
            }
        }
        return null;
    }
}
