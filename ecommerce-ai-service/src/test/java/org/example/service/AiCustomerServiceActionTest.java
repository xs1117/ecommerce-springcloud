package org.example.service;

import org.example.config.AiProperties;
import org.example.security.AuthenticatedUser;
import org.example.security.UserRole;
import org.example.service.action.ActionRegistry;
import org.example.service.action.AdminInterventionActionHandler;
import org.example.service.action.ExchangeActionHandler;
import org.example.service.action.ReturnActionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCustomerServiceActionTest {

    @Test
    void shouldCreatePendingActionForExchangeIntent() {
        AiProperties properties = new AiProperties();
        RagService ragService = new RagService(properties, new DefaultResourceLoader());
        ConfirmationService confirmationService = new ConfirmationService(properties);
        ReturnReasonSummarizer returnReasonSummarizer = mock(ReturnReasonSummarizer.class);
        ReplyPolisherService replyPolisherService = mock(ReplyPolisherService.class);
        ChatServiceClient chatServiceClient = mock(ChatServiceClient.class);
        OrderServiceClient orderServiceClient = mock(OrderServiceClient.class);
        ChatClient chatClient = mock(ChatClient.class);
        ActionRegistry actionRegistry = buildRegistry();

        when(replyPolisherService.polish(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        AiCustomerService service = new AiCustomerService(
                chatClient,
                properties,
                ragService,
                confirmationService,
                returnReasonSummarizer,
                replyPolisherService,
                chatServiceClient,
                orderServiceClient,
                actionRegistry
        );

        AuthenticatedUser user = new AuthenticatedUser(1L, "user", "用户", UserRole.USER, List.of(), 0, "STANDARD");
        AiChatResult result = service.chat(
                user,
                "Bearer test-token",
                new AiChatCommand("我要换货，订单号 ORDER-202604150001", null, null, null, false, List.of())
        );

        assertTrue(result.requiresConfirmation());
        assertNotNull(result.confirmationToken());
        assertEquals("APPLY_EXCHANGE", result.suggestedAction().get("actionType"));
    }

    @Test
    void shouldExecuteAdminInterventionAfterConfirmation() {
        AiProperties properties = new AiProperties();
        RagService ragService = new RagService(properties, new DefaultResourceLoader());
        ConfirmationService confirmationService = new ConfirmationService(properties);
        ReturnReasonSummarizer returnReasonSummarizer = mock(ReturnReasonSummarizer.class);
        ReplyPolisherService replyPolisherService = mock(ReplyPolisherService.class);
        ChatServiceClient chatServiceClient = mock(ChatServiceClient.class);
        OrderServiceClient orderServiceClient = mock(OrderServiceClient.class);
        ChatClient chatClient = mock(ChatClient.class);
        ActionRegistry actionRegistry = buildRegistry();

        when(replyPolisherService.polish(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        when(chatServiceClient.openAfterSale("Bearer test-token", "ORDER-202604150002", null))
                .thenReturn(Map.of("id", 1001L));
        when(chatServiceClient.applyAfterSaleAction("Bearer test-token", 1001L, "REQUEST_ADMIN_INTERVENTION", "AI客服发起平台介入", null))
                .thenReturn(Map.of("ok", true));

        AiCustomerService service = new AiCustomerService(
                chatClient,
                properties,
                ragService,
                confirmationService,
                returnReasonSummarizer,
                replyPolisherService,
                chatServiceClient,
                orderServiceClient,
                actionRegistry
        );

        AuthenticatedUser user = new AuthenticatedUser(2L, "user2", "用户2", UserRole.USER, List.of(), 0, "STANDARD");
        AiChatResult prepare = service.chat(
                user,
                "Bearer test-token",
                new AiChatCommand("我要投诉，申请平台介入，订单号 ORDER-202604150002", null, null, null, false, List.of())
        );

        AiChatResult confirmed = service.chat(
                user,
                "Bearer test-token",
                new AiChatCommand("确认", null, null, prepare.confirmationToken(), true, List.of())
        );

        assertTrue(confirmed.executed());
        assertTrue(confirmed.reply().contains("平台介入"));
    }

    @Test
    void shouldCollectReturnReasonBeforeConfirmation() {
        AiProperties properties = new AiProperties();
        RagService ragService = new RagService(properties, new DefaultResourceLoader());
        ConfirmationService confirmationService = new ConfirmationService(properties);
        ReturnReasonSummarizer returnReasonSummarizer = mock(ReturnReasonSummarizer.class);
        ReplyPolisherService replyPolisherService = mock(ReplyPolisherService.class);
        ChatServiceClient chatServiceClient = mock(ChatServiceClient.class);
        OrderServiceClient orderServiceClient = mock(OrderServiceClient.class);
        ChatClient chatClient = mock(ChatClient.class);
        ActionRegistry actionRegistry = buildRegistry();

        when(replyPolisherService.polish(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        when(returnReasonSummarizer.summarizeForMerchant("衣服尺码偏小，试穿后明显不合适"))
                .thenReturn("商品尺码偏小，建议退货处理");

        when(chatServiceClient.openAfterSale("Bearer test-token", "ORDER-202604150003", null))
                .thenReturn(Map.of("id", 1002L));
        when(chatServiceClient.applyAfterSaleAction(
                eq("Bearer test-token"),
                eq(1002L),
                eq("APPLY_RETURN"),
                eq("AI客服发起退货"),
                eq("商品尺码偏小，建议退货处理")
        )).thenReturn(Map.of("ok", true));

        AiCustomerService service = new AiCustomerService(
                chatClient,
                properties,
                ragService,
                confirmationService,
                returnReasonSummarizer,
                replyPolisherService,
                chatServiceClient,
                orderServiceClient,
                actionRegistry
        );

        AuthenticatedUser user = new AuthenticatedUser(3L, "user3", "用户3", UserRole.USER, List.of(), 0, "STANDARD");

        AiChatResult askReason = service.chat(
                user,
                "Bearer test-token",
                new AiChatCommand("我要退货，订单号 ORDER-202604150003", null, null, null, false, List.of())
        );
        assertTrue(askReason.reply().contains("退货的主要原因"));
        assertFalse(askReason.requiresConfirmation());

        AiChatResult askConfirm = service.chat(
                user,
                "Bearer test-token",
                new AiChatCommand("衣服尺码偏小，试穿后明显不合适", null, null, askReason.confirmationToken(), false, List.of())
        );
        assertTrue(askConfirm.requiresConfirmation());
        assertEquals("商品尺码偏小，建议退货处理", askConfirm.suggestedAction().get("reasonSummary"));
        assertNotNull(askConfirm.reply());
        assertFalse(askConfirm.reply().contains("衣服尺码偏小"));

        AiChatResult confirmed = service.chat(
                user,
                "Bearer test-token",
                new AiChatCommand("确认", null, null, askConfirm.confirmationToken(), true, List.of())
        );

        assertTrue(confirmed.executed());
        verify(returnReasonSummarizer).summarizeForMerchant("衣服尺码偏小，试穿后明显不合适");
        verify(chatServiceClient).applyAfterSaleAction(
                "Bearer test-token",
                1002L,
                "APPLY_RETURN",
                "AI客服发起退货",
                "商品尺码偏小，建议退货处理"
        );
    }

    private ActionRegistry buildRegistry() {
        return new ActionRegistry(List.of(
                new AdminInterventionActionHandler(),
                new ExchangeActionHandler(),
                new ReturnActionHandler()
        ));
    }
}
