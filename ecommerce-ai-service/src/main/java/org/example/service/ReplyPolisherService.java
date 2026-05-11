package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReplyPolisherService {

    private static final Logger log = LoggerFactory.getLogger(ReplyPolisherService.class);

    private final ChatClient chatClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public ReplyPolisherService(ChatClient chatClient, AiProperties aiProperties, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    public String polish(String scenario,
                         String draftReply,
                         String userMessage,
                         String facts,
                         String historyContext) {
        String normalizedDraft = normalize(draftReply);
        if (!StringUtils.hasText(normalizedDraft)) {
            return "";
        }
        if (!aiProperties.isLlmEnabled() || chatClient == null) {
            return normalizedDraft;
        }

        try {
            String systemPrompt = "你是电商客服回复润色器。你的职责只是把现有回复改写得更自然、更礼貌、更有共情，不要改变事实，不要新增承诺，不要泄露用户原话。"
                    + "只输出最终回复正文，不要输出分析过程、不要输出 JSON、不要输出代码块。"
                    + "回复要求：1) 语气真诚友好；2) 简洁清晰；3) 事实必须严格遵循输入；4) 如果输入本身已经很好，只做轻微润色。";
            String userPrompt = "场景：" + safe(scenario) + "\n"
                    + "用户原话：" + safe(userMessage) + "\n"
                    + "可用事实：" + safe(facts) + "\n"
                    + "对话历史：" + safe(historyContext) + "\n"
                    + "待润色回复：" + normalizedDraft;
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            String polished = normalize(stripWrappers(content));
            if (!StringUtils.hasText(polished)) {
                return normalizedDraft;
            }
            if (looksUnsafe(polished, userMessage, facts)) {
                return normalizedDraft;
            }
            return polished;
        } catch (Exception ex) {
            log.debug("Reply polish failed: {}", ex.getMessage(), ex);
            return normalizedDraft;
        }
    }

    private String stripWrappers(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String text = content.trim();
        text = text.replaceAll("^```(?:json|text)?\\s*", "");
        text = text.replaceAll("\\s*```$", "");
        text = text.replaceAll("(?i)^json\\s+", "");
        if (text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            if (node.isTextual()) {
                return node.asText();
            }
            JsonNode replyNode = node.get("reply");
            if (replyNode != null && replyNode.isTextual()) {
                return replyNode.asText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return text;
    }

    private boolean looksUnsafe(String reply, String userMessage, String facts) {
        String normalizedReply = normalize(reply).toLowerCase();
        String normalizedUser = normalize(userMessage).toLowerCase();
        if (StringUtils.hasText(normalizedUser) && normalizedReply.contains(normalizedUser) && normalizedUser.length() >= 4) {
            return true;
        }
        String normalizedFacts = normalize(facts).toLowerCase();
        if (StringUtils.hasText(normalizedFacts) && normalizedFacts.length() >= 10 && normalizedReply.contains(normalizedFacts)) {
            return true;
        }
        return false;
    }

    private String normalize(String text) {
        return StringUtils.hasText(text) ? text.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").trim() : "";
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "(无)";
    }
}


