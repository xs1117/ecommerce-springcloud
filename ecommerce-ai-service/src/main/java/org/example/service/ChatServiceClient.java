package org.example.service;

import org.example.config.AiProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class ChatServiceClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public ChatServiceClient(RestClient.Builder builder, AiProperties aiProperties) {
        this.restClient = builder
                .baseUrl(aiProperties.getChatServiceBaseUrl())
                .build();
    }

    public Map<String, Object> openAfterSale(String authorizationHeader, String orderNo, Long storeId) {
        Map<String, Object> payload = storeId == null
                ? Map.of("orderNo", orderNo)
                : Map.of("orderNo", orderNo, "storeId", storeId);
        return restClient.post()
                .uri("/api/chat/conversations/open-after-sale")
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(MAP_TYPE);
    }

    public Map<String, Object> applyAfterSaleAction(String authorizationHeader,
                                                    Long conversationId,
                                                    String actionType,
                                                    String remark,
                                                    String reasonSummary) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("actionType", actionType);
        if (remark != null && !remark.isBlank()) {
            payload.put("remark", remark);
        }
        if (reasonSummary != null && !reasonSummary.isBlank()) {
            payload.put("reasonSummary", reasonSummary);
        }
        return restClient.post()
                .uri("/api/chat/conversations/{id}/after-sale/action", conversationId)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(MAP_TYPE);
    }
}

