package org.example.agent;

import org.example.service.dto.AiChatResult;

public interface CustomerAgent {

    String agentName();

    int priority();

    boolean supports(ConversationContext context);

    AiChatResult handle(ConversationContext context);
}

