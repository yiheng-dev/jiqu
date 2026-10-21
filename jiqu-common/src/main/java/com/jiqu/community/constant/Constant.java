package com.jiqu.community.constant;


public class Constant {
    
    public static final String PREFIX_DISTINCTION_NAME = "prefix.distinction.name";
    
    public static final String DEFAULT_PREFIX_DISTINCTION_NAME = "jiqu";
    
    public static final String SPRING_INJECT_PREFIX_DISTINCTION_NAME = "${"+PREFIX_DISTINCTION_NAME+":"+DEFAULT_PREFIX_DISTINCTION_NAME+"}";
    
    public static final String LIMITED_DROP_TOPIC = "limited_drop_topic";
    
    public static final String LIMITED_DROP_CACHE_INVALIDATION_TOPIC = "limited_drop_cache_invalidation_topic";
    
    public static final String BLOOM_FILTER_HANDLER_STORE = "store";
    
    public static final String BLOOM_FILTER_HANDLER_DROP = "drop";
    
    public static final String DELAY_DROP_REMINDER ="h_delay_drop_reminder";
}
