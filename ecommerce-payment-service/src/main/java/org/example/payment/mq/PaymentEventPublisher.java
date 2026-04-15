package org.example.payment.mq;

import org.example.payment.config.MqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final boolean mqEnabled;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate,
                                 @Value("${app.mq.enabled:false}") boolean mqEnabled) {
        this.rabbitTemplate = rabbitTemplate;
        this.mqEnabled = mqEnabled;
    }

    public void publishSuccess(PaymentSuccessEvent event) {
        if (!mqEnabled) {
            log.info("mq disabled, skip payment.success event: {}", event);
            return;
        }
        rabbitTemplate.convertAndSend(MqConfig.EXCHANGE, "payment.success", Map.of(
                "eventId", event.eventId(),
                "orderNo", event.orderNo(),
                "paymentNo", event.paymentNo(),
                "paidAmount", event.paidAmount()
        ));
    }
}

