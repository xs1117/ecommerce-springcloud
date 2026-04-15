package org.example.service;

public record AiChatCommand(
        String message,
        String orderNo,
        String confirmationToken,
        Boolean confirm
) {
}

