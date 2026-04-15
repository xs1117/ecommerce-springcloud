package org.example.inventory.mq;

import org.example.inventory.service.InventoryService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class OrderCreatedListener {

    private final InventoryService inventoryService;

    public OrderCreatedListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @RabbitListener(queues = "inventory.order.created.queue")
    public void onOrderCreated(Map<String, Object> payload) {
        List<OrderCreatedEvent.OrderSkuItem> items = new ArrayList<>();
        Object rawItems = payload.get("items");
        if (rawItems instanceof List<?> list) {
            for (Object raw : list) {
                if (raw instanceof Map<?, ?> itemMap) {
                    Long productId = Long.valueOf(String.valueOf(itemMap.get("productId")));
                    Integer quantity = Integer.valueOf(String.valueOf(itemMap.get("quantity")));
                    items.add(new OrderCreatedEvent.OrderSkuItem(productId, quantity));
                }
            }
        }
        OrderCreatedEvent event = new OrderCreatedEvent(
                String.valueOf(payload.getOrDefault("eventId", "")),
                String.valueOf(payload.getOrDefault("orderNo", "")),
                Long.valueOf(String.valueOf(payload.getOrDefault("userId", 0))),
                new BigDecimal(String.valueOf(payload.getOrDefault("payAmount", "0"))),
                items
        );
        inventoryService.tryReserveForOrder(event);
    }
}

