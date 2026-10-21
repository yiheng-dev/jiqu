package com.jiqu.community;

import jakarta.annotation.Resource;
import com.jiqu.community.entity.Store;
import com.jiqu.community.service.impl.StoreServiceImpl;
import com.jiqu.community.utils.CacheClient;
import com.jiqu.community.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.jiqu.community.utils.RedisConstants.CACHE_STORE_KEY;
import static com.jiqu.community.utils.RedisConstants.STORE_GEO_KEY;

@SpringBootTest
class JiquCommunityApplicationTests {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private StoreServiceImpl storeService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    @Test
    void testSaveStore() throws InterruptedException {
        Store store = storeService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_STORE_KEY + 1L, store, 10L, TimeUnit.SECONDS);
    }

    @Test
    void loadStoreData() {
        // 1.查询潮流店铺信息
        List<Store> list = storeService.list();
        // 2.把潮流店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Store>> map = list.stream().collect(Collectors.groupingBy(Store::getTypeId));
        // 3.分批完成写入Redis
        for (Map.Entry<Long, List<Store>> entry : map.entrySet()) {
            // 3.1.获取类型id
            Long typeId = entry.getKey();
            String key = STORE_GEO_KEY + typeId;
            // 3.2.获取同类型的潮流店铺的集合
            List<Store> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            // 3.3.写入redis GEOADD key 经度 纬度 member
            for (Store store : value) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(store.getX(), store.getY()), store.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        store.getId().toString(),
                        new Point(store.getX(), store.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    @Test
    void testHyperLogLog() {
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if(j == 999){
                // 发送到Redis
                stringRedisTemplate.opsForHyperLogLog().add("hl2", values);
            }
        }
        // 统计数量
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println("count = " + count);
    }
}
