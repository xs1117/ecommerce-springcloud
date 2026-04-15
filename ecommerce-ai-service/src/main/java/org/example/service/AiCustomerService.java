package org.example.service;

import org.example.config.AiProperties;
import org.example.security.AuthenticatedUser;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class AiCustomerService {

    private static final String ACTION_APPLY_RETURN = "APPLY_RETURN";
    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("(?:订单号|订单|order)\\s*[:：#-]?\\s*([A-Za-z0-9-]{6,64})", Pattern.CASE_INSENSITIVE);

    private final ChatClient chatClient;
    private final AiProperties aiProperties;
    private final RagService ragService;
    private final ConfirmationService confirmationService;
    private final ChatServiceClient chatServiceClient;

    public AiCustomerService(ChatClient chatClient,
                             AiProperties aiProperties,
                             RagService ragService,
                             ConfirmationService confirmationService,
                             ChatServiceClient chatServiceClient) {
        this.chatClient = chatClient;
        this.aiProperties = aiProperties;
        this.ragService = ragService;
        this.confirmationService = confirmationService;
        this.chatServiceClient = chatServiceClient;
    }

    public AiChatResult chat(AuthenticatedUser user, String authorizationHeader, AiChatCommand command) {
        if (command == null || !StringUtils.hasText(command.message())) {
            throw new ResponseStatusException(BAD_REQUEST, "message不能为空");
        }

        Optional<PendingAction> tokenAction = findPendingAction(user, command);
        if (Boolean.TRUE.equals(command.confirm()) || isConfirm(command.message())) {
            PendingAction action = tokenAction.orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "确认令牌无效或已过期"));
            Map<String, Object> result = executeAfterSaleAction(authorizationHeader, action);
            confirmationService.invalidate(action);
            return new AiChatResult(
                    aiProperties.getModel(),
                    "已为你发起退货申请，商家会尽快处理。",
                    false,
                    null,
                    null,
                    true,
                    result,
                    ""
            );
        }

        if (isCancel(command.message()) && tokenAction.isPresent()) {
            confirmationService.invalidate(tokenAction.get());
            return new AiChatResult(
                    aiProperties.getModel(),
                    "好的，已取消本次操作。如果你仍需要退货，可以重新告诉我订单号。",
                    false,
                    null,
                    null,
                    false,
                    null,
                    ""
            );
        }

        String orderNo = resolveOrderNo(command);
        String message = command.message().trim();

        if (isReturnIntent(message)) {
            if (!StringUtils.hasText(orderNo)) {
                return new AiChatResult(
                        aiProperties.getModel(),
                        "可以帮你发起退货申请。请先告诉我订单号（例如：订单号 202604150001）。",
                        false,
                        null,
                        null,
                        false,
                        null,
                        ""
                );
            }
            PendingAction pendingAction = confirmationService.create(user.userId(), orderNo, ACTION_APPLY_RETURN, "AI客服发起退货");
            Map<String, Object> suggested = new LinkedHashMap<>();
            suggested.put("actionType", ACTION_APPLY_RETURN);
            suggested.put("orderNo", orderNo);
            return new AiChatResult(
                    aiProperties.getModel(),
                    "我已准备好为你发起退货申请（订单号：" + orderNo + "）。请点击确认，或回复“取消”。",
                    true,
                    pendingAction.token(),
                    suggested,
                    false,
                    null,
                    ""
            );
        }

        String ragContext = ragService.findContext(message);
        String reply = buildGeneralReplyWithLlm(message, ragContext);
        return new AiChatResult(
                aiProperties.getModel(),
                reply,
                false,
                null,
                null,
                false,
                null,
                ragContext
        );
    }

    private Optional<PendingAction> findPendingAction(AuthenticatedUser user, AiChatCommand command) {
        if (StringUtils.hasText(command.confirmationToken())) {
            return confirmationService.findValidByToken(user.userId(), command.confirmationToken().trim());
        }
        return confirmationService.findLatestValidByUser(user.userId());
    }

    private Map<String, Object> executeAfterSaleAction(String authorizationHeader, PendingAction action) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(BAD_REQUEST, "登录态无效，请重新登录后再试");
        }
        Map<String, Object> conversation = chatServiceClient.openAfterSale(authorizationHeader, action.orderNo(), null);
        if (conversation == null || conversation.get("id") == null) {
            throw new ResponseStatusException(BAD_REQUEST, "创建售后会话失败，请稍后重试");
        }
        Long conversationId = Long.valueOf(String.valueOf(conversation.get("id")));
        Map<String, Object> applied = chatServiceClient.applyAfterSaleAction(
                authorizationHeader,
                conversationId,
                action.actionType(),
                action.remark()
        );
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("conversation", conversation);
        merged.put("afterSale", applied);
        return merged;
    }

    private String buildGeneralReplyWithLlm(String message, String ragContext) {
        String systemPrompt = "你是电商平台AI客服。回答需简洁、礼貌，优先中文。"
                + "若用户需求涉及退货/换货/退款等需要执行的动作，提示用户提供订单号并说明会进行二次确认。";
        String userPrompt = "用户问题: " + message + "\n"
                + "可用知识库片段:\n" + (StringUtils.hasText(ragContext) ? ragContext : "(无匹配知识)");
        try {
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            if (StringUtils.hasText(content)) {
                return content.trim();
            }
        } catch (Exception ignored) {
            // Fall back to deterministic reply when LLM is unavailable.
        }
        if (StringUtils.hasText(ragContext)) {
            return "根据客服规则，我建议你参考以下信息：\n" + ragContext;
        }
        return "我已收到你的问题。你可以告诉我更具体的信息（如订单号、商品名称），我会继续帮你处理。";
    }

    private String resolveOrderNo(AiChatCommand command) {
        if (StringUtils.hasText(command.orderNo())) {
            return command.orderNo().trim();
        }
        Matcher matcher = ORDER_NO_PATTERN.matcher(command.message());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private boolean isReturnIntent(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("退货") || normalized.contains("退款") || normalized.contains("return");
    }

    private boolean isCancel(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("取消") || normalized.contains("不用了") || normalized.contains("cancel");
    }

    private boolean isConfirm(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("确认") || normalized.contains("同意") || normalized.contains("ok") || normalized.contains("yes");
    }
}


