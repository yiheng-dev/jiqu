package com.jiqu.community.core;

import org.redisson.Redisson;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RReliableQueue;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.PlainOptions;
import org.redisson.client.codec.Codec;

import java.util.concurrent.TimeUnit;


public class DelayProduceQueue extends DelayBaseQueue{
    
    
    private final RDelayedQueue<String> delayedQueue;
    public DelayProduceQueue(RedissonClient redissonClient, final String relTopic) {
        super(redissonClient, relTopic);
        this.delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
    }
    
    public void offer(String content, long delayTime, TimeUnit timeUnit) {
        delayedQueue.offer(content,delayTime,timeUnit);
    }
}
