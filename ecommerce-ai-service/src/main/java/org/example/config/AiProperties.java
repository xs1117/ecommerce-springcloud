package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String model = "gpt-4o-mini";
    private String apiKey;
    private String chatServiceBaseUrl = "http://localhost:8080";
    private int confirmationTtlSeconds = 300;
    private int ragTopK = 3;
    private RagProperties rag = new RagProperties();

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getChatServiceBaseUrl() {
        return chatServiceBaseUrl;
    }

    public void setChatServiceBaseUrl(String chatServiceBaseUrl) {
        this.chatServiceBaseUrl = chatServiceBaseUrl;
    }

    public int getConfirmationTtlSeconds() {
        return confirmationTtlSeconds;
    }

    public void setConfirmationTtlSeconds(int confirmationTtlSeconds) {
        this.confirmationTtlSeconds = confirmationTtlSeconds;
    }

    public int getRagTopK() {
        return ragTopK;
    }

    public void setRagTopK(int ragTopK) {
        this.ragTopK = ragTopK;
    }

    public RagProperties getRag() {
        return rag;
    }

    public void setRag(RagProperties rag) {
        this.rag = rag;
    }

    public static class RagProperties {
        private List<String> knowledgeFiles = new ArrayList<>(List.of("classpath:rag/customer-service-knowledge.md"));

        public List<String> getKnowledgeFiles() {
            return knowledgeFiles;
        }

        public void setKnowledgeFiles(List<String> knowledgeFiles) {
            this.knowledgeFiles = knowledgeFiles;
        }
    }
}

