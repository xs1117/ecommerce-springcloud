package org.example.rag;

import org.example.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeChunkerTest {

    @Test
    void shouldChunkCustomerServiceKnowledgeAndMarkReturnRules() {
        AiProperties properties = new AiProperties();
        KnowledgeChunker chunker = new KnowledgeChunker(properties);

        KnowledgeDocument document = new KnowledgeDocument(
                "classpath:rag/customer-service-knowledge.md",
                "# AI客服知识库\n\n退货申请流程：\n1. 用户提供订单号。\n2. AI客服识别到退货意图后，先生成待确认操作，不直接执行。\n3. 用户确认后，AI客服调用售后动作接口发起 `APPLY_RETURN`。\n4. 若用户取消，则清除待确认操作，不执行任何业务动作。\n"
        );

        var chunks = chunker.chunk(document);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).content().contains("退货申请流程"));
        assertTrue(chunks.get(0).requiresConfirmation());
    }
}

