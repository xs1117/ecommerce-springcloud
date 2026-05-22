package org.example.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.AiProperties;
import org.example.service.dto.AiChatResult;
import org.example.service.MerchantCatalogClient;
import org.example.service.ReplyPolisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IntentGuidedShoppingAgent implements CustomerAgent {

    private static final Logger log = LoggerFactory.getLogger(IntentGuidedShoppingAgent.class);
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*?\\}");

    private static final List<String> AFTER_SALE_KEYWORDS = List.of("退货", "退款", "换货", "售后", "平台介入");
    private static final List<String> BUY_KEYWORDS = List.of("推荐", "买", "挑", "选", "找", "适合", "合适", "穿", "搭配");
    private static final List<String> CATEGORY_KEYWORDS = List.of(
            "连衣裙", "裙子", "半身裙", "礼服", "上衣", "衬衫", "t恤", "卫衣", "毛衣", "针织",
            "裤子", "牛仔裤", "西装", "外套", "风衣", "羽绒服", "大衣", "鞋", "高跟鞋", "运动鞋",
            "包", "内衣", "配饰", "耳环", "项链"
    );
    private static final List<String> OCCASION_KEYWORDS = List.of(
            "婚礼", "婚宴", "年会", "晚宴", "派对", "约会", "通勤", "面试", "毕业", "旅行", "日常"
    );
    private static final List<String> BODY_TYPE_KEYWORDS = List.of("梨形", "苹果", "沙漏", "倒三角", "直筒");
    private static final List<String> GOAL_KEYWORDS = List.of("显瘦", "遮胯", "遮肚", "显腿长", "显高", "显白", "气质", "减龄", "修身", "宽松");
    private static final List<String> COLOR_KEYWORDS = List.of("黑色", "白色", "蓝色", "红色", "绿色", "灰色", "米色", "裸色", "卡其", "粉色");

    private final ChatClient chatClient;
    private final AiProperties aiProperties;
    private final MerchantCatalogClient merchantCatalogClient;
    private final ReplyPolisherService replyPolisherService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IntentGuidedShoppingAgent(ChatClient chatClient,
                                    AiProperties aiProperties,
                                    MerchantCatalogClient merchantCatalogClient,
                                    ReplyPolisherService replyPolisherService) {
        this.chatClient = chatClient;
        this.aiProperties = aiProperties;
        this.merchantCatalogClient = merchantCatalogClient;
        this.replyPolisherService = replyPolisherService;
    }

    @Override
    public String agentName() {
        return "intent-guided-shopping-agent";
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public boolean supports(ConversationContext context) {
        if (context.hasImage() || !context.hasTextMessage()) {
            return false;
        }
        return isShoppingIntent(context.message());
    }

    @Override
    public AiChatResult handle(ConversationContext context) {
        String message = context.message();
        IntentSlots slots = resolveSlots(message);
        String keyword = buildSearchKeyword(slots, message);
        List<Map<String, Object>> products = searchProducts(keyword, slots);

        String followUp = buildFollowUpQuestion(slots);
        String intentSummary = buildIntentSummary(slots);
        String reply;
        if (products.isEmpty()) {
            reply = "我已理解你的需求" + intentSummary + "，但暂时没有匹配到商品。"
                    + (StringUtils.hasText(followUp) ? " " + followUp : " 你也可以补充预算/颜色/尺码，我会继续缩小范围。");
        } else {
            reply = "我已理解你的需求" + intentSummary + "，并已筛选出 " + products.size() + " 个更匹配的结果。"
                    + (StringUtils.hasText(followUp) ? " " + followUp : " 如果你愿意，我还可以按预算或颜色进一步精筛。");
        }

        String polished = polishReply("意图导购", reply, message, "keyword=" + keyword + ", intent=" + intentSummary);
        String thinking = polishReply("意图导购-思考", "已完成需求识别并检索商品", message, "keyword=" + keyword);
        return new AiChatResult(
                aiProperties.getModel(),
                polished,
                thinking,
                false,
                null,
                null,
                false,
                null,
                "",
                products
        );
    }

    private boolean isShoppingIntent(String message) {
        String text = normalize(message);
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if (containsAny(text, AFTER_SALE_KEYWORDS)) {
            return false;
        }
        boolean hasCategory = containsAny(text, CATEGORY_KEYWORDS);
        boolean hasOccasion = containsAny(text, OCCASION_KEYWORDS);
        boolean hasGoal = containsAny(text, GOAL_KEYWORDS) || containsAny(text, BODY_TYPE_KEYWORDS);
        boolean hasBuyVerb = containsAny(text, BUY_KEYWORDS);
        return (hasCategory && (hasOccasion || hasGoal || hasBuyVerb)) || (hasOccasion && hasGoal);
    }

    private IntentSlots resolveSlots(String message) {
        IntentSlots llm = extractSlotsWithLlm(message);
        if (llm != null && llm.hasSignal()) {
            return llm;
        }
        return extractSlotsHeuristically(message);
    }

    private IntentSlots extractSlotsWithLlm(String message) {
        if (!aiProperties.isLlmEnabled() || chatClient == null || !StringUtils.hasText(message)) {
            return null;
        }
        try {
            String systemPrompt = "你是电商导购意图识别器，只输出 JSON。字段："
                    + "category(品类), occasion(场合), bodyType(身材), styleGoals(风格/诉求数组), colors(颜色数组), season(季节), keywords(补充关键词数组)。"
                    + "不要输出解释，不要输出代码块。";
            String userPrompt = "用户话术：" + message + "\n"
                    + "请输出JSON，例如：{\"category\":\"连衣裙\",\"occasion\":\"婚礼\",\"bodyType\":\"梨形\",\"styleGoals\":[\"显瘦\"],\"colors\":[],\"season\":\"\",\"keywords\":[]}";
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            String json = extractJsonObject(content);
            if (!StringUtils.hasText(json)) {
                return null;
            }
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {
            });
            return IntentSlots.from(map);
        } catch (Exception ex) {
            log.debug("Intent LLM parse failed: {}", ex.getMessage());
            return null;
        }
    }

    private IntentSlots extractSlotsHeuristically(String message) {
        String text = normalize(message);
        String category = findFirst(text, CATEGORY_KEYWORDS);
        String occasion = findFirst(text, OCCASION_KEYWORDS);
        String bodyType = findFirst(text, BODY_TYPE_KEYWORDS);
        List<String> goals = findAll(text, GOAL_KEYWORDS);
        List<String> colors = findAll(text, COLOR_KEYWORDS);
        return new IntentSlots(category, occasion, bodyType, goals, colors, "", List.of());
    }

    private String buildSearchKeyword(IntentSlots slots, String message) {
        Set<String> tokens = new LinkedHashSet<>();
        if (StringUtils.hasText(slots.category())) {
            tokens.add(slots.category());
        }
        if (StringUtils.hasText(slots.occasion())) {
            tokens.add(slots.occasion());
        }
        if (StringUtils.hasText(slots.bodyType())) {
            tokens.addAll(bodyTypeHints(slots.bodyType()));
        }
        for (String goal : slots.styleGoals()) {
            if (StringUtils.hasText(goal)) {
                tokens.add(goal);
                tokens.addAll(goalHints(goal));
            }
        }
        for (String color : slots.colors()) {
            if (StringUtils.hasText(color)) {
                tokens.add(color);
                break;
            }
        }
        for (String kw : slots.keywords()) {
            if (StringUtils.hasText(kw)) {
                tokens.add(kw);
            }
        }
        if (tokens.isEmpty()) {
            tokens.add(message);
        }
        return String.join(" ", tokens);
    }

    private List<Map<String, Object>> searchProducts(String keyword, IntentSlots slots) {
        if (!StringUtils.hasText(keyword) || merchantCatalogClient == null) {
            return List.of();
        }
        try {
            List<Map<String, Object>> found = merchantCatalogClient.searchPublicProducts(keyword, 6);
            List<Map<String, Object>> normalized = normalizeRecommendedProducts(found);
            if (!normalized.isEmpty()) {
                return normalized;
            }
        } catch (Exception ex) {
            log.warn("Guided shopping search failed. keyword={}, error={}", keyword, ex.getMessage());
        }
        if (StringUtils.hasText(slots.category())) {
            try {
                return normalizeRecommendedProducts(merchantCatalogClient.searchPublicProducts(slots.category(), 6));
            } catch (Exception ex) {
                log.warn("Guided shopping fallback search failed. category={}, error={}", slots.category(), ex.getMessage());
            }
        }
        return List.of();
    }

    private List<Map<String, Object>> normalizeRecommendedProducts(List<Map<String, Object>> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> item : products) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            String id = asText(item.get("id"));
            if (!StringUtils.hasText(id)) {
                continue;
            }
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", id);
            dto.put("title", asText(item.get("title")));
            dto.put("price", item.get("price"));
            dto.put("imageUrl", asText(item.get("imageUrl")));
            dto.put("storeName", asText(item.get("storeName")));
            dto.put("link", "/product/" + id);
            normalized.add(dto);
        }
        return normalized;
    }

    private String buildFollowUpQuestion(IntentSlots slots) {
        if (!StringUtils.hasText(slots.category())) {
            return "你更偏好哪类单品（连衣裙/半身裙/套装等）？";
        }
        if (!StringUtils.hasText(slots.occasion())) {
            return "主要是在哪些场合穿？比如婚礼、通勤或约会。";
        }
        if (slots.styleGoals().isEmpty()) {
            return "想突出哪种效果？比如显瘦、显高或气质感。";
        }
        return "预算区间和颜色偏好是什么？";
    }

    private String buildIntentSummary(IntentSlots slots) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(slots.bodyType())) {
            parts.add(slots.bodyType() + "身材");
        }
        if (StringUtils.hasText(slots.occasion())) {
            parts.add(slots.occasion());
        }
        if (!slots.styleGoals().isEmpty()) {
            parts.add(String.join("/", slots.styleGoals()));
        }
        if (StringUtils.hasText(slots.category())) {
            parts.add(slots.category());
        }
        if (parts.isEmpty()) {
            return "";
        }
        return "（" + String.join("，", parts) + "）";
    }

    private String extractJsonObject(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String text = content.trim();
        text = text.replaceAll("^```(?:json)?\\s*", "");
        text = text.replaceAll("\\s*```$", "");
        Matcher matcher = JSON_OBJECT_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : "";
    }

    private String normalize(String message) {
        return StringUtils.hasText(message) ? message.toLowerCase(Locale.ROOT) : "";
    }

    private boolean containsAny(String text, List<String> tokens) {
        for (String token : tokens) {
            if (text.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String findFirst(String text, List<String> tokens) {
        for (String token : tokens) {
            if (text.contains(token.toLowerCase(Locale.ROOT))) {
                return token;
            }
        }
        return "";
    }

    private List<String> findAll(String text, List<String> tokens) {
        List<String> found = new ArrayList<>();
        for (String token : tokens) {
            if (text.contains(token.toLowerCase(Locale.ROOT))) {
                found.add(token);
            }
        }
        return found;
    }

    private List<String> bodyTypeHints(String bodyType) {
        if (!StringUtils.hasText(bodyType)) {
            return List.of();
        }
        return switch (bodyType) {
            case "梨形" -> List.of("A字", "高腰", "遮胯");
            case "苹果" -> List.of("V领", "遮肚", "直筒");
            case "倒三角" -> List.of("A字", "下装加分");
            case "沙漏" -> List.of("收腰", "修身");
            case "直筒" -> List.of("收腰", "有曲线");
            default -> List.of();
        };
    }

    private List<String> goalHints(String goal) {
        if (!StringUtils.hasText(goal)) {
            return List.of();
        }
        return switch (goal) {
            case "显瘦" -> List.of("深色", "高腰");
            case "显腿长" -> List.of("高腰", "短上衣");
            case "显高" -> List.of("高腰", "顺色");
            default -> List.of();
        };
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String polishReply(String scenario, String draftReply, String userMessage, String facts) {
        return replyPolisherService == null
                ? draftReply
                : replyPolisherService.polish(scenario, draftReply, userMessage, facts, null);
    }

    private record IntentSlots(
            String category,
            String occasion,
            String bodyType,
            List<String> styleGoals,
            List<String> colors,
            String season,
            List<String> keywords
    ) {
        static IntentSlots from(Map<String, Object> map) {
            if (map == null || map.isEmpty()) {
                return new IntentSlots("", "", "", List.of(), List.of(), "", List.of());
            }
            String category = asText(map.get("category"));
            String occasion = asText(map.get("occasion"));
            String bodyType = asText(map.get("bodyType"));
            List<String> styleGoals = asStringList(map.get("styleGoals"));
            List<String> colors = asStringList(map.get("colors"));
            String season = asText(map.get("season"));
            List<String> keywords = asStringList(map.get("keywords"));
            return new IntentSlots(category, occasion, bodyType, styleGoals, colors, season, keywords);
        }

        boolean hasSignal() {
            return StringUtils.hasText(category)
                    || StringUtils.hasText(occasion)
                    || StringUtils.hasText(bodyType)
                    || (styleGoals != null && !styleGoals.isEmpty());
        }

        private static String asText(Object value) {
            return value == null ? "" : String.valueOf(value).trim();
        }

        private static List<String> asStringList(Object value) {
            if (value instanceof List<?> list) {
                List<String> result = new ArrayList<>();
                for (Object item : list) {
                    String text = asText(item);
                    if (StringUtils.hasText(text)) {
                        result.add(text);
                    }
                }
                return result;
            }
            if (value instanceof String text && StringUtils.hasText(text)) {
                List<String> result = new ArrayList<>();
                for (String part : text.split("[,/，、]")) {
                    String cleaned = part.trim();
                    if (StringUtils.hasText(cleaned)) {
                        result.add(cleaned);
                    }
                }
                return result;
            }
            return List.of();
        }
    }
}

