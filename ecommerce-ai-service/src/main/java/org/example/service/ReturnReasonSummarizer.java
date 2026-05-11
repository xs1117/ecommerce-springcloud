package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class ReturnReasonSummarizer {

    private static final Logger log = LoggerFactory.getLogger(ReturnReasonSummarizer.class);

    private final ChatClient chatClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public ReturnReasonSummarizer(ChatClient chatClient, AiProperties aiProperties, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    public String summarizeForMerchant(String userText) {
        String normalizedInput = normalize(userText);
        if (!StringUtils.hasText(normalizedInput)) {
            return "其他原因";
        }

        String llmSummary = summarizeWithLlm(normalizedInput);
        if (!StringUtils.hasText(llmSummary)) {
            return fallbackSummary(normalizedInput);
        }

        String cleaned = cleanSummary(llmSummary);
        if (!StringUtils.hasText(cleaned)) {
            return fallbackSummary(normalizedInput);
        }

        if (normalize(cleaned).equals(normalizedInput)) {
            return fallbackSummary(normalizedInput);
        }
        return cleaned;
    }

    private String summarizeWithLlm(String userText) {
        if (!aiProperties.isLlmEnabled() || chatClient == null) {
            return "";
        }
        try {
            String systemPrompt = "你是电商售后原因总结器。请根据用户原话，生成一条商家可见的退货原因说明。"
                    + "只输出JSON对象，格式必须为 {\"reasonSummary\":\"...\"}。"
                    + "要求：1) 用商家可读的自然表述重新组织，不要照搬用户原话；"
                    + "2) 不要包含引号、姓名、手机号、订单号等敏感信息；"
                    + "3) 不要输出分析过程；"
                    + "4) 语气客观简洁，长度控制在30字以内；"
                    + "5) 如果无法明确理解，就输出“其他原因”。";
            String userPrompt = "用户原话：\n" + userText;
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            if (!StringUtils.hasText(content)) {
                return "";
            }
            return extractReasonSummary(content);
        } catch (Exception ex) {
            log.debug("Return reason summarization failed: {}", ex.getMessage(), ex);
            return "";
        }
    }

    private String extractReasonSummary(String content) {
        String text = stripWrappers(content);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            if (node.isTextual()) {
                return cleanSummary(node.asText());
            }
            JsonNode summaryNode = node.get("reasonSummary");
            if (summaryNode != null && summaryNode.isTextual()) {
                return cleanSummary(summaryNode.asText());
            }
        } catch (Exception ignored) {
            // fall through to plain-text handling
        }
        if (text.startsWith("{") && text.endsWith("}")) {
            return "";
        }
        return cleanSummary(text);
    }

    private String stripWrappers(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String text = content.trim();
        text = text.replaceAll("^```(?:json)?\\s*", "");
        text = text.replaceAll("\\s*```$", "");
        text = text.replaceAll("(?i)^json\\s+", "");
        return text.trim();
    }

    private String fallbackSummary(String userText) {
        String lower = normalize(userText).toLowerCase(Locale.ROOT);
        if (containsAny(lower, "尺码", "大小", "偏小", "偏大", "不合适", "太小", "太大", "穿不了", "穿着不合适")) {
            return "用户反馈商品尺码不合适，申请退货";
        }
        if (containsAny(lower, "质量", "破损", "损坏", "瑕疵", "掉色", "开线", "裂", "坏了", "有问题", "做工", "污渍")) {
            return "用户反馈商品存在质量问题，申请退货";
        }
        if (containsAny(lower, "不符", "不一样", "与描述不符", "和图片不符", "和描述不一样", "货不对板", "色差", "假", "不是", "错发")) {
            return "用户反馈商品与描述不符，申请退货";
        }
        if (containsAny(lower, "发错", "漏发", "少发", "缺件", "少件", "多发")) {
            return "用户反馈商品发错或漏发，申请退货";
        }
        if (containsAny(lower, "不想要", "不需要", "不要了", "后悔", "改变主意", "买错", "拍错", "用不上")) {
            return "用户表示个人原因不想要了，申请退货";
        }
        if (containsAny(lower, "物流", "快递", "送达", "太慢", "迟到", "延迟", "没收到", "未收到")) {
            return "用户反馈物流问题，申请退货";
        }
        return "其他原因";
    }

    private boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text) || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String cleanSummary(String summary) {
        if (!StringUtils.hasText(summary)) {
            return "";
        }
        String cleaned = summary.replaceAll("[\\r\\n]+", " ").trim();
        cleaned = cleaned.replaceAll("\\s+", " ");
        cleaned = cleaned.replaceAll("^(?i)json\\s+", "");
        cleaned = cleaned.replaceAll("^[\\[{\\s]+", "");
        cleaned = cleaned.replaceAll("[\\]}\\s]+$", "");
        cleaned = cleaned.replaceAll("^[\"'`]+|[\"'`]+$", "");
        if (cleaned.length() > 60) {
            cleaned = cleaned.substring(0, 60);
        }
        return cleaned.trim();
    }

    private String normalize(String text) {
        return StringUtils.hasText(text) ? text.trim().replaceAll("\\s+", " ") : "";
    }
}

