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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiCustomerServiceActionTest {

    @Test
    void shouldCreatePendingActionForExchangeIntent() {
        AiProperties properties = new AiProperties();
        RagService ragService = new RagService(properties, new DefaultResourceLoader());
        ConfirmationService confirmationService = new ConfirmationService(properties);
        ChatServiceClient chatServiceClient = mock(ChatServiceClient.class);
        OrderServiceClient orderServiceClient = mock(OrderServiceClient.class);
        ChatClient chatClient = mock(ChatClient.class);
        ActionRegistry actionRegistry = buildRegistry();

        AiCustomerService service = new AiCustomerService(
                chatClient,
                properties,
                ragService,
                confirmationService,
                chatServiceClient,
                orderServiceClient,
                actionRegistry
        );

        AuthenticatedUser user = new AuthenticatedUser(1L, "user", "用户", UserRole.USER, List.of(), 0, "STANDARD");
        AiChatResult result = service.chat(
                user,
                "Bearer test-token",
                new AiChatCommand("我要换货，订单号 ORDER-202604150001", null, null, false)
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
        ChatServiceClient chatServiceClient = mock(ChatServiceClient.class);
        OrderServiceClient orderServiceClient = mock(OrderServiceClient.class);
        ChatClient chatClient = mock(ChatClient.class);
        ActionRegistry actionRegistry = buildRegistry();

        when(chatServiceClient.openAfterSale("Bearer test-token", "ORDER-202604150002", null))
                .thenReturn(Map.of("id", 1001L));
        when(chatServiceClient.applyAfterSaleAction("Bearer test-token", 1001L, "REQUEST_ADMIN_INTERVENTION", "AI客服发起平台介入"))
                .thenReturn(Map.of("ok", true));

        AiCustomerService service = new AiCustomerService(
                chatClient,
                properties,
                ragService,
                confirmationService,
                chatServiceClient,
                orderServiceClient,
                actionRegistry
        );

        AuthenticatedUser user = new AuthenticatedUser(2L, "user2", "用户2", UserRole.USER, List.of(), 0, "STANDARD");
        AiChatResult prepare = service.chat(
                user,
                "Bearer test-token",
                new AiChatCommand("我要投诉，申请平台介入，订单号 ORDER-202604150002", null, null, false)
        );

        AiChatResult confirmed = service.chat(
                user,
                "Bearer test-token",
                new AiChatCommand("确认", null, prepare.confirmationToken(), true)
        );

        assertTrue(confirmed.executed());
        assertTrue(confirmed.reply().contains("平台介入"));
    }

    private ActionRegistry buildRegistry() {
        return new ActionRegistry(List.of(
                new AdminInterventionActionHandler(),
                new ExchangeActionHandler(),
                new ReturnActionHandler()
        ));
    }
}
