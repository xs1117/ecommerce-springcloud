package org.example.service;

public record AiChatCommand(
        String message,
        String imageUrl,
        String orderNo,
        String confirmationToken,
        Boolean confirm
) {
}

