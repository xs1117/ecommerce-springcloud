package org.example.service;

import org.example.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RagServiceTest {

    @Test
    void shouldReturnKnowledgeContextForReturnQuestion() {
        AiProperties properties = new AiProperties();
        RagService ragService = new RagService(properties, new DefaultResourceLoader());

        String context = ragService.findContext("我要退货，如何申请？");

        assertTrue(context.contains("退货申请流程"));
    }
}

