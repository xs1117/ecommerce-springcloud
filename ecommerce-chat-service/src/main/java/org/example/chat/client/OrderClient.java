package org.example.chat.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class OrderClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;

    public OrderClient(@Value("${app.integration.order.base-url:http://localhost:8087}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> orderDetail(String orderNo) {
        ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(
                baseUrl + "/api/order/orders/" + orderNo,
                (Class<Map<String, Object>>) (Class<?>) Map.class
        );
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> forceCancelAfterSale(String orderNo) {
        return postForMap(
                "/api/order/internal/orders/" + orderNo + "/after-sale/force-cancel",
                "/api/order/orders/" + orderNo + "/after-sale/force-cancel"
        );
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> forceRefundAfterSale(String orderNo) {
        return postForMap(
                "/api/order/internal/orders/" + orderNo + "/after-sale/force-refund",
                "/api/order/orders/" + orderNo + "/after-sale/force-refund"
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postForMap(String primaryPath, String fallbackPath) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(
                    baseUrl + primaryPath,
                    null,
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );
            return response.getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            ResponseEntity<Map<String, Object>> fallback = restTemplate.postForEntity(
                    baseUrl + fallbackPath,
                    null,
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );
            return fallback.getBody();
        }
    }
}



