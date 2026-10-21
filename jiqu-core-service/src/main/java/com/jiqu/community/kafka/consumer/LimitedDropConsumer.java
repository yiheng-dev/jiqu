package com.jiqu.community.kafka.consumer;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.consumer.AbstractConsumerHandler;
import com.jiqu.community.core.RedisKeyManage;
import com.jiqu.community.enums.BaseCode;
import com.jiqu.community.enums.BusinessType;
import com.jiqu.community.enums.LogType;
import com.jiqu.community.enums.LimitedDropOrderOperate;
import com.jiqu.community.exception.JiquFrameException;
import com.jiqu.community.kafka.message.LimitedDropMessage;
import com.jiqu.community.kafka.redis.RedisDropData;
import com.jiqu.community.message.MessageExtend;
import com.jiqu.community.model.LimitedDropFullModel;
import com.jiqu.community.redis.RedisCache;
import com.jiqu.community.redis.RedisKeyBuild;
import com.jiqu.community.service.IAutoIssueNotifyService;
import com.jiqu.community.service.ILimitedDropService;
import com.jiqu.community.service.IDropOrderService;
import com.jiqu.community.service.IDropReconcileLogService;
import com.jiqu.community.toolkit.SnowflakeIdGenerator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jiqu.community.constant.Constant.LIMITED_DROP_TOPIC;
import static com.jiqu.community.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;




@Slf4j
@Component
public class LimitedDropConsumer extends AbstractConsumerHandler<LimitedDropMessage> {
    
    public static Long MESSAGE_DELAY_TIME = 10000L;
    
    @Resource
    private IDropOrderService dropOrderService;
    
    @Resource
    private RedisDropData redisDropData;
    
    @Resource
    private RedisCache redisCache;
    
    @Resource
    private ILimitedDropService limitedDropService;
    
    @Resource
    private IDropReconcileLogService dropReconcileLogService;
     
    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;
    
    
    @Resource
    private IAutoIssueNotifyService autoIssueNotifyService;
    
    
    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();
    private static final int EXECUTOR_THREADS = Math.max(2, CPU_CORES);
    private static final int EXECUTOR_QUEUE_CAPACITY = 1024 * Math.max(1, CPU_CORES);
    
    private static final ThreadPoolExecutor DROP_ORDER_CONSUME_TASK_EXECUTOR =
            new ThreadPoolExecutor(
                    EXECUTOR_THREADS,
                    EXECUTOR_THREADS,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(EXECUTOR_QUEUE_CAPACITY),
                    new NamedThreadFactory("drop-order-consume-task", false),
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );
    
    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final boolean daemon;
        private final AtomicInteger index = new AtomicInteger(1);
        
        public NamedThreadFactory(String namePrefix, boolean daemon) {
            this.namePrefix = namePrefix;
            this.daemon = daemon;
        }
        
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + index.getAndIncrement());
            t.setDaemon(daemon);
            t.setUncaughtExceptionHandler((thread, ex) ->
                    log.error("未捕获异常，线程={}, err={}", thread.getName(), ex.getMessage(), ex)
            );
            return t;
        }
    }
    
    
    public LimitedDropConsumer() {
        super(LimitedDropMessage.class);
    }
    
   
    @KafkaListener(
            topics = {SPRING_INJECT_PREFIX_DISTINCTION_NAME + "-" + LIMITED_DROP_TOPIC}
    )
    public void onMessage(String value,
                          @Headers Map<String, Object> headers,
                          @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
                          Acknowledgment acknowledgment) {
        consumeRaw(value, key, headers);
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }
    
    @Override
    protected Boolean beforeConsume(MessageExtend<LimitedDropMessage> message) {
        long producerTimeTimestamp = message.getProducerTime().getTime();
        long delayTime = System.currentTimeMillis() - producerTimeTimestamp;
        //如果消息超时时间达到了阈值（10秒）
        if (delayTime > MESSAGE_DELAY_TIME){
            log.info("消费到kafka的创建优惠券消息延迟时间大于了 {} 毫秒 此订单消息被丢弃 订单号 : {}",
                    delayTime,message.getMessageBody().getOrderId());
            long traceId = snowflakeIdGenerator.nextId();
            redisDropData.rollbackRedisDropData(
                    LimitedDropOrderOperate.YES,
                    traceId,
                    message.getMessageBody().getDropId(),
                    message.getMessageBody().getUserId(),
                    message.getMessageBody().getOrderId(),
                    // 这是回滚操作，所以redis中扣减前和扣减后的数量要和消息中的反过来
                    message.getMessageBody().getAfterQty(),
                    message.getMessageBody().getChangeQty(),
                    message.getMessageBody().getBeforeQty()
            );
            try {
                dropReconcileLogService.saveReconcileLog(LogType.RESTORE.getCode(), 
                        BusinessType.TIMEOUT.getCode(), 
                        "message delayed " + delayTime + "ms, rollback redis", 
                        traceId,
                        message);
            } catch (Exception e) {
                log.warn("保存对账日志失败(延迟丢弃)", e);
            }
            return false;
        }
        return true;
    }
    
    @Override
    protected void doConsume(MessageExtend<LimitedDropMessage> message) {
        dropOrderService.createDropOrderV2(message);
    }
    
    @Override
    protected void afterConsumeSuccess(MessageExtend<LimitedDropMessage> message) {
        super.afterConsumeSuccess(message);
        LimitedDropMessage messageBody = message.getMessageBody();
        Long userId = messageBody.getUserId();
        Long dropId = messageBody.getDropId();
        Long orderId = messageBody.getOrderId();
        DROP_ORDER_CONSUME_TASK_EXECUTOR.execute(() -> {
            try {
                RedisKeyBuild subscribeZSetKey = RedisKeyBuild.createRedisKey(
                        RedisKeyManage.DROP_SUBSCRIBE_ZSET_TAG_KEY,
                        messageBody.getDropId()
                );
                redisCache.delForSortedSet(subscribeZSetKey, String.valueOf(userId));
            } catch (Exception e) {
                log.warn("清理订阅ZSET成员失败，dropId={}, userId={}, err={}", messageBody.getDropId(), userId, e.getMessage());
            }
            if (Boolean.TRUE.equals(messageBody.getAutoIssue())) {
                try {
                    autoIssueNotifyService.sendAutoIssueNotify(dropId, userId, orderId);
                } catch (Exception e) {
                    log.warn("自动发券通知发送失败，dropId={}, userId={}, orderId={}, err={}",
                            dropId, userId, orderId, e.getMessage());
                }
            }
            try {
                LimitedDropFullModel dropFull = limitedDropService.queryByDropId(dropId);
                if (Objects.isNull(dropFull)) {
                    return;
                }
                Long storeId = dropFull.getStoreId();
                // yyyyMMdd
                String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
                RedisKeyBuild dailyKey = RedisKeyBuild.createRedisKey(
                        RedisKeyManage.DROP_STORE_TOP_BUYERS_DAILY_TAG_KEY,
                        storeId,
                        day
                );
                redisCache.incrementScoreForSortedSet(dailyKey, String.valueOf(userId), 1.0);
                Long ttl = redisCache.getExpire(dailyKey, TimeUnit.SECONDS);
                if (ttl == null || ttl < 0) {
                    redisCache.expire(dailyKey, 90, TimeUnit.DAYS);
                }
            } catch (Exception e) {
                log.warn("统计潮流店铺Top买家失败，忽略不影响主流程", e);
            }
        });
    }
    
    @Override
    protected void afterConsumeFailure(final MessageExtend<LimitedDropMessage> message, 
                                       final Throwable throwable) {
        super.afterConsumeFailure(message, throwable);
        LimitedDropOrderOperate limitedDropOrderOperate = LimitedDropOrderOperate.YES;
        if (throwable instanceof JiquFrameException jiquFrameException) {
            if (Objects.nonNull(jiquFrameException.getCode()) && 
                    jiquFrameException.getCode().equals(BaseCode.DROP_ORDER_EXIST.getCode())){
                limitedDropOrderOperate = LimitedDropOrderOperate.NO;
            }
        }
        long traceId = snowflakeIdGenerator.nextId();
        redisDropData.rollbackRedisDropData(
                limitedDropOrderOperate,
                traceId,
                message.getMessageBody().getDropId(),
                message.getMessageBody().getUserId(),
                message.getMessageBody().getOrderId(),
                message.getMessageBody().getAfterQty(),
                message.getMessageBody().getChangeQty(),
                message.getMessageBody().getBeforeQty()
        );
        try {
            String detail = throwable == null ? "consume failed" : ("consume failed: " + throwable.getMessage());
            dropReconcileLogService.saveReconcileLog(LogType.RESTORE.getCode(),
                    BusinessType.FAIL.getCode(), 
                    detail,
                    traceId,
                    message
            );
        } catch (Exception e) {
            log.warn("保存对账日志失败(消费失败)", e);
        }
    }
}
