package org.example.service;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MerchantCatalogClient {

    private static final String MERCHANT_SERVICE_ID = "ecommerce-merchant-service";

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAP =
            new ParameterizedTypeReference<>() {
            };

    private final DiscoveryClient discoveryClient;
    private final RestTemplate restTemplate;

    public MerchantCatalogClient(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
        this.restTemplate = new RestTemplate();
    }

    public List<Map<String, Object>> hotProducts(int limit) {
        return getList("/api/merchant/products/hot", Map.of("limit", limit));
    }

    public List<Map<String, Object>> listStores(int limit) {
        return getList("/api/merchant/public/stores", Map.of("limit", limit));
    }

    public List<Map<String, Object>> searchProducts(String keyword, String sort, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("keyword", keyword);
        params.put("sort", sort);
        params.put("limit", limit);
        return getList("/api/merchant/public/products/search", params);
    }

    public List<Map<String, Object>> searchStores(String keyword, String sort, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("keyword", keyword);
        params.put("sort", sort);
        params.put("limit", limit);
        return getList("/api/merchant/public/stores/search", params);
    }

    public Map<String, Object> productDetail(Long productId) {
        return getMap("/api/merchant/public/products/{id}", Map.of("id", productId), "商品不存在");
    }

    public Map<String, Object> storeDetail(Long storeId) {
        return getMap("/api/merchant/public/stores/{id}", Map.of("id", storeId), "店铺不存在");
    }

    public List<Map<String, Object>> storeProducts(Long storeId, int limit) {
        return getList("/api/merchant/public/stores/{id}/products", Map.of(
                "id", storeId,
                "limit", limit
        ));
    }

    public List<Map<String, Object>> recommendProducts(List<String> keywords, int limit) {
        String keywordValue = keywords == null || keywords.isEmpty() ? "" : String.join(",", keywords);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("keywords", keywordValue);
        params.put("limit", limit);
        return getList("/api/merchant/public/products/recommend", params);
    }

    private List<Map<String, Object>> getList(String path, Map<String, Object> params) {
        try {
            URI uri = buildUri(path, params);
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(uri, HttpMethod.GET, null, LIST_OF_MAP);
            return response.getBody() == null ? Collections.emptyList() : response.getBody();
        } catch (HttpStatusCodeException ex) {
            throw new ResponseStatusException(resolveStatus(ex), "商家目录服务请求失败", ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "商家目录服务请求失败", ex);
        }
    }

    private Map<String, Object> getMap(String path, Map<String, Object> params, String notFoundMessage) {
        try {
            URI uri = buildUri(path, params);
            Map<?, ?> body = restTemplate.getForObject(uri, Map.class);
            if (body == null) {
                return Collections.emptyMap();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            body.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        } catch (HttpStatusCodeException ex) {
            throw new ResponseStatusException(resolveStatus(ex), resolveMessage(ex, notFoundMessage), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "商家目录服务请求失败", ex);
        }
    }

    private HttpStatus resolveStatus(HttpStatusCodeException ex) {
        return ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value() ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
    }

    private String resolveMessage(HttpStatusCodeException ex, String notFoundMessage) {
        return ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value() ? notFoundMessage : "商家目录服务请求失败";
    }

    private URI buildUri(String path, Map<String, Object> params) {
        URI baseUri = resolveBaseUri();
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(trimTrailingSlash(baseUri.toString()) + path);
        Map<String, Object> uriVariables = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                if (path.contains("{" + key + "}")) {
                    uriVariables.put(key, value);
                    return;
                }
                builder.queryParam(key, value);
            }
        });
        return builder.buildAndExpand(uriVariables).encode().toUri();
    }

    private URI resolveBaseUri() {
        List<ServiceInstance> instances = discoveryClient.getInstances(MERCHANT_SERVICE_ID);
        if (instances == null || instances.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "商家目录服务不可用");
        }
        ServiceInstance instance = instances.get(0);
        return URI.create(instance.getUri().toString());
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}


