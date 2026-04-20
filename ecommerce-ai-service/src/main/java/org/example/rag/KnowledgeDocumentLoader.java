package org.example.rag;

import org.example.config.AiProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class KnowledgeDocumentLoader {

    private final AiProperties aiProperties;
    private final ResourceLoader resourceLoader;

    public KnowledgeDocumentLoader(AiProperties aiProperties, ResourceLoader resourceLoader) {
        this.aiProperties = aiProperties;
        this.resourceLoader = resourceLoader;
    }

    public List<KnowledgeDocument> loadDocuments() {
        List<KnowledgeDocument> documents = new ArrayList<>();
        for (String location : aiProperties.getRag().getKnowledgeFiles()) {
            if (!StringUtils.hasText(location)) {
                continue;
            }
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                continue;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                documents.add(new KnowledgeDocument(location, builder.toString()));
            } catch (Exception ignored) {
                // ignore a broken knowledge file to keep the service running
            }
        }
        return documents;
    }
}

