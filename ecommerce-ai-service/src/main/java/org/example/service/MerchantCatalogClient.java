package org.example.service;

import org.example.config.AiProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.net.URI;

@Component
public class MerchantCatalogClient {

    private static final String MERCHANT_SERVICE_ID = "ecommerce-merchant-service";

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAP = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final DiscoveryClient discoveryClient;
    private final AiProperties aiProperties;

    public MerchantCatalogClient(RestClient.Builder builder,
                                 AiProperties aiProperties,
                                 DiscoveryClient discoveryClient) {
        this.restClient = builder
                .baseUrl(aiProperties.getChatServiceBaseUrl())
                .build();
        this.aiProperties = aiProperties;
        this.discoveryClient = discoveryClient;
    }

    public List<Map<String, Object>> searchPublicProducts(String keyword, int limit) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/merchant/public/products/search")
                        .queryParam("keyword", keyword)
                        .queryParam("sort", "relevance")
                        .queryParam("limit", Math.max(1, limit))
                        .build())
                .retrieve()
                .body(LIST_OF_MAP);
    }

    public List<Map<String, Object>> listPublicProductsForCompare(int limit) {
        return searchPublicProducts("", Math.max(1, limit));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchImageIndexPage(LocalDateTime updatedAfter, long cursorId, int limit) {
        String path = "/api/merchant/internal/products/index?cursorId=" + Math.max(0L, cursorId)
                + "&limit=" + Math.max(1, limit)
                + (updatedAfter == null ? "" : "&updatedAfter=" + updatedAfter);
        return RestClient.builder()
                .baseUrl(resolveMerchantBaseUrl())
                .build()
                .get()
                .uri(path)
                .retrieve()
                .body(Map.class);
    }

    private String resolveMerchantBaseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(MERCHANT_SERVICE_ID);
        if (instances != null && !instances.isEmpty()) {
            URI uri = instances.get(0).getUri();
            String value = uri.toString();
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        }
        String fallback = aiProperties.getChatServiceBaseUrl();
        return fallback.endsWith("/") ? fallback.substring(0, fallback.length() - 1) : fallback;
    }
}




