package com.jiqu.community.ai.tool;

import com.jiqu.community.entity.Store;
import com.jiqu.community.service.IStoreService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI Function Calling 店铺工具：语义搜店 / 店铺详情 / 附近店铺
 * 数据映射 tb_shop（实体 Store）
 */
@Component
@ConditionalOnProperty(name = "ai.rag.enabled", havingValue = "true")
public class StoreTools {

    private static final String AI_STORE_GEO_KEY = "ai:store:geo";

    @Resource
    private VectorStore vectorStore;
    @Resource
    private IStoreService storeService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void initGeo() {
        List<RedisGeoCommands.GeoLocation<String>> locations = storeService.list().stream()
                .filter(store -> store.getX() != null && store.getY() != null)
                .map(store -> new RedisGeoCommands.GeoLocation<>(
                        store.getId().toString(), new Point(store.getX(), store.getY())))
                .collect(Collectors.toList());
        if (!locations.isEmpty()) {
            stringRedisTemplate.opsForGeo().add(AI_STORE_GEO_KEY, locations);
        }
    }

    @Tool(description = "根据语义描述搜索最匹配的潮流店铺")
    public List<Store> searchShops(String query) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(5)
                        .build()
        );

        List<Long> storeIds = results.stream()
                .map(doc -> doc.getMetadata().get("storeId"))
                .filter(Objects::nonNull)
                .map(storeId -> (Long) storeId)
                .collect(Collectors.toList());

        return storeIds.isEmpty() ? List.of() : storeService.listByIds(storeIds);
    }

    @Tool(description = "获取潮流店铺详细信息")
    public Store getShopDetail(Long storeId) {
        return storeService.getById(storeId);
    }

    @Tool(description = "计算指定坐标指定公里范围内的潮流店铺，并返回距离")
    public List<Store> getNearByShops(double lat, double lng, double maxDistanceKm) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().radius(
                AI_STORE_GEO_KEY,
                new Circle(new Point(lng, lat), new Distance(maxDistanceKm, RedisGeoCommands.DistanceUnit.KILOMETERS)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().sortAscending());

        if (results == null || results.getContent().isEmpty()) {
            return List.of();
        }

        Map<Long, Double> distanceMap = results.getContent().stream()
                .collect(Collectors.toMap(
                        r -> Long.valueOf(r.getContent().getName()),
                        r -> r.getDistance().getValue()));

        List<Store> stores = storeService.listByIds(distanceMap.keySet());
        stores.forEach(store -> store.setDistance(distanceMap.get(store.getId())));
        stores.sort(Comparator.comparingDouble(Store::getDistance));
        return stores;
    }
}
