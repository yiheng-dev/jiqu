package com.jiqu.community.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

/**
 * AI 配置：OpenAI 兼容模式（百炼 DashScope）+ 内存向量库 + RAG 顾问。
 */
@Configuration
public class AiChatConfiguration {

    /**
     * Spring AI 使用独立的 RestClient 默认转换器。
     * 项目全局 JacksonCustom 会把 null 写成空串、数字写成字符串（如 "temperature":"0.7"），
     * 百炼收到这种请求会直接 500，不能让 Spring AI 复用 MVC 的 ObjectMapper。
     */
    @Bean
    @Scope("prototype")
    public RestClient.Builder springAiRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public VectorStore vectorStore(OpenAiEmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder().topK(5).build())
                .build();
    }

    @Bean
    public ChatClient chatClient(OpenAiChatModel model, QuestionAnswerAdvisor advisor) {
        return ChatClient.builder(model)
                .defaultSystem("你是一个专业、严谨的潮流社区助手，回答要基于提供的数据。")
                .defaultAdvisors(advisor)
                .build();
    }
}
