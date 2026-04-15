package org.example.service;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class UserContentClient {

    private static final String USER_SERVICE_ID = "ecommerce-user-service";

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAP =
            new ParameterizedTypeReference<>() {
            };

    private final DiscoveryClient discoveryClient;
    private final RestTemplate restTemplate;

    public UserContentClient(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
        this.restTemplate = new RestTemplate();
    }

    public List<Map<String, Object>> activeNotices() {
        try {
            URI uri = UriComponentsBuilder.fromUriString(resolveBaseUrl() + "/api/user/coupons/notices").build().toUri();
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(uri, HttpMethod.GET, null, LIST_OF_MAP);
            return response.getBody() == null ? Collections.emptyList() : response.getBody();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private String resolveBaseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(USER_SERVICE_ID);
        if (instances == null || instances.isEmpty()) {
            throw new IllegalStateException("user service unavailable");
        }
        String uri = instances.get(0).getUri().toString();
        return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }
}

