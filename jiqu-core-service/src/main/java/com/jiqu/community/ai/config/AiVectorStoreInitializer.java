package com.jiqu.community.ai.config;

import com.jiqu.community.entity.Blog;
import com.jiqu.community.entity.Store;
import com.jiqu.community.entity.StoreCategory;
import com.jiqu.community.service.IBlogService;
import com.jiqu.community.service.IStoreCategoryService;
import com.jiqu.community.service.IStoreService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 启动时将潮流店铺与探店笔记写入向量库，供 RAG 检索
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.rag.enabled", havingValue = "true")
public class AiVectorStoreInitializer implements CommandLineRunner {

    @Resource
    private VectorStore vectorStore;
    @Resource
    private IStoreService storeService;
    @Resource
    private IBlogService blogService;
    @Resource
    private IStoreCategoryService storeCategoryService;

    @Override
    public void run(String... args) {
        try {
            List<Document> documents = new ArrayList<>();

            List<Store> stores = storeService.list();
            if (stores.isEmpty()) {
                log.warn("[AI] tb_shop 无数据，跳过向量库初始化");
                return;
            }

            Map<Long, String> storeNameMap = stores.stream()
                    .collect(Collectors.toMap(Store::getId, Store::getName, (a, b) -> a));
            Map<Long, String> typeNameMap = storeCategoryService.list().stream()
                    .collect(Collectors.toMap(StoreCategory::getId, StoreCategory::getName, (a, b) -> a));

            for (Store store : stores) {
                String typeName = typeNameMap.get(store.getTypeId());
                String storeText = "店铺名称：" + store.getName()
                        + ",店铺类型：" + (typeName != null ? typeName : "未知")
                        + ",评分：" + (store.getScore() != null ? store.getScore() / 10.0 : "暂无")
                        + ",地址：" + (store.getAddress() != null ? store.getAddress() : "暂无")
                        + ",商圈：" + (store.getArea() != null ? store.getArea() : "暂无")
                        + ",营业时间：" + (store.getOpenHours() != null ? store.getOpenHours() : "暂无")
                        + ",均价：" + (store.getAvgPrice() != null ? store.getAvgPrice() : "暂无");
                documents.add(new Document(storeText, Map.of("storeId", store.getId())));
            }

            List<Blog> blogs = blogService.list();
            if (!blogs.isEmpty()) {
                for (Blog blog : blogs) {
                    String storeName = storeNameMap.get(blog.getStoreId());
                    String blogText = "探店笔记" + (storeName != null ? "（所属店铺：" + storeName + "）" : "") + "：" + blog.getContent();
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("blogId", blog.getId());
                    if (blog.getStoreId() != null) {
                        metadata.put("storeId", blog.getStoreId());
                    }
                    documents.add(new Document(blogText, metadata));
                }
            }

            vectorStore.add(documents);
            log.info("[AI] 向量库初始化完成，共 {} 条文档（{} 店铺 + {} 笔记）",
                    documents.size(), stores.size(), blogs.size());
        } catch (Exception e) {
            log.error("[AI] 向量库初始化失败（请检查百炼 API Key 与网络）：{}", e.getMessage());
        }
    }
}
