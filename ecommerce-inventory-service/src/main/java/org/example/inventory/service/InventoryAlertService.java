package org.example.inventory.service;

import org.example.inventory.domain.InventorySku;
import org.example.inventory.mq.InventoryAlertEvent;
import org.example.inventory.mq.InventoryEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class InventoryAlertService {

    private final InventoryEventPublisher eventPublisher;

    public InventoryAlertService(InventoryEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Async("bizExecutor")
    public void checkAndAlert(InventorySku sku) {
        if (sku.getAvailableStock() <= sku.getWarnThreshold()) {
            eventPublisher.publishAlert(new InventoryAlertEvent(
                    UUID.randomUUID().toString(),
                    sku.getProductId(),
                    sku.getAvailableStock(),
                    sku.getWarnThreshold()
            ));
        }
    }
}

