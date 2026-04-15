package org.example.inventory.mq;

import org.example.inventory.config.MqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final boolean mqEnabled;

    public InventoryEventPublisher(RabbitTemplate rabbitTemplate,
                                  @Value("${app.mq.enabled:false}") boolean mqEnabled) {
        this.rabbitTemplate = rabbitTemplate;
        this.mqEnabled = mqEnabled;
    }

    public void publishReserved(InventoryReservedEvent event) {
        if (!mqEnabled) {
            log.info("mq disabled, skip inventory.reserved event: {}", event);
            return;
        }
        rabbitTemplate.convertAndSend(MqConfig.EXCHANGE, "inventory.reserved", Map.of(
                "eventId", event.eventId(),
                "orderNo", event.orderNo(),
                "success", event.success(),
                "reason", event.reason() == null ? "" : event.reason()
        ));
    }

    public void publishAlert(InventoryAlertEvent event) {
        if (!mqEnabled) {
            log.warn("stock alert: {}", event);
            return;
        }
        rabbitTemplate.convertAndSend(MqConfig.EXCHANGE, "inventory.alert", Map.of(
                "eventId", event.eventId(),
                "productId", event.productId(),
                "availableStock", event.availableStock(),
                "warnThreshold", event.warnThreshold()
        ));
    }
}

