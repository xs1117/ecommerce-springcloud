package org.example.agent;

import org.example.config.AiProperties;
import org.example.service.AiChatResult;
import org.example.service.ChatServiceClient;
import org.example.service.ConfirmationService;
import org.example.service.PendingAction;
import org.example.service.ReplyPolisherService;
import org.example.service.ReturnReasonSummarizer;
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

    private static final String ACTION_APPLY_RETURN = "APPLY_RETURN";

    private final AiProperties aiProperties;
    private final ConfirmationService confirmationService;
    private final ChatServiceClient chatServiceClient;
    private final ActionRegistry actionRegistry;
    private final ReturnReasonSummarizer returnReasonSummarizer;
    private final ReplyPolisherService replyPolisherService;

    public AfterSaleAgent(AiProperties aiProperties,
                          ConfirmationService confirmationService,
                          ChatServiceClient chatServiceClient,
                          ActionRegistry actionRegistry,
                          ReturnReasonSummarizer returnReasonSummarizer,
                          ReplyPolisherService replyPolisherService) {
        this.aiProperties = aiProperties;
        this.confirmationService = confirmationService;
        this.chatServiceClient = chatServiceClient;
        this.actionRegistry = actionRegistry;
        this.returnReasonSummarizer = returnReasonSummarizer;
        this.replyPolisherService = replyPolisherService;
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
        if (context.hasPendingAction() && context.pendingAction().waitingReturnReason()) {
            return true;
        }
        return context.hasTextMessage() && actionRegistry.resolve(context.message()).isPresent();
    }

    @Override
    public AiChatResult handle(ConversationContext context) {
        String message = context.message();
        PendingAction pendingAction = context.pendingAction();

        if (pendingAction != null && pendingAction.waitingReturnReason()) {
            if (context.cancelRequested()) {
                confirmationService.invalidate(pendingAction);
                String reply = polishReply("售后-取消退货原因收集", "好的，已取消本次退货申请。如果你还需要处理，我随时可以继续帮你。", message, "afterSaleStatus=cancelled, actionType=" + pendingAction.actionType(), null);
                return new AiChatResult(
                        aiProperties.getModel(),
                        reply,
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

            String reasonSummary = returnReasonSummarizer == null ? "其他原因" : returnReasonSummarizer.summarizeForMerchant(message);
            if (!StringUtils.hasText(reasonSummary)) {
                reasonSummary = "其他原因";
            }

            PendingAction confirmAction = confirmationService.create(
                    context.user().userId(),
                    pendingAction.orderNo(),
                    pendingAction.actionType(),
                    pendingAction.remark(),
                    PendingAction.STAGE_CONFIRMATION,
                    reasonSummary
            );
            confirmationService.invalidate(pendingAction);

            Map<String, Object> suggested = new LinkedHashMap<>();
            suggested.put("actionType", pendingAction.actionType());
            suggested.put("orderNo", pendingAction.orderNo());
            suggested.put("reasonSummary", reasonSummary);

            String reply = polishReply(
                    "售后-退货原因确认",
                    "收到，你本次退货原因我已记录为：" + reasonSummary + "。我将把这个原因同步给商家。请确认是否现在发起退货申请？",
                    message,
                    "actionType=" + pendingAction.actionType() + ", orderNo=" + pendingAction.orderNo() + ", reasonSummary=" + reasonSummary,
                    null
            );

            return new AiChatResult(
                    aiProperties.getModel(),
                    reply,
                    "",
                    true,
                    confirmAction.token(),
                    suggested,
                    false,
                    null,
                    "",
                    null
            );
        }

        if (context.confirmRequested() || isConfirm(message)) {
            PendingAction action = Optional.ofNullable(pendingAction)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "确认令牌无效或已过期"));
            Map<String, Object> result = executeAfterSaleAction(context.authorizationHeader(), action);
            confirmationService.invalidate(action);
            String reply = polishReply("售后-确认执行", actionRegistry.executedReply(action.actionType()), message, "actionType=" + action.actionType() + ", orderNo=" + action.orderNo() + ", reasonSummary=" + action.reasonSummary(), null);
            return new AiChatResult(
                    aiProperties.getModel(),
                    reply,
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
            confirmationService.invalidate(pendingAction);
            String reply = polishReply("售后-取消待办", "好的，已取消本次操作。如果你仍需要我处理，可重新告诉我订单号和诉求。", message, "actionType=" + pendingAction.actionType() + ", orderNo=" + pendingAction.orderNo(), null);
            return new AiChatResult(
                    aiProperties.getModel(),
                    reply,
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
            PendingAction createdAction = confirmationService.create(
                    context.user().userId(),
                    context.orderNo(),
                    handler.actionType(),
                    handler.remark(),
                    ACTION_APPLY_RETURN.equals(handler.actionType())
                            ? PendingAction.STAGE_COLLECT_RETURN_REASON
                            : PendingAction.STAGE_CONFIRMATION,
                    null
            );
            Map<String, Object> suggested = new LinkedHashMap<>();
            suggested.put("actionType", handler.actionType());
            suggested.put("orderNo", context.orderNo());
            if (ACTION_APPLY_RETURN.equals(handler.actionType())) {
                String reply = polishReply("售后-引导退货原因", "我可以帮你申请退货。为提高处理效率，先告诉我这次退货的主要原因，我会连同申请一起发给商家。", message, "actionType=" + handler.actionType() + ", orderNo=" + context.orderNo(), null);
                return new AiChatResult(
                        aiProperties.getModel(),
                        reply,
                        "",
                        false,
                        createdAction.token(),
                        suggested,
                        false,
                        null,
                        "",
                        null
                );
            }
            String reply = polishReply("售后-待确认", handler.readyReply(context.orderNo()), message, "actionType=" + handler.actionType() + ", orderNo=" + context.orderNo(), null);
            return new AiChatResult(
                    aiProperties.getModel(),
                    reply,
                    "",
                    true,
                    createdAction.token(),
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
                action.remark(),
                action.reasonSummary()
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

    private String polishReply(String scenario, String draftReply, String userMessage, String facts, String historyContext) {
        return replyPolisherService == null
                ? draftReply
                : replyPolisherService.polish(scenario, draftReply, userMessage, facts, historyContext);
    }

}


