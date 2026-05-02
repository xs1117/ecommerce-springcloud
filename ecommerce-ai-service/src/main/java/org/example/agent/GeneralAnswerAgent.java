package org.example.agent;

import org.example.config.AiProperties;
import org.example.service.AiChatResult;
import org.example.service.OrderServiceClient;
import org.example.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GeneralAnswerAgent implements CustomerAgent {

    private static final Logger log = LoggerFactory.getLogger(GeneralAnswerAgent.class);
    private static final Pattern THINK_BLOCK_PATTERN = Pattern.compile("<think>([\\s\\S]*?)</think>", Pattern.CASE_INSENSITIVE);
    private static final String THINK_LABEL = "【思考内容】";
    private static final String ANSWER_LABEL = "【正式回答】";

    private final ChatClient chatClient;
    private final AiProperties aiProperties;
    private final RagService ragService;
    private final OrderServiceClient orderServiceClient;

    public GeneralAnswerAgent(ChatClient chatClient,
                              AiProperties aiProperties,
                              RagService ragService,
                              OrderServiceClient orderServiceClient) {
        this.chatClient = chatClient;
        this.aiProperties = aiProperties;
        this.ragService = ragService;
        this.orderServiceClient = orderServiceClient;
    }

    @Override
    public String agentName() {
        return "general-answer-agent";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean supports(ConversationContext context) {
        return true;
    }

    @Override
    public AiChatResult handle(ConversationContext context) {
        String message = context.message();
        String ragContext = ragService == null ? "" : ragService.findContext(message);
        String orderContext = resolveOrderContext(context.authorizationHeader(), context.orderNo());
        String historyContext = buildHistoryContext(context.history());
        AssistantReply assistantReply = buildGeneralReplyWithLlm(message, historyContext, ragContext, context.orderNo(), orderContext);
        return new AiChatResult(
                aiProperties.getModel(),
                assistantReply.reply(),
                assistantReply.thinking(),
                false,
                null,
                null,
                false,
                null,
                ragContext,
                List.of()
        );
    }

    private AssistantReply buildGeneralReplyWithLlm(String message, String historyContext, String ragContext, String orderNo, String orderContext) {
        String systemPrompt = "你是电商平台AI客服。回答需简洁、礼貌，优先中文。"
                + "若用户需求涉及退货/换货/退款/平台介入等需要执行的动作，提示用户提供订单号并说明会进行二次确认。"
                + "当需要给出推理、证据或说明原因时，请把思考过程放在一个独立的 <think>...</think> 块中返回（前端会解读并展示），仅在有价值时才输出思考过程，避免冗长无关内容。"
                + "思考过程应是简短的决策摘要，不要直接复制知识库原文。";
        String userPrompt = "最近对话历史:\n" + (StringUtils.hasText(historyContext) ? historyContext : "(无)") + "\n"
                + "当前用户问题: " + message + "\n"
                + "已选订单号: " + (StringUtils.hasText(orderNo) ? orderNo : "(未提供)") + "\n"
                + "订单上下文:\n" + (StringUtils.hasText(orderContext) ? orderContext : "(未获取到订单详情)") + "\n"
                + "可用知识库片段:\n" + (StringUtils.hasText(ragContext) ? ragContext : "(无匹配知识)");
        if (chatClient == null) {
                return fallbackReply(message, ragContext);
        }
        try {
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            if (StringUtils.hasText(content)) {
                log.debug("LLM content received (len={}): containsThinkTag={}, containsLabel={}",
                        content.length(),
                        content.toLowerCase(Locale.ROOT).contains("<think>"),
                        content.contains("【思考内容】"));
                AssistantReply parsed = parseAssistantReply(content);
                if (!StringUtils.hasText(parsed.thinking())) {
                    String llmThinking = generateThinkingBlock(message, historyContext, ragContext, orderNo, orderContext, parsed.reply());
                    if (StringUtils.hasText(llmThinking)) {
                        return new AssistantReply(parsed.reply(), llmThinking);
                    }
                }
                return parsed;
            }
        } catch (Exception ex) {
            log.warn("LLM call failed, fallback to deterministic reply. model={}, message={}, ragContextPresent={}, error={}",
                    aiProperties.getModel(),
                    message,
                    StringUtils.hasText(ragContext),
                    ex.getMessage(),
                    ex);
        }
        return fallbackReply(message, ragContext);
    }

    private AssistantReply fallbackReply(String message, String ragContext) {
        if (StringUtils.hasText(ragContext)) {
            return new AssistantReply("根据客服规则，我建议你参考以下信息：\n" + ragContext, "");
        }
        if (StringUtils.hasText(message)) {
            return new AssistantReply("我已收到你的问题（" + message + "）。你可以告诉我更具体的信息（如订单号、商品名称），我会继续帮你处理。", "");
        }
        return new AssistantReply("我已收到你的问题。你可以告诉我更具体的信息（如订单号、商品名称），我会继续帮你处理。", "");
    }

    private AssistantReply parseAssistantReply(String content) {
        String text = content.trim();
        if (!StringUtils.hasText(text)) {
            return new AssistantReply("", "");
        }
        int thinkingIdx = text.indexOf(THINK_LABEL);
        int answerIdx = text.indexOf(ANSWER_LABEL);
        if (thinkingIdx != -1 && answerIdx != -1 && answerIdx > thinkingIdx) {
            String thinking = text.substring(thinkingIdx + THINK_LABEL.length(), answerIdx).trim();
            String answer = text.substring(answerIdx + ANSWER_LABEL.length()).trim();
            return new AssistantReply(StringUtils.hasText(answer) ? answer : text, thinking);
        }
        Matcher matcher = THINK_BLOCK_PATTERN.matcher(text);
        if (!matcher.find()) {
            return new AssistantReply(text, "");
        }
        String thinking = matcher.group(1) == null ? "" : matcher.group(1).trim();
        String answer = matcher.replaceAll("").trim();
        if (!StringUtils.hasText(answer)) {
            answer = text;
        }
        return new AssistantReply(answer, thinking);
    }

    private String resolveOrderContext(String authorizationHeader, String orderNo) {
        if (!StringUtils.hasText(orderNo) || orderServiceClient == null) {
            return "";
        }
        try {
            Map<String, Object> detail = orderServiceClient.getOrderDetail(authorizationHeader, orderNo.trim());
            if (detail == null || detail.isEmpty()) {
                return "";
            }
            return summarizeOrderContext(detail);
        } catch (Exception ex) {
            log.warn("Failed to load order detail for AI prompt. orderNo={}, error={}", orderNo, ex.getMessage());
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private String summarizeOrderContext(Map<String, Object> detail) {
        Map<String, Object> order = detail.get("order") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Object itemsObj = detail.get("items");
        java.util.List<Map<String, Object>> items = itemsObj instanceof java.util.List<?> list
                ? list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList()
                : java.util.List.of();

        String orderNo = asText(order.get("orderNo"));
        String status = asText(order.get("status"));
        String createdAt = asText(order.get("createdAt"));
        String payAmount = asText(order.get("payAmount"));
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(orderNo)) {
            builder.append("订单号：").append(orderNo).append('\n');
        }
        if (StringUtils.hasText(status)) {
            builder.append("订单状态：").append(status).append('\n');
        }
        if (StringUtils.hasText(createdAt)) {
            builder.append("下单时间：").append(createdAt).append('\n');
        }
        if (StringUtils.hasText(payAmount)) {
            builder.append("支付金额：").append(payAmount).append('\n');
        }
        if (!items.isEmpty()) {
            builder.append("商品列表：");
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> item = items.get(i);
                String title = asText(item.get("title"));
                if (!StringUtils.hasText(title)) {
                    title = asText(item.get("productTitle"));
                }
                if (!StringUtils.hasText(title)) {
                    title = asText(item.get("productName"));
                }
                if (i > 0) {
                    builder.append("；");
                }
                builder.append(title.isBlank() ? "商品" : title);
                String quantity = asText(item.get("quantity"));
                if (StringUtils.hasText(quantity)) {
                    builder.append(" x ").append(quantity);
                }
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String generateThinkingBlock(String message,
                                         String historyContext,
                                         String ragContext,
                                         String orderNo,
                                         String orderContext,
                                         String answer) {
        if (chatClient == null) {
            return "";
        }
        try {
            String systemPrompt = "你是电商平台AI客服的思考摘要生成器。"
                    + "请根据用户问题、历史上下文、订单信息、知识库片段和已确定的正式回答，生成一段简短、自然、像 DeepSeek 一样的思考摘要。"
                    + "只输出思考内容本身，不要输出正式回答，不要复述知识库原文，不要分点过多，控制在 60~180 字。";
            String userPrompt = "用户问题:\n" + message + "\n"
                    + "历史上下文:\n" + (StringUtils.hasText(historyContext) ? historyContext : "(无)") + "\n"
                    + "订单号:\n" + (StringUtils.hasText(orderNo) ? orderNo : "(无)") + "\n"
                    + "订单信息:\n" + (StringUtils.hasText(orderContext) ? orderContext : "(无)") + "\n"
                    + "知识库片段:\n" + (StringUtils.hasText(ragContext) ? ragContext : "(无)") + "\n"
                    + "正式回答:\n" + (StringUtils.hasText(answer) ? answer : "(无)");
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            if (StringUtils.hasText(content)) {
                AssistantReply parsed = parseAssistantReply(content);
                if (StringUtils.hasText(parsed.thinking())) {
                    return parsed.thinking();
                }
                return content.trim();
            }
        } catch (Exception ex) {
            log.debug("Generate thinking block failed: {}", ex.getMessage(), ex);
        }
        return "";
    }

    private String buildHistoryContext(List<org.example.service.ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int start = Math.max(0, history.size() - 8);
        for (int i = start; i < history.size(); i++) {
            org.example.service.ChatTurn turn = history.get(i);
            if (turn == null || !StringUtils.hasText(turn.content())) {
                continue;
            }
            String role = StringUtils.hasText(turn.role()) ? turn.role().trim() : "user";
            String content = turn.content().trim();
            builder.append(role).append(": ").append(truncate(content)).append('\n');
        }
        return builder.toString().trim();
    }

    private String truncate(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String cleaned = text.replaceAll("\\s+", " ").trim();
        int maxLen = 280;
        if (cleaned.length() <= maxLen) {
            return cleaned;
        }
        return cleaned.substring(0, maxLen) + "...";
    }

    private record AssistantReply(String reply, String thinking) {
    }
}


