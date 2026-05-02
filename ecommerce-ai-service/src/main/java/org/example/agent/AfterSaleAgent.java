package org.example.agent;

import org.example.config.AiProperties;
import org.example.service.AiChatResult;
import org.example.service.ChatServiceClient;
import org.example.service.ConfirmationService;
import org.example.service.PendingAction;
import org.example.service.action.ActionHandler;
import org.example.service.action.ActionRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class AfterSaleAgent implements CustomerAgent {

    private final AiProperties aiProperties;
    private final ConfirmationService confirmationService;
    private final ChatServiceClient chatServiceClient;
    private final ActionRegistry actionRegistry;

    public AfterSaleAgent(AiProperties aiProperties,
                          ConfirmationService confirmationService,
                          ChatServiceClient chatServiceClient,
                          ActionRegistry actionRegistry) {
        this.aiProperties = aiProperties;
        this.confirmationService = confirmationService;
        this.chatServiceClient = chatServiceClient;
        this.actionRegistry = actionRegistry;
    }

    @Override
    public String agentName() {
        return "after-sale-agent";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean supports(ConversationContext context) {
        // If the user provided an image, don't handle it in AfterSaleAgent.
        // Let image-aware agents (e.g. ImageSearchAgent) match first.
        if (context.hasImage()) {
            return false;
        }
        if (context.confirmRequested()) {
            return true;
        }
        if (context.cancelRequested() && context.hasPendingAction()) {
            return true;
        }
        return context.hasTextMessage() && actionRegistry.resolve(context.message()).isPresent();
    }

    @Override
    public AiChatResult handle(ConversationContext context) {
        String message = context.message();
        if (context.confirmRequested() || isConfirm(message)) {
            PendingAction action = Optional.ofNullable(context.pendingAction())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "确认令牌无效或已过期"));
            Map<String, Object> result = executeAfterSaleAction(context.authorizationHeader(), action);
            confirmationService.invalidate(action);
            return new AiChatResult(
                    aiProperties.getModel(),
                    actionRegistry.executedReply(action.actionType()),
                    "",
                    false,
                    null,
                    null,
                    true,
                    result,
                    "",
                    null
            );
        }

        if (context.cancelRequested() && context.hasPendingAction()) {
            confirmationService.invalidate(context.pendingAction());
            return new AiChatResult(
                    aiProperties.getModel(),
                    "好的，已取消本次操作。如果你仍需要我处理，可重新告诉我订单号和诉求。",
                    "",
                    false,
                    null,
                    null,
                    false,
                    null,
                    "",
                    null
            );
        }

        Optional<ActionHandler> actionHandler = StringUtils.hasText(message)
                ? actionRegistry.resolve(message)
                : Optional.empty();
        if (actionHandler.isPresent()) {
            ActionHandler handler = actionHandler.get();
            if (!StringUtils.hasText(context.orderNo())) {
                return new AiChatResult(
                        aiProperties.getModel(),
                        handler.missingOrderReply(),
                        "",
                        false,
                        null,
                        null,
                        false,
                        null,
                        "",
                        null
                );
            }
            PendingAction pendingAction = confirmationService.create(
                    context.user().userId(),
                    context.orderNo(),
                    handler.actionType(),
                    handler.remark()
            );
            Map<String, Object> suggested = new LinkedHashMap<>();
            suggested.put("actionType", handler.actionType());
            suggested.put("orderNo", context.orderNo());
            return new AiChatResult(
                    aiProperties.getModel(),
                    handler.readyReply(context.orderNo()),
                    "",
                    true,
                    pendingAction.token(),
                    suggested,
                    false,
                    null,
                    "",
                    null
            );
        }

        return new AiChatResult(
                aiProperties.getModel(),
                "我暂时没识别到明确的售后诉求，请告诉我你想办理退货、换货还是平台介入，并提供订单号。",
                "",
                false,
                null,
                null,
                false,
                null,
                "",
                null
        );
    }

    private Map<String, Object> executeAfterSaleAction(String authorizationHeader, PendingAction action) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "登录态无效，请重新登录后再试");
        }
        Map<String, Object> conversation = chatServiceClient.openAfterSale(authorizationHeader, action.orderNo(), null);
        if (conversation == null || conversation.get("id") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "创建售后会话失败，请稍后重试");
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

    private boolean isConfirm(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("确认") || normalized.contains("同意") || normalized.contains("ok") || normalized.contains("yes");
    }
}


