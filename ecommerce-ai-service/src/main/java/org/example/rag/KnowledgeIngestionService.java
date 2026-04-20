package org.example.rag;

import org.example.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    private final AiProperties aiProperties;
    private final KnowledgeDocumentLoader documentLoader;
    private final KnowledgeChunker chunker;
    private final EmbeddingService embeddingService;
    private final QdrantKnowledgeStore knowledgeStore;

    public KnowledgeIngestionService(AiProperties aiProperties,
                                     KnowledgeDocumentLoader documentLoader,
                                     KnowledgeChunker chunker,
                                     EmbeddingService embeddingService,
                                     QdrantKnowledgeStore knowledgeStore) {
        this.aiProperties = aiProperties;
        this.documentLoader = documentLoader;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.knowledgeStore = knowledgeStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void indexOnStartup() {
        if (!aiProperties.getRag().isIndexOnStartup() || !knowledgeStore.isAvailable()) {
            return;
        }
        try {
            indexKnowledge();
        } catch (Exception ex) {
            log.warn("Knowledge indexing skipped. error={}", ex.getMessage(), ex);
        }
    }

    public void indexKnowledge() {
        List<KnowledgeDocument> documents = documentLoader.loadDocuments();
        if (documents.isEmpty()) {
            return;
        }
        List<KnowledgeChunk> chunks = new ArrayList<>();
        List<float[]> vectors = new ArrayList<>();
        for (KnowledgeDocument document : documents) {
            for (KnowledgeChunk chunk : chunker.chunk(document)) {
                chunks.add(chunk);
                vectors.add(embeddingService.embed(chunk.content()));
            }
        }
        if (chunks.isEmpty()) {
            return;
        }
        knowledgeStore.upsert(chunks, vectors);
        log.info("Indexed {} knowledge chunks into Qdrant collection {}", chunks.size(), aiProperties.getRag().getQdrantCollection());
    }
}

