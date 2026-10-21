package com.jiqu.community.delay.consumer;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.core.ConsumerTask;
import com.jiqu.community.core.RedisKeyManage;
import com.jiqu.community.core.SpringUtil;
import com.jiqu.community.delay.message.DelayedDropReminderMessage;
import com.jiqu.community.model.LimitedDropFullModel;
import com.jiqu.community.redis.RedisCache;
import com.jiqu.community.redis.RedisKeyBuild;
import com.jiqu.community.service.ILimitedDropService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.jiqu.community.constant.Constant.DELAY_DROP_REMINDER;



@Slf4j
@Component
public class ConsumerDelayedDropReminder implements ConsumerTask {
    
    @Resource
    private RedisCache redisCache;
    
    @Resource
    private ILimitedDropService limitedDropService;

    @Value("${drop.reminder.notify.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${drop.reminder.notify.app.enabled:false}")
    private boolean appEnabled;

    @Value("${drop.reminder.notify.sms.to:}")
    private String smsTo;

    @Value("${drop.reminder.dedup.window.seconds:1800}")
    private long dedupWindowSeconds;

    /**
     * 是否附加通知“最近购买活跃用户”
     * */
    @Value("${drop.reminder.notify.top.buyers.enabled:true}")
    private boolean topBuyersEnabled;
    /**
     * 统计最近多少天的购买行为
     * */
    @Value("${drop.reminder.notify.top.buyers.days:30}")
    private int topBuyersDays;
    /**
     * Top购买用户数量
     * */
    @Value("${drop.reminder.notify.top.buyers.count:200}")
    private int topBuyersCount;

    @Override
    public void execute(final String content) {
        try {
            DelayedDropReminderMessage msg = parseMessage(content);
            if (Objects.isNull(msg)) { 
                return; 
            }
            Long dropId = msg.getDropId();
            LimitedDropFullModel dropFull = limitedDropService.queryByDropId(dropId);
            if (dropFull == null) {
                log.warn("[DELAY_REMINDER_CONSUMER] 限量发售不存在或缓存未命中 dropId={}", dropId);
                return;
            }
            Set<String> userIds = buildAudienceUserIds(dropFull);
            if (CollectionUtil.isEmpty(userIds)) {
                log.info("[DELAY_REMINDER_CONSUMER] 无符合规则的用户 dropId={}", dropId);
                return;
            }
            int notified = notifyUsers(dropId, msg.getBeginTime(), userIds);
            log.info("[DELAY_REMINDER_CONSUMER] 完成提醒 dropId={} totalUsers={} notified={}",
                    dropId, userIds.size(), notified);
        } catch (Exception e) {
            log.warn("[DELAY_REMINDER_CONSUMER] 执行异常", e);
        }
    }

    private DelayedDropReminderMessage parseMessage(String content) {
        try {
            DelayedDropReminderMessage msg = JSON.parseObject(content, DelayedDropReminderMessage.class);
            if (msg == null || msg.getDropId() == null) {
                log.warn("[DELAY_REMINDER_CONSUMER] 消息解析失败 content={}", content);
                return null;
            }
            return msg;
        } catch (Exception ex) {
            log.warn("[DELAY_REMINDER_CONSUMER] 消息反序列化异常 content={}", content, ex);
            return null;
        }
    }

    private Set<String> buildAudienceUserIds(LimitedDropFullModel dropFull) {
        Long storeId = dropFull.getStoreId();
        Set<String> userIds = new LinkedHashSet<>();
        if (topBuyersEnabled && Objects.nonNull(storeId)) {
            for (Long uid : readTopBuyersFromRedis(storeId, topBuyersCount, topBuyersDays)) {
                if (uid != null) {
                    userIds.add(String.valueOf(uid));
                }
            }
        } else if (topBuyersEnabled) {
            log.warn("[DELAY_REMINDER_CONSUMER] 潮流店铺ID为空，跳过Top买家统计");
        }
        return userIds;
    }

    private List<Long> readTopBuyersFromRedis(Long storeId, int count, int days) {
        try {
            LocalDate today = LocalDate.now();
            DateTimeFormatter fmt = DateTimeFormatter.BASIC_ISO_DATE;
            List<RedisKeyBuild> dailyKeys = new ArrayList<>();
            for (int i = 0; i < Math.max(days, 1); i++) {
                String day = today.minusDays(i).format(fmt);
                dailyKeys.add(RedisKeyBuild.createRedisKey(
                        RedisKeyManage.DROP_STORE_TOP_BUYERS_DAILY_TAG_KEY,
                        storeId,
                        day
                ));
            }
            if (dailyKeys.isEmpty()) { 
                return Collections.emptyList(); 
            }
            if (dailyKeys.size() == 1) {
                Set<Long> topSet = redisCache.getReverseRangeForSortedSet(
                        dailyKeys.get(0), 0, Math.max(count - 1, 0), Long.class);
                return new ArrayList<>(topSet);
            }
            String rangeLabel = today.minusDays(dailyKeys.size() - 1).format(fmt) + "-" + today.format(fmt);
            RedisKeyBuild destKey = RedisKeyBuild.createRedisKey(
                    RedisKeyManage.DROP_STORE_TOP_BUYERS_UNION_TAG_KEY,
                    storeId,
                    rangeLabel
            );
            RedisKeyBuild base = dailyKeys.get(0);
            Collection<RedisKeyBuild> others = dailyKeys.subList(1, dailyKeys.size());
            try {
                redisCache.unionAndStoreForSortedSet(base, others, destKey);
                redisCache.expire(destKey, 60, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("[DELAY_REMINDER_CONSUMER] ZSET并集失败 storeId={} range={}", storeId, rangeLabel, e);
            }
            Set<Long> topSet = redisCache.getReverseRangeForSortedSet(destKey, 0, Math.max(count - 1, 0), Long.class);
            return new ArrayList<>(topSet);
        } catch (Exception ex) {
            log.warn("[DELAY_REMINDER_CONSUMER] 读取Redis Top买家失败 storeId={} days={} count={} ex={}",
                    storeId, days, count, ex.getMessage());
            return Collections.emptyList();
        }
    }

    private int notifyUsers(Long dropId, LocalDateTime beginTime, Set<String> userIds) {
        int notifyCount = 0;
        for (String userIdStr : userIds) {
            if (StrUtil.isBlank(userIdStr)) { continue; }
            boolean shouldNotify;
            try {
                shouldNotify = redisCache.setIfAbsent(
                        RedisKeyBuild.createRedisKey(RedisKeyManage.DROP_REMINDER_NOTIFY_DEDUP_KEY, dropId, userIdStr),
                        "1",
                        dedupWindowSeconds,
                        java.util.concurrent.TimeUnit.SECONDS
                );
            } catch (Exception e) {
                shouldNotify = true;
            }
            if (!shouldNotify) { 
                continue; 
            }
            String notifyContent = String.format("[REMINDER] dropId=%s userId=%s beginTime=%s",
                    dropId, userIdStr, beginTime);
            if (smsEnabled && StrUtil.isNotBlank(smsTo)) {
                log.info("[REMINDER_SMS] to={} content={}", smsTo, notifyContent);
            }
            if (appEnabled) {
                log.info("[REMINDER_APP] userId={} content={}", userIdStr, notifyContent);
            }
            notifyCount++;
        }
        return notifyCount;
    }
    
    @Override
    public String topic() {
        return SpringUtil.getPrefixDistinctionName() + "-" + DELAY_DROP_REMINDER;
    }
}
