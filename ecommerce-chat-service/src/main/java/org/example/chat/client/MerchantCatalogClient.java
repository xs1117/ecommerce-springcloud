package org.example.chat.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class MerchantCatalogClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;

    public MerchantCatalogClient(@Value("${app.integration.merchant.base-url:http://localhost:8084}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> publicStoreDetail(Long storeId) {
        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl + "/api/merchant/public/stores/" + storeId, Map.class);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> publicProductDetail(Long productId) {
        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl + "/api/merchant/public/products/" + productId, Map.class);
        return response.getBody();
    }
}

