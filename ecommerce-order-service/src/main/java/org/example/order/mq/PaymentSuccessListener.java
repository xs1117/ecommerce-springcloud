package org.example.order.mq;

import org.example.order.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class PaymentSuccessListener {

    private final OrderService orderService;

    public PaymentSuccessListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = "order.payment.success.queue")
    public void onPaymentSuccess(Map<String, Object> payload) {
        Object orderNo = payload.get("orderNo");
        if (orderNo != null) {
            orderService.markPaid(String.valueOf(orderNo));
        }
    }
}

