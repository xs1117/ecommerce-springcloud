package org.example.service;

import org.example.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ProductImageIndexSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProductImageIndexSyncService.class);

    private final AiProperties aiProperties;
    private final MerchantCatalogClient merchantCatalogClient;
    private final ProductImageCompareService productImageCompareService;

    private volatile LocalDateTime lastSyncTime;

    public ProductImageIndexSyncService(AiProperties aiProperties,
                                        MerchantCatalogClient merchantCatalogClient,
                                        ProductImageCompareService productImageCompareService) {
        this.aiProperties = aiProperties;
        this.merchantCatalogClient = merchantCatalogClient;
        this.productImageCompareService = productImageCompareService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initOnStartup() {
        AiProperties.ImageCompareProperties settings = aiProperties.getImageCompare();
        if (!settings.isEnabled() || !settings.isIndexOnStartup()) {
            return;
        }
        syncFull();
    }

    @Scheduled(fixedDelayString = "${ai.image-compare.incremental-sync-delay-millis:30000}")
    public void scheduledIncrementalSync() {
        AiProperties.ImageCompareProperties settings = aiProperties.getImageCompare();
        if (!settings.isEnabled()) {
            return;
        }
        if (lastSyncTime == null) {
            syncFull();
            return;
        }
        syncIncremental(lastSyncTime);
    }

    public synchronized void syncFull() {
        AiProperties.ImageCompareProperties settings = aiProperties.getImageCompare();
        long cursor = 0L;
        int total = 0;
        productImageCompareService.resetIndex();
        while (true) {
            Map<String, Object> page = merchantCatalogClient.fetchImageIndexPage(null, cursor, settings.getSyncPageSize());
            List<Map<String, Object>> items = readItems(page);
            if (items.isEmpty()) {
                break;
            }
            productImageCompareService.upsertIndexBatch(items);
            total += items.size();
            long nextCursor = asLong(page.get("nextCursorId"), cursor);
            boolean hasMore = asBoolean(page.get("hasMore"));
            if (!hasMore || nextCursor <= cursor) {
                break;
            }
            cursor = nextCursor;
        }
        lastSyncTime = LocalDateTime.now();
        log.info("Product image full index synced. rows={}, indexed={}", total, productImageCompareService.indexedSize());
    }

    public synchronized void syncIncremental(LocalDateTime updatedAfter) {
        AiProperties.ImageCompareProperties settings = aiProperties.getImageCompare();
        long cursor = 0L;
        int total = 0;
        while (true) {
            Map<String, Object> page = merchantCatalogClient.fetchImageIndexPage(updatedAfter, cursor, settings.getSyncPageSize());
            List<Map<String, Object>> items = readItems(page);
            if (items.isEmpty()) {
                break;
            }
            productImageCompareService.upsertIndexBatch(items);
            total += items.size();
            long nextCursor = asLong(page.get("nextCursorId"), cursor);
            boolean hasMore = asBoolean(page.get("hasMore"));
            if (!hasMore || nextCursor <= cursor) {
                break;
            }
            cursor = nextCursor;
        }
        lastSyncTime = LocalDateTime.now();
        if (total > 0) {
            log.info("Product image incremental sync applied. deltaRows={}, indexed={}", total, productImageCompareService.indexedSize());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readItems(Map<String, Object> page) {
        Object items = page == null ? null : page.get("items");
        if (items instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private long asLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}

