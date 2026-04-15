package org.example.inventory.mq;

import org.example.inventory.service.InventoryService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class OrderStatusListener {

    private final InventoryService inventoryService;

    public OrderStatusListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @RabbitListener(queues = "inventory.order.closed.queue")
    public void onOrderClosed(Map<String, Object> payload) {
        Object orderNo = payload.get("orderNo");
        if (orderNo != null) {
            inventoryService.releaseByOrderNo(String.valueOf(orderNo));
        }
    }

    @RabbitListener(queues = "inventory.order.paid.queue")
    public void onOrderPaid(Map<String, Object> payload) {
        Object orderNo = payload.get("orderNo");
        if (orderNo != null) {
            inventoryService.confirmByOrderNo(String.valueOf(orderNo));
        }
    }
}

