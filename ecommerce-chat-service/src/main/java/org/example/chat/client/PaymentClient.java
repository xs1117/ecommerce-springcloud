package org.example.chat.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class PaymentClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;

    public PaymentClient(@Value("${app.integration.payment.base-url:http://localhost:8088}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> refundByOrderNo(String orderNo) {
        ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(
                baseUrl + "/api/payment/admin/refund/by-order?orderNo=" + orderNo,
                null,
                (Class<Map<String, Object>>) (Class<?>) Map.class
        );
        return response.getBody();
    }
}

