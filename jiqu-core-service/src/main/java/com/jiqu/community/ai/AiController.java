package com.jiqu.community.ai;

import com.jiqu.community.ai.tool.StoreTools;
import com.jiqu.community.dto.Result;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 潮流 AI 助手：RAG + Function Calling。
 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private ChatClient chatClient;
    @Resource
    private StoreTools storeTools;

    @PostMapping("/chat")
    public Result<String> chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return Result.fail("消息不能为空");
        }
        try {
            String reply = chatClient.prompt()
                    .user(message)
                    .tools(storeTools)
                    .call()
                    .content();
            return Result.ok(reply);
        } catch (Exception e) {
            log.error("[AI] 问答失败: {}", e.getMessage());
            return Result.fail("AI 服务暂不可用：" + e.getMessage());
        }
    }
}
