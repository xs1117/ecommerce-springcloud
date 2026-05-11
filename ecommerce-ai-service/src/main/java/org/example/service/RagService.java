package org.example.service;

import org.example.config.AiProperties;
import org.example.rag.EmbeddingService;
import org.example.rag.KnowledgeChunk;
import org.example.rag.KnowledgeChunker;
import org.example.rag.KnowledgeDocument;
import org.example.rag.KnowledgeDocumentLoader;
import org.example.rag.QdrantKnowledgeStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final AiProperties aiProperties;
    private final ResourceLoader resourceLoader;
    private final KnowledgeDocumentLoader knowledgeDocumentLoader;
    private final KnowledgeChunker knowledgeChunker;
    private final EmbeddingService embeddingService;
    private final QdrantKnowledgeStore qdrantKnowledgeStore;

    public RagService(AiProperties aiProperties, ResourceLoader resourceLoader) {
        this(aiProperties, resourceLoader, null, null, null, null);
    }

    @Autowired
    public RagService(AiProperties aiProperties,
                      ResourceLoader resourceLoader,
                      KnowledgeDocumentLoader knowledgeDocumentLoader,
                      KnowledgeChunker knowledgeChunker,
                      EmbeddingService embeddingService,
                      QdrantKnowledgeStore qdrantKnowledgeStore) {
        this.aiProperties = aiProperties;
        this.resourceLoader = resourceLoader;
        this.knowledgeDocumentLoader = knowledgeDocumentLoader;
        this.knowledgeChunker = knowledgeChunker;
        this.embeddingService = embeddingService;
        this.qdrantKnowledgeStore = qdrantKnowledgeStore;
    }

    public String findContext(String query) {
        if (StringUtils.hasText(query) && canUseVectorRetrieval()) {
            try {
                float[] vector = embeddingService.embed(query);
                List<QdrantKnowledgeStore.RetrievedChunk> hits = qdrantKnowledgeStore.search(vector, Math.max(1, aiProperties.getRagTopK()));
                String context = formatVectorHits(hits);
                if (StringUtils.hasText(context)) {
                    return context;
                }
            } catch (Exception ex) {
                // fall back to local keyword search
            }
        }

        List<String> chunks = loadKnowledgeChunks();
        if (chunks.isEmpty()) {
            return "";
        }
        String normalized = normalize(query);
        List<ScoredChunk> scored = chunks.stream()
                .map(chunk -> new ScoredChunk(chunk, score(chunk, normalized)))
                .filter(scoredChunk -> scoredChunk.score > 0)
                .sorted(Comparator.comparingInt((ScoredChunk it) -> it.score).reversed())
                .limit(Math.max(1, aiProperties.getRagTopK()))
                .toList();

        if (scored.isEmpty()) {
            return "";
        }

        return scored.stream()
                .map(ScoredChunk::content)
                .collect(Collectors.joining("\n\n"));
    }

    private boolean canUseVectorRetrieval() {
        return qdrantKnowledgeStore != null
                && embeddingService != null
                && qdrantKnowledgeStore.isAvailable();
    }

    private String formatVectorHits(List<QdrantKnowledgeStore.RetrievedChunk> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        double minimumScore = Math.max(0d, aiProperties.getRag().getMinimumScore());
        return hits.stream()
                .filter(hit -> hit.score() >= minimumScore)
                .map(hit -> {
                    String title = StringUtils.hasText(hit.title()) ? hit.title() : extractSourceLabel(hit.source());
                    String content = StringUtils.hasText(hit.content()) ? hit.content().trim() : "";
                    return "【" + title + " · " + String.format(Locale.ROOT, "%.3f", hit.score()) + "】\n" + content;
                })
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n\n"));
    }

    private List<String> loadKnowledgeChunks() {
        List<String> result = new ArrayList<>();
        if (knowledgeDocumentLoader != null && knowledgeChunker != null) {
            List<KnowledgeDocument> documents = knowledgeDocumentLoader.loadDocuments();
            for (KnowledgeDocument document : documents) {
                for (KnowledgeChunk chunk : knowledgeChunker.chunk(document)) {
                    if (StringUtils.hasText(chunk.content())) {
                        result.add(chunk.content());
                    }
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }

        for (String location : aiProperties.getRag().getKnowledgeFiles()) {
            if (location == null || location.isBlank()) {
                continue;
            }
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                continue;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder current = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        flushChunk(result, current);
                    } else {
                        if (!current.isEmpty()) {
                            current.append('\n');
                        }
                        current.append(line.trim());
                    }
                }
                flushChunk(result, current);
            } catch (Exception ignored) {
                // Ignore broken knowledge file to keep service alive.
            }
        }
        return result;
    }

    private void flushChunk(List<String> result, StringBuilder current) {
        if (current.isEmpty()) {
            return;
        }
        String chunk = current.toString().trim();
        if (!chunk.isEmpty()) {
            result.add(chunk);
        }
        current.setLength(0);
    }

    private int score(String chunk, String query) {
        String normalizedChunk = normalize(chunk);
        if (query.isBlank() || normalizedChunk.isBlank()) {
            return 0;
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : query.split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
                tokens.addAll(extractChineseFragments(token));
            }
        }
        int score = 0;
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (normalizedChunk.contains(token)) {
                score += token.length() >= 4 ? 2 : 1;
            }
        }
        if (normalizedChunk.contains(query)) {
            score += 4;
        }
        return score;
    }

    private List<String> extractChineseFragments(String token) {
        String onlyChinese = token.replaceAll("[^\\u4e00-\\u9fa5]", "");
        if (onlyChinese.length() < 2) {
            return List.of();
        }
        List<String> fragments = new ArrayList<>();
        for (int i = 0; i <= onlyChinese.length() - 2; i++) {
            fragments.add(onlyChinese.substring(i, i + 2));
        }
        return fragments;
    }

    private String normalize(String text) {
        return Objects.toString(text, "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String extractSourceLabel(String source) {
        if (!StringUtils.hasText(source)) {
            return "知识片段";
        }
        int slash = Math.max(source.lastIndexOf('/'), source.lastIndexOf('\\'));
        return slash >= 0 && slash + 1 < source.length() ? source.substring(slash + 1) : source;
    }

    private record ScoredChunk(String content, int score) {
    }
}

