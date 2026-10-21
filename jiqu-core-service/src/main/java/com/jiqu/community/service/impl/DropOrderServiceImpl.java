package com.jiqu.community.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.core.RedisKeyManage;
import com.jiqu.community.core.SpringUtil;
import com.jiqu.community.dto.CancelDropOrderDto;
import com.jiqu.community.dto.GetDropOrderByDropIdDto;
import com.jiqu.community.dto.GetDropOrderDto;
import com.jiqu.community.dto.Result;
import com.jiqu.community.dto.DropReconcileLogDto;
import com.jiqu.community.entity.LimitedDrop;
import com.jiqu.community.entity.Drop;
import com.jiqu.community.entity.DropOrder;
import com.jiqu.community.enums.BaseCode;
import com.jiqu.community.enums.BusinessType;
import com.jiqu.community.enums.LogType;
import com.jiqu.community.enums.OrderStatus;
import com.jiqu.community.enums.LimitedDropOrderOperate;
import com.jiqu.community.exception.JiquFrameException;
import com.jiqu.community.kafka.message.LimitedDropMessage;
import com.jiqu.community.kafka.producer.LimitedDropProducer;
import com.jiqu.community.kafka.redis.RedisDropData;
import com.jiqu.community.lua.LimitedDropDomain;
import com.jiqu.community.lua.LimitedDropOperate;
import com.jiqu.community.mapper.DropOrderMapper;
import com.jiqu.community.message.MessageExtend;
import com.jiqu.community.model.LimitedDropFullModel;
import com.jiqu.community.redis.RedisCacheImpl;
import com.jiqu.community.redis.RedisKeyBuild;
import com.jiqu.community.repeatexecutelimit.annotion.RepeatExecuteLimit;
import com.jiqu.community.service.ILimitedDropService;
import com.jiqu.community.service.IDropOrderService;
import com.jiqu.community.service.IDropReconcileLogService;
import com.jiqu.community.service.IDropService;
import com.jiqu.community.toolkit.SnowflakeIdGenerator;
import com.jiqu.community.utils.RedisIdWorker;
import com.jiqu.community.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.jiqu.community.constant.Constant.LIMITED_DROP_TOPIC;
import static com.jiqu.community.constant.RepeatExecuteLimitConstants.LIMITED_DROP_ORDER;


@Slf4j
@Service
public class DropOrderServiceImpl extends ServiceImpl<DropOrderMapper, DropOrder> implements IDropOrderService {

    @Resource
    private IDropService dropService;
    
    @Resource
    private ILimitedDropService limitedDropService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;
    
    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;
    
    @Resource
    private LimitedDropOperate limitedDropOperate;
    
    @Resource
    private LimitedDropProducer limitedDropProducer;
    
    @Resource
    private RedisCacheImpl redisCache;
    
    @Resource
    private DropOrderMapper dropOrderMapper;

    @Resource
    private RedisDropData redisDropData;
    
    @Resource
    private IDropReconcileLogService dropReconcileLogService;
    

    private static final DefaultRedisScript<Long> DROP_SCRIPT;

    static {
        DROP_SCRIPT = new DefaultRedisScript<>();
        DROP_SCRIPT.setLocation(new ClassPathResource("drop.lua"));
        DROP_SCRIPT.setResultType(Long.class);
    }

    public static final ThreadPoolExecutor DROP_ORDER_EXECUTOR =
            new ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(1024),
                    new NamedThreadFactory("drop-order-", false),
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
    
    
    @PostConstruct
    private void init(){
        // 这是集趣·潮流社区的普通版本，升级版本中不再使用此方式
        //DROP_ORDER_EXECUTOR.submit(new DropOrderHandler());
    }

    @PreDestroy
    private void destroy(){
        try {
            DROP_ORDER_EXECUTOR.shutdown();
            if (!DROP_ORDER_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                DROP_ORDER_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            DROP_ORDER_EXECUTOR.shutdownNow();
        }
    }
    
    
    private class DropOrderHandler implements Runnable{
        private final String queueName = "stream.orders";
        @Override
        public void run() {
            while (true) {
                try {
                    // 0.初始化stream
                    initStream();
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有消息，继续下一次循环
                        continue;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    DropOrder dropOrder = BeanUtil.fillBeanWithMap(value, new DropOrder(), true);
                    // 3.创建订单
                    handleDropOrder(dropOrder);
                    // 4.确认消息 XACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }

        public void initStream(){
            Boolean exists = stringRedisTemplate.hasKey(queueName);
            if (BooleanUtil.isFalse(exists)) {
                log.info("stream不存在，开始创建stream");
                // 不存在，需要创建
                stringRedisTemplate.opsForStream().createGroup(queueName, ReadOffset.latest(), "g1");
                log.info("stream和group创建完毕");
                return;
            }
            // stream存在，判断group是否存在
            StreamInfo.XInfoGroups groups = stringRedisTemplate.opsForStream().groups(queueName);
            if(groups.isEmpty()){
                log.info("group不存在，开始创建group");
                // group不存在，创建group
                stringRedisTemplate.opsForStream().createGroup(queueName, ReadOffset.latest(), "g1");
                log.info("group创建完毕");
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS s1 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有消息，继续下一次循环
                        break;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    DropOrder dropOrder = BeanUtil.fillBeanWithMap(value, new DropOrder(), true);
                    // 3.创建订单
                    handleDropOrder(dropOrder);
                    // 4.确认消息 XACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    private void handleDropOrder(DropOrder dropOrder) {
        Long userId = dropOrder.getId();
        // 创建锁对象
        // SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock();
        // 判断是否获取锁成功
        if(!isLock){
            // 获取锁失败，返回错误或重试
            log.error("不允许重复下单");
            return;
        }
        try {
            // 获取代理对象（事务）
            createDropOrderV1(dropOrder);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    IDropOrderService proxy;
    /**
     * 抢优惠券下单
     * */
    @Override
    public Result<Long> limitedDrop(Long dropId) {
        //return doLimitedDropV1(dropId);
        return doLimitedDropV2(dropId);
    }
    
    public Result<Long> doLimitedDropV1(Long dropId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = snowflakeIdGenerator.nextId();
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                DROP_SCRIPT,
                Collections.emptyList(),
                dropId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 3.获取代理对象
        proxy = (IDropOrderService) AopContext.currentProxy();
        // 4.返回订单id
        return Result.ok(orderId);
    }
    
    public Result<Long> doLimitedDropV2(Long dropId) {
        LimitedDropFullModel limitedDropFullModel = limitedDropService.queryByDropId(dropId);
        limitedDropService.loadDropStock(dropId);
        Long userId = UserHolder.getUser().getId();
        long orderId = snowflakeIdGenerator.nextId();
        long traceId = snowflakeIdGenerator.nextId();
        List<String> keys = ListUtil.of(
                RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_STOCK_TAG_KEY, dropId).getRelKey(),
                RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_USER_TAG_KEY, dropId).getRelKey(),
                RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_TRACE_LOG_TAG_KEY, dropId).getRelKey()
        );
        String[] args = new String[9];
        args[0] = dropId.toString();
        args[1] = userId.toString();
        args[2] = String.valueOf(LocalDateTimeUtil.toEpochMilli(limitedDropFullModel.getBeginTime()));
        args[3] = String.valueOf(LocalDateTimeUtil.toEpochMilli(limitedDropFullModel.getEndTime()));
        args[4] = String.valueOf(limitedDropFullModel.getStatus());
        args[5] = String.valueOf(orderId);
        args[6] = String.valueOf(traceId);
        args[7] = String.valueOf(LogType.DEDUCT.getCode());
        long secondsUntilEnd = Duration.between(LocalDateTimeUtil.now(), limitedDropFullModel.getEndTime()).getSeconds();
        long ttlSeconds = Math.max(1L, secondsUntilEnd + Duration.ofDays(1).getSeconds());
        args[8] = String.valueOf(ttlSeconds);
        LimitedDropDomain limitedDropDomain = limitedDropOperate.execute(
                keys,
                args
        );
        if (!limitedDropDomain.getCode().equals(BaseCode.SUCCESS.getCode())) {
            throw new JiquFrameException(Objects.requireNonNull(BaseCode.getRc(limitedDropDomain.getCode())));
        }
        LimitedDropMessage limitedDropMessage = new LimitedDropMessage(
                userId,
                dropId,
                orderId,
                traceId,
                limitedDropDomain.getBeforeQty(),
                limitedDropDomain.getDeductQty(),
                limitedDropDomain.getAfterQty(),
                Boolean.FALSE
        );
        limitedDropProducer.sendPayload(
                SpringUtil.getPrefixDistinctionName() + "-" + LIMITED_DROP_TOPIC, 
                limitedDropMessage);
        
        return Result.ok(orderId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createDropOrderV1(DropOrder dropOrder) {
        // 5.一人一单
        Long userId = dropOrder.getUserId();

        // 5.1.查询订单
        Long count = query().eq("user_id", userId).eq("drop_id", dropOrder.getDropId()).count();
        // 5.2.判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            log.error("用户已经购买过一次！");
            return;
        }
        // 6.扣减库存
        boolean success = limitedDropService.update()
                // set stock = stock - 1
                .setSql("stock = stock - 1")
                // where id = ? and stock > 0
                .eq("drop_id", dropOrder.getDropId()).gt("stock", 0) 
                .update();
        if (!success) {
            // 扣减失败
            log.error("库存不足！");
            return;
        }
        // 7.创建订单
        save(dropOrder);
    }
    
    
    @Override
    @RepeatExecuteLimit(name = LIMITED_DROP_ORDER,keys = {"#message.uuid"})
    @Transactional(rollbackFor = Exception.class)
    public boolean createDropOrderV2(MessageExtend<LimitedDropMessage> message) {
        LimitedDropMessage messageBody = message.getMessageBody();
        Long userId = messageBody.getUserId();
        DropOrder normalDropOrder = lambdaQuery()
                .eq(DropOrder::getDropId, messageBody.getDropId())
                .eq(DropOrder::getUserId, userId)
                .eq(DropOrder::getStatus,OrderStatus.NORMAL.getCode())
                .one();
        if (Objects.nonNull(normalDropOrder)) {
            log.warn("已存在此订单，dropId：{},userId：{}", normalDropOrder.getDropId(), userId);
            throw new JiquFrameException(BaseCode.DROP_ORDER_EXIST);
        }
        boolean success = limitedDropService.update()
                .setSql("stock = stock - 1")
                .eq("drop_id", messageBody.getDropId())
                .gt("stock", 0)
                .update();
        if (!success) {
            throw new JiquFrameException("优惠券库存不足！优惠券id:" + messageBody.getDropId());
        }
        DropOrder dropOrder = new DropOrder();
        dropOrder.setId(messageBody.getOrderId());
        dropOrder.setUserId(messageBody.getUserId());
        dropOrder.setDropId(messageBody.getDropId());
        dropOrder.setCreateTime(LocalDateTimeUtil.now());
        save(dropOrder);
        redisCache.set(RedisKeyBuild.createRedisKey(
                RedisKeyManage.DB_DROP_ORDER_KEY,messageBody.getOrderId()),
                dropOrder,
                60, 
                TimeUnit.SECONDS
        );
        dropReconcileLogService.saveReconcileLog(
                LogType.DEDUCT.getCode(),
                BusinessType.SUCCESS.getCode(),
                "order created",
                message
        );
        return true;
    }
    
    @Override
    public Long getLimitedDropOrder(GetDropOrderDto getDropOrderDto) {
        DropOrder dropOrder = 
                redisCache.get(RedisKeyBuild.createRedisKey(
                        RedisKeyManage.DB_DROP_ORDER_KEY, 
                        getDropOrderDto.getOrderId()), 
                        DropOrder.class);
        if (Objects.nonNull(dropOrder)) {
            return dropOrder.getId();
        }
        DropOrder dbOrder = lambdaQuery()
                .eq(DropOrder::getId, getDropOrderDto.getOrderId())
                .one();
        if (Objects.nonNull(dbOrder)) {
            return dbOrder.getId();
        }
        return null;
    }
    
    @Override
    public Long getLimitedDropOrderIdByDropId(GetDropOrderByDropIdDto getDropOrderByDropIdDto) {
        DropOrder dropOrder = lambdaQuery()
                .eq(DropOrder::getUserId, UserHolder.getUser().getId())
                .eq(DropOrder::getDropId, getDropOrderByDropIdDto.getDropId())
                .eq(DropOrder::getStatus, OrderStatus.NORMAL.getCode())
                .one();
        if (Objects.nonNull(dropOrder)) {
            return dropOrder.getId();
        }
        return null;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean cancel(CancelDropOrderDto cancelDropOrderDto) {
        DropOrder dropOrder = lambdaQuery()
                .eq(DropOrder::getUserId, UserHolder.getUser().getId())
                .eq(DropOrder::getDropId, cancelDropOrderDto.getDropId())
                .eq(DropOrder::getStatus, OrderStatus.NORMAL.getCode())
                .one();
        if (Objects.isNull(dropOrder)) {
            throw new JiquFrameException(BaseCode.LIMITED_DROP_ORDER_NOT_EXIST);
        }
        LimitedDrop limitedDrop = limitedDropService.lambdaQuery()
                .eq(LimitedDrop::getDropId, cancelDropOrderDto.getDropId())
                .one();
        if (Objects.isNull(limitedDrop)) {
            throw new JiquFrameException(BaseCode.LIMITED_DROP_NOT_EXIST);
        }
        boolean updateResult = lambdaUpdate().set(DropOrder::getStatus, OrderStatus.CANCEL.getCode())
                .set(DropOrder::getUpdateTime, LocalDateTimeUtil.now())
                .eq(DropOrder::getUserId, UserHolder.getUser().getId())
                .eq(DropOrder::getDropId, cancelDropOrderDto.getDropId())
                .update();
        long traceId = snowflakeIdGenerator.nextId();
        DropReconcileLogDto dropReconcileLogDto = new DropReconcileLogDto();
        dropReconcileLogDto.setOrderId(dropOrder.getId());
        dropReconcileLogDto.setUserId(dropOrder.getUserId());
        dropReconcileLogDto.setDropId(dropOrder.getDropId());
        dropReconcileLogDto.setDetail("cancel drop order ");
        dropReconcileLogDto.setBeforeQty(limitedDrop.getStock());
        dropReconcileLogDto.setChangeQty(1);
        dropReconcileLogDto.setAfterQty(limitedDrop.getStock() + 1);
        dropReconcileLogDto.setTraceId(traceId);
        dropReconcileLogDto.setLogType(LogType.RESTORE.getCode());
        dropReconcileLogDto.setBusinessType( BusinessType.CANCEL.getCode());
        boolean saveReconcileLogResult = dropReconcileLogService.saveReconcileLog(dropReconcileLogDto);
        
        boolean rollbackStockResult = limitedDropService.rollbackStock(cancelDropOrderDto.getDropId());
        
        Boolean result = updateResult && saveReconcileLogResult && rollbackStockResult;
        if (result) {
            redisDropData.rollbackRedisDropData(
                    LimitedDropOrderOperate.YES,
                    traceId,
                    dropOrder.getDropId(),
                    dropOrder.getUserId(),
                    dropOrder.getId(),
                    limitedDrop.getStock(),
                    1,
                    limitedDrop.getStock() + 1
            );
            redisCache.delForHash(RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_SUBSCRIBE_STATUS_TAG_KEY, 
                    cancelDropOrderDto.getDropId()),
                    String.valueOf(dropOrder.getUserId()));
            Drop drop = dropService.getById(dropOrder.getDropId());
            if (Objects.nonNull(drop)) {
                String day = dropOrder.getCreateTime().format(DateTimeFormatter.BASIC_ISO_DATE);
                RedisKeyBuild dailyKey = RedisKeyBuild.createRedisKey(
                        RedisKeyManage.DROP_STORE_TOP_BUYERS_DAILY_TAG_KEY,
                        drop.getStoreId(),
                        day
                );
                redisCache.incrementScoreForSortedSet(dailyKey, String.valueOf(dropOrder.getUserId()), -1.0);
            }
            
            try {
                autoIssueDropToEarliestSubscriber(
                        dropOrder.getDropId(), 
                        dropOrder.getUserId()
                );
            } catch (Exception e) {
                log.warn("自动发券失败，dropId={}, err=\n{}", dropOrder.getDropId(), e.getMessage());
            }
        }
        return result;
    }
    
    @Override
    public boolean autoIssueDropToEarliestSubscriber(final Long dropId, final Long excludeUserId) {
        LimitedDropFullModel limitedDropFullModel = limitedDropService.queryByDropId(dropId);
        if (Objects.isNull(limitedDropFullModel) 
                || 
                Objects.isNull(limitedDropFullModel.getBeginTime()) 
                ||
                Objects.isNull(limitedDropFullModel.getEndTime())) {
            return false;
        }
        limitedDropService.loadDropStock(dropId);
        String candidateUserIdStr = findEarliestCandidate(dropId, excludeUserId);
        if (StrUtil.isBlank(candidateUserIdStr)) {
            return false;
        }
        return issueToCandidate(dropId, candidateUserIdStr, limitedDropFullModel);
    }
    
    private String findEarliestCandidate(final Long dropId, final Long excludeUserId) {
        RedisKeyBuild subscribeZSetKey = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_SUBSCRIBE_ZSET_TAG_KEY, dropId);
        RedisKeyBuild purchasedSetKey = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_USER_TAG_KEY, dropId);
        
        final long pageCount = 1L;
        long offset = 0L;
        while (true) {
            Set<ZSetOperations.TypedTuple<String>> page = redisCache.rangeByScoreWithScoreForSortedSet(
                    subscribeZSetKey,
                    Double.NEGATIVE_INFINITY,
                    Double.POSITIVE_INFINITY,
                    offset,
                    pageCount,
                    String.class
            );
            if (CollectionUtil.isEmpty(page)) {
                return null;
            }
            ZSetOperations.TypedTuple<String> tuple = page.iterator().next();
            if (Objects.isNull(tuple) || Objects.isNull(tuple.getValue())) {
                offset++;
                continue;
            }
            String uidStr = tuple.getValue();
            if (StrUtil.isBlank(uidStr)) {
                offset++;
                continue;
            }
            if (Objects.nonNull(excludeUserId) && Objects.equals(uidStr, String.valueOf(excludeUserId))) {
                offset++;
                continue;
            }
            Boolean purchased = redisCache.isMemberForSet(purchasedSetKey, uidStr);
            if (BooleanUtil.isTrue(purchased)) {
                offset++;
                continue;
            }
            return uidStr;
        }
    }
    
    private boolean issueToCandidate(final Long dropId, 
                                     final String candidateUserIdStr, 
                                     final LimitedDropFullModel limitedDropFullModel) {
        Long candidateUserId = Long.valueOf(candidateUserIdStr);
        List<String> keys = buildDropKeys(dropId);
        long orderId = snowflakeIdGenerator.nextId();
        long traceId = snowflakeIdGenerator.nextId();
        String[] args = buildDropArgs(dropId, candidateUserIdStr, limitedDropFullModel, orderId, traceId);
        LimitedDropDomain domain = limitedDropOperate.execute(keys, args);
        if (!Objects.equals(domain.getCode(), BaseCode.SUCCESS.getCode())) {
            log.info("自动发券Lua扣减失败，code={}, dropId={}, userId={}", domain.getCode(), dropId, candidateUserId);
            return false;
        }
        LimitedDropMessage message = new LimitedDropMessage(
                candidateUserId,
                dropId,
                orderId,
                traceId,
                domain.getBeforeQty(),
                domain.getDeductQty(),
                domain.getAfterQty(),
                Boolean.TRUE
        );
        limitedDropProducer.sendPayload(
                SpringUtil.getPrefixDistinctionName() + "-" + LIMITED_DROP_TOPIC,
                message
        );
        return true;
    }
    
    private List<String> buildDropKeys(final Long dropId) {
        String stockKey = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_STOCK_TAG_KEY, dropId).getRelKey();
        String userKey = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_USER_TAG_KEY, dropId).getRelKey();
        String traceKey = RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_TRACE_LOG_TAG_KEY, dropId).getRelKey();
        return ListUtil.of(stockKey, userKey, traceKey);
    }
    
    private String[] buildDropArgs(final Long dropId,
                                      final String userIdStr,
                                      final LimitedDropFullModel limitedDropFullModel,
                                      final long orderId,
                                      final long traceId) {
        String[] args = new String[9];
        args[0] = dropId.toString();
        args[1] = userIdStr;
        args[2] = String.valueOf(LocalDateTimeUtil.toEpochMilli(limitedDropFullModel.getBeginTime()));
        args[3] = String.valueOf(LocalDateTimeUtil.toEpochMilli(limitedDropFullModel.getEndTime()));
        args[4] = String.valueOf(limitedDropFullModel.getStatus());
        args[5] = String.valueOf(orderId);
        args[6] = String.valueOf(traceId);
        args[7] = String.valueOf(LogType.DEDUCT.getCode());
        args[8] = String.valueOf(computeTtlSeconds(limitedDropFullModel));
        return args;
    }
    
    private long computeTtlSeconds(final LimitedDropFullModel limitedDropFullModel) {
        long secondsUntilEnd = Duration.between(LocalDateTimeUtil.now(), limitedDropFullModel.getEndTime()).getSeconds();
        return Math.max(1L, secondsUntilEnd + Duration.ofDays(1).getSeconds());
    }

    /*
    private BlockingQueue<DropOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private class DropOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){
                try {
                    // 1.获取队列中的订单信息
                    DropOrder dropOrder = orderTasks.take();
                    // 2.创建订单
                    handleDropOrder(dropOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    @Override
    public Result limitedDrop(Long dropId) {
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                DROP_SCRIPT,
                Collections.emptyList(),
                dropId.toString(), userId.toString()
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.2.为0 ，有购买资格，把下单信息保存到阻塞队列
        DropOrder dropOrder = new DropOrder();
        // 2.3.订单id
        long orderId = redisIdWorker.nextId("order");
        dropOrder.setId(orderId);
        // 2.4.用户id
        dropOrder.setUserId(userId);
        // 2.5.代金券id
        dropOrder.setDropId(dropId);
        // 2.6.放入阻塞队列
        orderTasks.add(dropOrder);
        // 3.获取代理对象
        proxy = (IDropOrderService) AopContext.currentProxy()
        // 4.返回订单id
        return Result.ok(orderId);
    }*/
    /*@Override
    public Result limitedDrop(Long dropId) {
        // 1.查询优惠券
        LimitedDrop drop = limitedDropService.getById(dropId);
        // 2.判断发售是否开始
        if (drop.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("发售尚未开始！");
        }
        // 3.判断发售是否已经结束
        if (drop.getEndTime().isBefore(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("发售已经结束！");
        }
        // 4.判断库存是否充足
        if (drop.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }

        Long userId = UserHolder.getUser().getId();
        // 创建锁对象
        // SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock();
        // 判断是否获取锁成功
        if(!isLock){
            // 获取锁失败，返回错误或重试
            return Result.fail("不允许重复下单");
        }
        try {
            // 获取代理对象（事务）
            IDropOrderService proxy = (IDropOrderService) AopContext.currentProxy();
            return proxy.createDropOrder(dropId);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }*/


    /*@Transactional
    public Result createDropOrder(Long dropId) {
        // 5.一人一单
        Long userId = UserHolder.getUser().getId();

        synchronized (userId.toString().intern()) {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("drop_id", dropId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }

            // 6.扣减库存
            boolean success = limitedDropService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("drop_id", dropId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足！");
            }

            // 7.创建订单
            DropOrder dropOrder = new DropOrder();
            // 7.1.订单id
            long orderId = redisIdWorker.nextId("order");
            dropOrder.setId(orderId);
            // 7.2.用户id
            dropOrder.setUserId(userId);
            // 7.3.代金券id
            dropOrder.setDropId(dropId);
            save(dropOrder);

            // 7.返回订单id
            return Result.ok(orderId);
        }
    }*/

}
