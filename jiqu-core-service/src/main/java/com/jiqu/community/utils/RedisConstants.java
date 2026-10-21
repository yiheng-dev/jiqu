package com.jiqu.community.utils;


public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_STORE_TTL = 30L;
    public static final String CACHE_STORE_KEY = "cache:store:";

    public static final String LOCK_STORE_KEY = "lock:store:";
    public static final Long LOCK_STORE_TTL = 10L;
    
    public static final String LOCK_LIMITED_DROP_KEY = "lock_limited_drop";
    
    public static final String LOCK_LIMITED_DROP_STOCK_KEY = "lock_limited_drop_stock";

    public static final String DROP_STOCK_KEY = "drop:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String STORE_GEO_KEY = "store:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
