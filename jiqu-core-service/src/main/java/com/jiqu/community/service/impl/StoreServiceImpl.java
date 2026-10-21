package com.jiqu.community.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.jiqu.community.core.RedisKeyManage;
import com.jiqu.community.dto.Result;
import com.jiqu.community.entity.Store;
import com.jiqu.community.handler.BloomFilterHandlerFactory;
import com.jiqu.community.mapper.StoreMapper;
import com.jiqu.community.redis.RedisCache;
import com.jiqu.community.redis.RedisKeyBuild;
import com.jiqu.community.service.IStoreService;
import com.jiqu.community.servicelock.LockType;
import com.jiqu.community.toolkit.SnowflakeIdGenerator;
import com.jiqu.community.util.ServiceLockTool;
import com.jiqu.community.utils.CacheClient;
import com.jiqu.community.utils.SystemConstants;
import org.redisson.api.RLock;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.jiqu.community.constant.Constant.BLOOM_FILTER_HANDLER_STORE;
import static com.jiqu.community.utils.RedisConstants.CACHE_STORE_KEY;
import static com.jiqu.community.utils.RedisConstants.CACHE_STORE_TTL;
import static com.jiqu.community.utils.RedisConstants.LOCK_STORE_KEY;
import static com.jiqu.community.utils.RedisConstants.STORE_GEO_KEY;


@Slf4j
@Service
public class StoreServiceImpl extends ServiceImpl<StoreMapper, Store> implements IStoreService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    
    @Resource
    private ServiceLockTool serviceLockTool;
    
    @Resource
    private RedisCache redisCache;
    
    @Resource
    private BloomFilterHandlerFactory bloomFilterHandlerFactory;
    
    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;
    
    
    @Override
    public Result<Long> saveStore(final Store store) {
        // 写入数据库
        store.setId(snowflakeIdGenerator.nextId());
        save(store);
        // 写入布隆过滤器（潮流店铺业务）
        bloomFilterHandlerFactory.get(BLOOM_FILTER_HANDLER_STORE).add(String.valueOf(store.getId()));
        // 返回潮流店铺id
        return Result.ok(store.getId());
    }
    
    @Override
    public Result queryById(Long id) {
        //Store store = queryByIdV1(id);

        // 互斥锁解决缓存击穿
        //store = queryByIdV2(id);

        // 逻辑过期解决缓存击穿
        //store = queryByIdV3(id);
        
        // 🚀完美的方案！使用双重检测锁和空值配置，解决缓存击穿和缓存穿透
        Store store = queryByIdV4(id);
        
        if (store == null) {
            return Result.fail("潮流店铺不存在！");
        }
        // 7.返回
        return Result.ok(store);
    }
    
    public Store queryByIdV1(Long id){
        return cacheClient
                .queryWithPassThrough(CACHE_STORE_KEY, id, Store.class, this::getById, CACHE_STORE_TTL, TimeUnit.MINUTES);
    }
    
    public Store queryByIdV2(Long id){
        return cacheClient
                .queryWithMutex(CACHE_STORE_KEY, id, Store.class, this::getById, CACHE_STORE_TTL, TimeUnit.MINUTES);
    }
    
    public Store queryByIdV3(Long id){
        return cacheClient
                .queryWithLogicalExpire(CACHE_STORE_KEY, id, Store.class, this::getById, 20L, TimeUnit.SECONDS);
    }
    
    public Store queryByIdV4(Long id){
        Store store =
                redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_STORE_KEY, id), Store.class);
        if (Objects.nonNull(store)) {
            return store;
        }
        log.info("查询潮流店铺 从Redis缓存没有查询到 潮流店铺id : {}",id);
        if (!bloomFilterHandlerFactory.get(BLOOM_FILTER_HANDLER_STORE).contains(String.valueOf(id))) {
            log.info("查询潮流店铺 布隆过滤器判断不存在 潮流店铺id : {}",id);
            throw new RuntimeException("查询潮流店铺不存在");
        }
        Boolean existResult = redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_STORE_KEY_NULL, id));
        if (existResult){
            throw new RuntimeException("查询潮流店铺不存在");
        }
        RLock lock = serviceLockTool.getLock(LockType.Reentrant, LOCK_STORE_KEY, new String[]{String.valueOf(id)});
        lock.lock();
        try {
            existResult = redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_STORE_KEY_NULL, id));
            if (existResult){
                throw new RuntimeException("查询潮流店铺不存在");
            }
            store = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_STORE_KEY, id), Store.class);
            if (Objects.nonNull(store)) {
                return store;
            }
            store = getById(id);
            if (Objects.isNull(store)) {
                redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_STORE_KEY_NULL, id),
                        "这是一个空值",
                        CACHE_STORE_TTL,
                        TimeUnit.MINUTES);
                throw new RuntimeException("查询潮流店铺不存在");
            }
            redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_STORE_KEY, id),store,
                    CACHE_STORE_TTL,
                    TimeUnit.MINUTES);
            return store;
        }finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional
    public Result update(Store store) {
        Long id = store.getId();
        if (id == null) {
            return Result.fail("潮流店铺id不能为空");
        }
        // 1.更新数据库
        updateById(store);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_STORE_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryStoreByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        //TODO 先改成 x 和 y 都是空
        x = null;
        y = null;
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Store> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：storeId、distance
        String key = STORE_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取潮流店铺id
            String storeIdStr = result.getContent().getName();
            ids.add(Long.valueOf(storeIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(storeIdStr, distance);
        });
        // 5.根据id查询Store
        String idStr = StrUtil.join(",", ids);
        List<Store> stores = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Store store : stores) {
            store.setDistance(distanceMap.get(store.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(stores);
    }
}
