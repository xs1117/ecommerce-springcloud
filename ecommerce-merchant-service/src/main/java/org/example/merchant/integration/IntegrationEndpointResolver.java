package org.example.merchant.integration;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class IntegrationEndpointResolver {

    private final DiscoveryClient discoveryClient;

    public IntegrationEndpointResolver(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    public String resolveBaseUrl(String serviceId, String fallbackBaseUrl) {
        if (StringUtils.hasText(serviceId)) {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceId.trim());
            if (instances != null && !instances.isEmpty()) {
                return normalizeBaseUrl(instances.getFirst().getUri().toString());
            }
        }
        if (StringUtils.hasText(fallbackBaseUrl)) {
            return normalizeBaseUrl(fallbackBaseUrl);
        }
        throw new IllegalStateException("No available target endpoint for serviceId=" + serviceId);
    }

    private String normalizeBaseUrl(String raw) {
        String target = raw.trim();
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            target = "http://" + target;
        }
        while (target.endsWith("/")) {
            target = target.substring(0, target.length() - 1);
        }
        return target;
    }
}

