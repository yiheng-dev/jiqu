package com.jiqu.community.core;


import lombok.Getter;


@Getter
public enum RedisKeyManage {
    /**
     * key信息，关于key中为什么使用{}大括号，在星球中有详细的讲解：<a href="https://articles.zsxq.com/id_7k4qtb2mofst.html">...</a>
     * */
    USER_INFO_KEY("user:info:%s","用户id","value为UserInfo类型","k"),
   
    CACHE_STORE_KEY("cache:store:%s","潮流店铺id","value为Store类型","k"),
 
    CACHE_STORE_KEY_NULL("cache:store_null:%s","潮流店铺id","value为这是空值","k"),
    
    DROP_STOCK_TAG_KEY("drop:stock:{%s}","限量发售id（同槽位HashTag）","value为库存","k"),
    
    DROP_USER_TAG_KEY("drop:user:{%s}","限量发售id（同槽位HashTag）","value为已下单用户集合","k"),
    
    LIMITED_DROP_TAG_KEY("drop:drop:{%s}","限量发售id（同槽位HashTag）","value为LimitedDrop类型","k"),
    
    LIMITED_DROP_NULL_TAG_KEY("drop:drop_null:{%s}","限量发售id（同槽位HashTag）","value为这是空值","k"),
    
    DROP_TRACE_LOG_TAG_KEY("drop:trace:log:{%s}","限量发售id（同槽位HashTag）","value为操作记录日志","k"),
    
    DROP_LIMIT_IP_TAG_KEY("drop:limit:ip:{%s}:%s","限量发售id（同槽位HashTag）","value为按IP的限流计数","k"),
    
    DROP_LIMIT_USER_TAG_KEY("drop:limit:user:{%s}:%s","限量发售id（同槽位HashTag）","value为按用户的限流计数","k"),
    
    DROP_LIMIT_IP_SW_TAG_KEY("drop:limit:ip:sw:{%s}:%s","限量发售id（同槽位HashTag）","value为按IP的滑动窗口计数","k"),
    
    DROP_LIMIT_USER_SW_TAG_KEY("drop:limit:user:sw:{%s}:%s","限量发售id（同槽位HashTag）","value为按用户的滑动窗口计数","k"),
    
    DROP_LIMIT_IP_TB_TAG_KEY("drop:limit:ip:tb:{%s}:%s","限量发售id（同槽位HashTag）","value为按IP的令牌桶HASH（tokens/last_ms）","k"),

    DROP_LIMIT_USER_TB_TAG_KEY("drop:limit:user:tb:{%s}:%s","限量发售id（同槽位HashTag）","value为按用户的令牌桶HASH（tokens/last_ms）","k"),
    
    DROP_BLOCK_IP_TAG_KEY("drop:block:ip:{%s}:%s","限量发售id（同槽位HashTag）","value为按IP的临时封禁标记","k"),
    
    DROP_BLOCK_USER_TAG_KEY("drop:block:user:{%s}:%s","限量发售id（同槽位HashTag）","value为按用户的临时封禁标记","k"),
    
    DROP_VIOLATION_IP_TAG_KEY("drop:violation:ip:{%s}:%s","限量发售id（同槽位HashTag）","value为按IP的违规计数","k"),
    
    DROP_VIOLATION_USER_TAG_KEY("drop:violation:user:{%s}:%s","限量发售id（同槽位HashTag）","value为按用户的违规计数","k"),
   
    DB_DROP_ORDER_KEY("db:drop:order:%s","限量发售的订单id","value为订单","k"),
    
    DROP_ROLLBACK_ALERT_DEDUP_KEY("drop:rollback:alert:dedup:%s","限量发售的id","value为1","k"),
    
    DROP_AUTO_ISSUE_NOTIFY_DEDUP_KEY("drop:autoissue:notify:dedup:{%s}:%s","限量发售id（同槽位HashTag）与用户id","value为1","k"),
    
    DROP_REMINDER_NOTIFY_DEDUP_KEY("drop:reminder:notify:dedup:{%s}:%s","限量发售id（同槽位HashTag）与用户id","value为1","k"),
    
    DROP_ACCESS_TOKEN_TAG_KEY("drop:access:token:{%s}:%s","限量发售id（同槽位HashTag）与用户id","访问令牌","k"),
    
    DROP_SUBSCRIBE_USER_TAG_KEY("drop:subscribe:user:{%s}","限量发售id（同槽位HashTag）","value为订阅用户集合","k"),
    
    DROP_SUBSCRIBE_ZSET_TAG_KEY("drop:subscribe:zset:{%s}","限量发售id（同槽位HashTag）","value为订阅队列，member为用户id，score为加入时间戳","k"),
    
    DROP_SUBSCRIBE_STATUS_TAG_KEY("drop:subscribe:status:{%s}","限量发售id（同槽位HashTag）","value为用户订阅状态HASH，field为用户id，value为状态码","k"),
    
    DROP_STORE_TOP_BUYERS_DAILY_TAG_KEY("drop:store:topbuyers:daily:{%s}:%s","潮流店铺id（同槽位HashTag）与日期(yyyyMMdd)","ZSET，member为用户id，score为购买次数","k"),
    
    DROP_STORE_TOP_BUYERS_UNION_TAG_KEY("drop:store:topbuyers:union:{%s}:%s","潮流店铺id（同槽位HashTag）与聚合范围","临时ZSET，member为用户id，score为购买次数合并","k"),

    ;

    /**
     * key值
     * */
    private final String key;

    /**
     * key的说明
     * */
    private final String keyIntroduce;

    /**
     * value的说明
     * */
    private final String valueIntroduce;

    /**
     * 作者
     * */
    private final String author;

    RedisKeyManage(String key, String keyIntroduce, String valueIntroduce, String author){
        this.key = key;
        this.keyIntroduce = keyIntroduce;
        this.valueIntroduce = valueIntroduce;
        this.author = author;
    }

    public static RedisKeyManage getRc(String keyCode) {
        for (RedisKeyManage re : RedisKeyManage.values()) {
            if (re.key.equals(keyCode)) {
                return re;
            }
        }
        return null;
    }
    
}
