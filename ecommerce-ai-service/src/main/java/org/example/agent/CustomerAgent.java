package org.example.agent;

import org.example.service.AiChatResult;

public interface CustomerAgent {

    String agentName();

    int priority();

    boolean supports(ConversationContext context);

    AiChatResult handle(ConversationContext context);
}

