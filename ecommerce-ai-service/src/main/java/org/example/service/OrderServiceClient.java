package org.example.service;

import org.example.config.AiProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class OrderServiceClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public OrderServiceClient(RestClient.Builder builder, AiProperties aiProperties) {
        this.restClient = builder
                .baseUrl(aiProperties.getChatServiceBaseUrl())
                .build();
    }

    public Map<String, Object> getOrderDetail(String authorizationHeader, String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return Map.of();
        }
        return restClient.get()
                .uri("/api/order/orders/{orderNo}", orderNo)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .body(MAP_TYPE);
    }
}

