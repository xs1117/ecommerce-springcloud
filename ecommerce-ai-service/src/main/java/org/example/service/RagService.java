package org.example.service;

import org.example.config.AiProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final AiProperties aiProperties;
    private final ResourceLoader resourceLoader;

    public RagService(AiProperties aiProperties, ResourceLoader resourceLoader) {
        this.aiProperties = aiProperties;
        this.resourceLoader = resourceLoader;
    }

    public String findContext(String query) {
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

    private List<String> loadKnowledgeChunks() {
        List<String> result = new ArrayList<>();
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
        String[] tokens = query.split("\\s+");
        int score = 0;
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (normalizedChunk.contains(token)) {
                score += 2;
            }
        }
        if (normalizedChunk.contains(query)) {
            score += 4;
        }
        return score;
    }

    private String normalize(String text) {
        return Objects.toString(text, "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record ScoredChunk(String content, int score) {
    }
}

