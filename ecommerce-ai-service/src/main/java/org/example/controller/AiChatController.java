package org.example.controller;

import jakarta.validation.Valid;
import org.example.security.AuthenticatedUser;
import org.example.service.AiChatCommand;
import org.example.service.AiChatResult;
import org.example.service.AiCustomerService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@Validated
public class AiChatController {

    private final AiCustomerService aiCustomerService;

    public AiChatController(AiCustomerService aiCustomerService) {
        this.aiCustomerService = aiCustomerService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResult> chat(Authentication authentication,
                                             @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                             @Valid @RequestBody ChatRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return ResponseEntity.status(401).build();
        }
        AiChatResult result = aiCustomerService.chat(
                user,
                authorization,
                new AiChatCommand(request.message(), request.imageUrl(), request.orderNo(), request.confirmationToken(), request.confirm())
        );
        return ResponseEntity.ok(result);
    }

    public record ChatRequest(
            String message,
            String imageUrl,
            String orderNo,
            String confirmationToken,
            Boolean confirm
    ) {
    }
}

