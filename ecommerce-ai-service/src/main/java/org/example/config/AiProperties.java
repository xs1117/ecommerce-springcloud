package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String provider = "minimax";
    private boolean llmEnabled = true;
    private String baseUrl = "https://api.whatai.cc";
    private String chatPath = "/v1/chat/completions";
    private String groupId;
    private String model = "MiniMax-M2.7";
    private String apiKey;
    private int chatTimeoutMillis = 20000;
    private int maxRetryAttempts = 3;
    private int retryBackoffMillis = 350;
    private String chatServiceBaseUrl = "http://localhost:8080";
    private int confirmationTtlSeconds = 300;
    private int ragTopK = 3;
    private RagProperties rag = new RagProperties();
    private VisionProperties vision = new VisionProperties();
    private ImageCompareProperties imageCompare = new ImageCompareProperties();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isLlmEnabled() {
        return llmEnabled;
    }

    public void setLlmEnabled(boolean llmEnabled) {
        this.llmEnabled = llmEnabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getChatPath() {
        return chatPath;
    }

    public void setChatPath(String chatPath) {
        this.chatPath = chatPath;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

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

    public int getChatTimeoutMillis() {
        return chatTimeoutMillis;
    }

    public void setChatTimeoutMillis(int chatTimeoutMillis) {
        this.chatTimeoutMillis = chatTimeoutMillis;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }

    public int getRetryBackoffMillis() {
        return retryBackoffMillis;
    }

    public void setRetryBackoffMillis(int retryBackoffMillis) {
        this.retryBackoffMillis = retryBackoffMillis;
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

    public VisionProperties getVision() {
        return vision;
    }

    public void setVision(VisionProperties vision) {
        this.vision = vision;
    }

    public ImageCompareProperties getImageCompare() {
        return imageCompare;
    }

    public void setImageCompare(ImageCompareProperties imageCompare) {
        this.imageCompare = imageCompare;
    }

    public static class ImageCompareProperties {
        private boolean enabled = true;
        private boolean qdrantEnabled = true;
        private String qdrantUrl;
        private String qdrantApiKey;
        private String qdrantCollection = "ecommerce_ai_product_image_index";
        private String imageBaseUrl = "http://localhost:8084";
        private boolean indexOnStartup = true;
        private int syncPageSize = 200;
        private int candidateLimit = 80;
        private int topK = 6;
        private double minimumScore = 0.70d;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isQdrantEnabled() {
            return qdrantEnabled;
        }

        public void setQdrantEnabled(boolean qdrantEnabled) {
            this.qdrantEnabled = qdrantEnabled;
        }

        public String getQdrantUrl() {
            return qdrantUrl;
        }

        public void setQdrantUrl(String qdrantUrl) {
            this.qdrantUrl = qdrantUrl;
        }

        public String getQdrantApiKey() {
            return qdrantApiKey;
        }

        public void setQdrantApiKey(String qdrantApiKey) {
            this.qdrantApiKey = qdrantApiKey;
        }

        public String getQdrantCollection() {
            return qdrantCollection;
        }

        public void setQdrantCollection(String qdrantCollection) {
            this.qdrantCollection = qdrantCollection;
        }

        public String getImageBaseUrl() {
            return imageBaseUrl;
        }

        public void setImageBaseUrl(String imageBaseUrl) {
            this.imageBaseUrl = imageBaseUrl;
        }

        public int getCandidateLimit() {
            return candidateLimit;
        }

        public void setCandidateLimit(int candidateLimit) {
            this.candidateLimit = candidateLimit;
        }

        public boolean isIndexOnStartup() {
            return indexOnStartup;
        }

        public void setIndexOnStartup(boolean indexOnStartup) {
            this.indexOnStartup = indexOnStartup;
        }

        public int getSyncPageSize() {
            return syncPageSize;
        }

        public void setSyncPageSize(int syncPageSize) {
            this.syncPageSize = syncPageSize;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getMinimumScore() {
            return minimumScore;
        }

        public void setMinimumScore(double minimumScore) {
            this.minimumScore = minimumScore;
        }
    }

    public static class VisionProperties {
        private boolean enabled = true;
        private String model = "";
        private String prompt = "请识别图片中的商品，并仅返回JSON对象：{\"productName\":\"\",\"keyword\":\"\",\"confidence\":\"high|medium|low\"}。keyword用于商品检索，应尽量简洁。";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }
    }

    public static class RagProperties {
        private List<String> knowledgeFiles = new ArrayList<>(List.of("classpath:rag/customer-service-knowledge.md"));
        private boolean vectorEnabled = true;
        private boolean indexOnStartup = true;
        private String qdrantUrl = "http://localhost:6333";
        private String qdrantApiKey;
        private String qdrantCollection = "ecommerce_ai_customer_knowledge";
        private String embeddingBaseUrl;
        private String embeddingPath = "/v1/embeddings";
        private String embeddingModel = "text-embedding-3-small";
        private String embeddingApiKey;
        private int embeddingDimension = 256;
        private int chunkMaxChars = 500;
        private int chunkOverlapChars = 80;
        private double minimumScore = 0.2d;

        public List<String> getKnowledgeFiles() {
            return knowledgeFiles;
        }

        public void setKnowledgeFiles(List<String> knowledgeFiles) {
            this.knowledgeFiles = knowledgeFiles;
        }

        public boolean isVectorEnabled() {
            return vectorEnabled;
        }

        public void setVectorEnabled(boolean vectorEnabled) {
            this.vectorEnabled = vectorEnabled;
        }

        public boolean isIndexOnStartup() {
            return indexOnStartup;
        }

        public void setIndexOnStartup(boolean indexOnStartup) {
            this.indexOnStartup = indexOnStartup;
        }

        public String getQdrantUrl() {
            return qdrantUrl;
        }

        public void setQdrantUrl(String qdrantUrl) {
            this.qdrantUrl = qdrantUrl;
        }

        public String getQdrantApiKey() {
            return qdrantApiKey;
        }

        public void setQdrantApiKey(String qdrantApiKey) {
            this.qdrantApiKey = qdrantApiKey;
        }

        public String getQdrantCollection() {
            return qdrantCollection;
        }

        public void setQdrantCollection(String qdrantCollection) {
            this.qdrantCollection = qdrantCollection;
        }

        public String getEmbeddingBaseUrl() {
            return embeddingBaseUrl;
        }

        public void setEmbeddingBaseUrl(String embeddingBaseUrl) {
            this.embeddingBaseUrl = embeddingBaseUrl;
        }

        public String getEmbeddingPath() {
            return embeddingPath;
        }

        public void setEmbeddingPath(String embeddingPath) {
            this.embeddingPath = embeddingPath;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public String getEmbeddingApiKey() {
            return embeddingApiKey;
        }

        public void setEmbeddingApiKey(String embeddingApiKey) {
            this.embeddingApiKey = embeddingApiKey;
        }

        public int getEmbeddingDimension() {
            return embeddingDimension;
        }

        public void setEmbeddingDimension(int embeddingDimension) {
            this.embeddingDimension = embeddingDimension;
        }

        public int getChunkMaxChars() {
            return chunkMaxChars;
        }

        public void setChunkMaxChars(int chunkMaxChars) {
            this.chunkMaxChars = chunkMaxChars;
        }

        public int getChunkOverlapChars() {
            return chunkOverlapChars;
        }

        public void setChunkOverlapChars(int chunkOverlapChars) {
            this.chunkOverlapChars = chunkOverlapChars;
        }

        public double getMinimumScore() {
            return minimumScore;
        }

        public void setMinimumScore(double minimumScore) {
            this.minimumScore = minimumScore;
        }
    }
}

