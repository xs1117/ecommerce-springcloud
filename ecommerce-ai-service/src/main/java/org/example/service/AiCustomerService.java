package org.example.service;

import org.example.agent.AfterSaleAgent;
import org.example.agent.AgentRouter;
import org.example.agent.ConversationContext;
import org.example.agent.CustomerAgent;
import org.example.agent.GeneralAnswerAgent;
import org.example.agent.ImageSearchAgent;
import org.example.agent.IntentGuidedShoppingAgent;
import org.example.config.AiProperties;
import org.example.security.AuthenticatedUser;
import org.example.service.action.ActionRegistry;
import org.example.service.dto.AiChatCommand;
import org.example.service.dto.AiChatResult;
import org.example.service.dto.PendingAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class AiCustomerService {

    private static final Logger log = LoggerFactory.getLogger(AiCustomerService.class);
    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("(?:订单号|订单|order)\\s*[:：#-]?\\s*([A-Za-z0-9-]{6,64})", Pattern.CASE_INSENSITIVE);

    private final AgentRouter agentRouter;
    private final ConfirmationService confirmationService;

    public AiCustomerService(ChatClient chatClient,
                             AiProperties aiProperties,
                             RagService ragService,
                             ConfirmationService confirmationService,
                             ReturnReasonSummarizer returnReasonSummarizer,
                             ReplyPolisherService replyPolisherService,
                             ChatServiceClient chatServiceClient,
                             OrderServiceClient orderServiceClient,
                             ActionRegistry actionRegistry) {
        this(chatClient, aiProperties, ragService, confirmationService, returnReasonSummarizer, replyPolisherService, chatServiceClient, orderServiceClient, actionRegistry, null, null, null, null, null);
    }

    @Autowired
    public AiCustomerService(ChatClient chatClient,
                             AiProperties aiProperties,
                             RagService ragService,
                             ConfirmationService confirmationService,
                             ReturnReasonSummarizer returnReasonSummarizer,
                             ReplyPolisherService replyPolisherService,
                             ChatServiceClient chatServiceClient,
                             OrderServiceClient orderServiceClient,
                             ActionRegistry actionRegistry,
                             VisionRecognitionService visionRecognitionService,
                             MerchantCatalogClient merchantCatalogClient,
                             ProductImageCompareService productImageCompareService,
                             ProductImageIndexSyncService productImageIndexSyncService,
                             ProductImageSemanticSearchService productImageSemanticSearchService) {
        this.agentRouter = new AgentRouter(List.of(
                new AfterSaleAgent(aiProperties, confirmationService, chatServiceClient, actionRegistry, returnReasonSummarizer, replyPolisherService),
                new ImageSearchAgent(aiProperties, visionRecognitionService, merchantCatalogClient, productImageCompareService, productImageIndexSyncService, productImageSemanticSearchService, replyPolisherService),
                new IntentGuidedShoppingAgent(chatClient, aiProperties, merchantCatalogClient, replyPolisherService),
                new GeneralAnswerAgent(chatClient, aiProperties, ragService, orderServiceClient, replyPolisherService)
        ));
        this.confirmationService = confirmationService;
    }

    public AiChatResult chat(AuthenticatedUser user, String authorizationHeader, AiChatCommand command) {
        if (command == null) {
            throw new ResponseStatusException(BAD_REQUEST, "请求不能为空");
        }

        String message = StringUtils.hasText(command.message()) ? command.message().trim() : "";
        boolean hasImage = StringUtils.hasText(command.imageUrl());
        if (!StringUtils.hasText(message) && !hasImage) {
            throw new ResponseStatusException(BAD_REQUEST, "message和imageUrl不能同时为空");
        }

        Optional<PendingAction> tokenAction = findPendingAction(user, command);
        String orderNo = resolveOrderNo(command);
        boolean confirmRequested = Boolean.TRUE.equals(command.confirm()) || isConfirm(message);
        boolean cancelRequested = isCancel(message);
        ConversationContext context = new ConversationContext(
                user,
                authorizationHeader,
                command,
                message,
                normalizeMessage(message),
                orderNo,
                hasImage,
                confirmRequested,
                cancelRequested,
                tokenAction.orElse(null),
                command.history()
        );
        CustomerAgent agent = agentRouter.route(context);
        log.info("AI chat routed to agent={}, hasImage={}, confirmRequested={}, cancelRequested={}, orderNoPresent={}, pendingActionPresent={}",
                agent.agentName(),
                hasImage,
                confirmRequested,
                cancelRequested,
                StringUtils.hasText(orderNo),
                tokenAction.isPresent());
        AiChatResult result = agent.handle(context);
        try {
            int replyLen = result.reply() == null ? 0 : result.reply().length();
            int thinkingLen = result.thinking() == null ? 0 : result.thinking().trim().length();
            int recommends = result.recommendProducts() == null ? 0 : result.recommendProducts().size();
            log.info("Agent result summary: agent={}, replyLen={}, thinkingLen={}, recommendCount={}", agent.agentName(), replyLen, thinkingLen, recommends);
        } catch (Exception ex) {
            log.debug("Failed to inspect AiChatResult: {}", ex.getMessage());
        }
        return result;
    }

    private Optional<PendingAction> findPendingAction(AuthenticatedUser user, AiChatCommand command) {
        if (StringUtils.hasText(command.confirmationToken())) {
            return confirmationService.findValidByToken(user.userId(), command.confirmationToken().trim());
        }
        return confirmationService.findLatestValidByUser(user.userId());
    }

    private String normalizeMessage(String message) {
        return StringUtils.hasText(message) ? message.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String resolveOrderNo(AiChatCommand command) {
        if (StringUtils.hasText(command.orderNo())) {
            return command.orderNo().trim();
        }
        if (!StringUtils.hasText(command.message())) {
            return "";
        }
        Matcher matcher = ORDER_NO_PATTERN.matcher(command.message());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private boolean isCancel(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("取消") || normalized.contains("不用了") || normalized.contains("cancel");
    }

    private boolean isConfirm(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("确认") || normalized.contains("同意") || normalized.contains("ok") || normalized.contains("yes");
    }
}

