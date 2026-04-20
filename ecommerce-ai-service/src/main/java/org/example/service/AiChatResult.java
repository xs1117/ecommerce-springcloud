package org.example.service;

import java.util.Map;
import java.util.List;

public record AiChatResult(
        String model,
        String reply,
        boolean requiresConfirmation,
        String confirmationToken,
        Map<String, Object> suggestedAction,
        boolean executed,
        Map<String, Object> executionResult,
        String ragContext,
        List<Map<String, Object>> recommendProducts
) {
}

