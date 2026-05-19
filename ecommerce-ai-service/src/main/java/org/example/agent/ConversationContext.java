package org.example.agent;

import org.example.security.AuthenticatedUser;
import org.example.service.dto.AiChatCommand;
import org.example.service.dto.ChatTurn;
import org.example.service.dto.PendingAction;
import org.springframework.util.StringUtils;

import java.util.List;

public record ConversationContext(
        AuthenticatedUser user,
        String authorizationHeader,
        AiChatCommand command,
        String message,
        String normalizedMessage,
        String orderNo,
        boolean hasImage,
        boolean confirmRequested,
        boolean cancelRequested,
        PendingAction pendingAction,
        List<ChatTurn> history
) {

    public boolean hasPendingAction() {
        return pendingAction != null;
    }

    public boolean hasTextMessage() {
        return StringUtils.hasText(message);
    }

    public boolean hasHistory() {
        return history != null && !history.isEmpty();
    }
}

