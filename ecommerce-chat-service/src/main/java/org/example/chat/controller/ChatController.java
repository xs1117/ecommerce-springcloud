package org.example.chat.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.chat.security.AuthenticatedUser;
import org.example.chat.service.ChatConversationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatConversationService conversationService;

    public ChatController(ChatConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/conversations/open")
    public ResponseEntity<Map<String, Object>> open(Authentication authentication,
                                                    @Valid @RequestBody OpenConversationRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(conversationService.openConversation(
                user,
                request.storeId(),
                request.productId(),
                request.sourceType()
        ));
    }

    @PostMapping("/conversations/open-after-sale")
    public ResponseEntity<Map<String, Object>> openAfterSale(Authentication authentication,
                                                             @Valid @RequestBody OpenAfterSaleConversationRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(conversationService.openAfterSaleConversation(
                user,
                request.orderNo(),
                request.storeId()
        ));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<Map<String, Object>>> list(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(conversationService.listConversations(user));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<Map<String, Object>> detail(Authentication authentication,
                                                      @PathVariable("id") Long conversationId) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(conversationService.getConversation(user, conversationId));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<Map<String, Object>>> messages(Authentication authentication,
                                                             @PathVariable("id") Long conversationId) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(conversationService.listMessages(user, conversationId));
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<Map<String, Object>> send(Authentication authentication,
                                                    @PathVariable("id") Long conversationId,
                                                    @Valid @RequestBody ChatConversationService.SendMessageRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(conversationService.sendMessage(user, conversationId, request));
    }

    @PostMapping("/conversations/{id}/read")
    public ResponseEntity<Map<String, Object>> read(Authentication authentication,
                                                    @PathVariable("id") Long conversationId) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(conversationService.markRead(user, conversationId));
    }

    @PostMapping("/conversations/{id}/after-sale/action")
    public ResponseEntity<Map<String, Object>> afterSaleAction(Authentication authentication,
                                                               @PathVariable("id") Long conversationId,
                                                               @Valid @RequestBody ChatConversationService.AfterSaleActionRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(conversationService.applyAfterSaleAction(user, conversationId, request));
    }

    public record OpenConversationRequest(@NotNull Long storeId, Long productId, String sourceType) {
    }

    public record OpenAfterSaleConversationRequest(@NotBlank String orderNo, Long storeId) {
    }
}

