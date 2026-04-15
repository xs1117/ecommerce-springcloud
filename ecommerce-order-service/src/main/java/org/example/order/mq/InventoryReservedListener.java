package org.example.order.mq;

import org.example.order.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class InventoryReservedListener {

    private final OrderService orderService;

    public InventoryReservedListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = "order.inventory.reserved.queue")
    public void onInventoryReserved(Map<String, Object> payload) {
        InventoryReservedEvent event = new InventoryReservedEvent(
                String.valueOf(payload.getOrDefault("eventId", "")),
                String.valueOf(payload.getOrDefault("orderNo", "")),
                Boolean.parseBoolean(String.valueOf(payload.getOrDefault("success", false))),
                String.valueOf(payload.getOrDefault("reason", ""))
        );
        orderService.handleInventoryReserved(event);
    }
}

