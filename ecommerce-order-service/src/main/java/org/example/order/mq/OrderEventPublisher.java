package org.example.order.mq;

import org.example.order.config.MqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final boolean mqEnabled;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate,
                               @Value("${app.mq.enabled:false}") boolean mqEnabled) {
        this.rabbitTemplate = rabbitTemplate;
        this.mqEnabled = mqEnabled;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        if (!mqEnabled) {
            log.info("mq disabled, skip order.created event: {}", event);
            return;
        }
        rabbitTemplate.convertAndSend(MqConfig.EXCHANGE, "order.created", Map.of(
                "eventId", event.eventId(),
                "orderNo", event.orderNo(),
                "userId", event.userId(),
                "payAmount", event.payAmount(),
                "items", event.items().stream().map(item -> Map.of(
                        "productId", item.productId(),
                        "quantity", item.quantity()
                )).toList()
        ));
    }

    public void publishOrderClosed(String eventId, String orderNo) {
        if (!mqEnabled) {
            log.info("mq disabled, skip order.closed event: {}", orderNo);
            return;
        }
        rabbitTemplate.convertAndSend(MqConfig.EXCHANGE, "order.closed", Map.of(
                "eventId", eventId,
                "orderNo", orderNo
        ));
    }

    public void publishOrderPaid(String eventId, String orderNo) {
        if (!mqEnabled) {
            log.info("mq disabled, skip order.paid event: {}", orderNo);
            return;
        }
        rabbitTemplate.convertAndSend(MqConfig.EXCHANGE, "order.paid", Map.of(
                "eventId", eventId,
                "orderNo", orderNo
        ));
    }

    public void publishOrderPaidDetail(String eventId, String orderNo, List<OrderCreatedEvent.OrderSkuItem> items) {
        if (!mqEnabled) {
            log.info("mq disabled, skip order.paid.detail event: {}", orderNo);
            return;
        }
        rabbitTemplate.convertAndSend(MqConfig.EXCHANGE, "order.paid.detail", Map.of(
                "eventId", eventId,
                "orderNo", orderNo,
                "items", items.stream().map(item -> Map.of(
                        "productId", item.productId(),
                        "quantity", item.quantity()
                )).toList()
        ));
    }
}

