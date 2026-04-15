package org.example.inventory.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class MqConfig {

    public static final String EXCHANGE = "ecommerce.trade.exchange";
    public static final String DLX_EXCHANGE = "ecommerce.trade.dlx";
    public static final String ORDER_CREATED_QUEUE = "inventory.order.created.queue";
    public static final String ORDER_CREATED_DLQ = "inventory.order.created.dlq";
    public static final String INVENTORY_RESERVED_QUEUE = "order.inventory.reserved.queue";
    public static final String INVENTORY_RESERVED_DLQ = "order.inventory.reserved.dlq";
    public static final String INVENTORY_ALERT_QUEUE = "inventory.alert.queue";
    public static final String INVENTORY_ALERT_DLQ = "inventory.alert.dlq";
    public static final String ORDER_CLOSED_QUEUE = "inventory.order.closed.queue";
    public static final String ORDER_CLOSED_DLQ = "inventory.order.closed.dlq";
    public static final String ORDER_PAID_QUEUE = "inventory.order.paid.queue";
    public static final String ORDER_PAID_DLQ = "inventory.order.paid.dlq";

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange tradeExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(ORDER_CREATED_QUEUE)
                .withArguments(deadLetterArgs(ORDER_CREATED_DLQ))
                .build();
    }

    @Bean
    public Queue orderCreatedDlq() {
        return new Queue(ORDER_CREATED_DLQ, true);
    }

    @Bean
    public Queue inventoryReservedQueue() {
        return QueueBuilder.durable(INVENTORY_RESERVED_QUEUE)
                .withArguments(deadLetterArgs(INVENTORY_RESERVED_DLQ))
                .build();
    }

    @Bean
    public Queue inventoryReservedDlq() {
        return new Queue(INVENTORY_RESERVED_DLQ, true);
    }

    @Bean
    public Queue inventoryAlertQueue() {
        return QueueBuilder.durable(INVENTORY_ALERT_QUEUE)
                .withArguments(deadLetterArgs(INVENTORY_ALERT_DLQ))
                .build();
    }

    @Bean
    public Queue inventoryAlertDlq() {
        return new Queue(INVENTORY_ALERT_DLQ, true);
    }

    @Bean
    public Queue orderClosedQueue() {
        return QueueBuilder.durable(ORDER_CLOSED_QUEUE)
                .withArguments(deadLetterArgs(ORDER_CLOSED_DLQ))
                .build();
    }

    @Bean
    public Queue orderClosedDlq() {
        return new Queue(ORDER_CLOSED_DLQ, true);
    }

    @Bean
    public Queue orderPaidQueue() {
        return QueueBuilder.durable(ORDER_PAID_QUEUE)
                .withArguments(deadLetterArgs(ORDER_PAID_DLQ))
                .build();
    }

    @Bean
    public Queue orderPaidDlq() {
        return new Queue(ORDER_PAID_DLQ, true);
    }

    @Bean
    public Binding orderCreatedBinding() {
        return BindingBuilder.bind(orderCreatedQueue()).to(tradeExchange()).with("order.created");
    }

    @Bean
    public Binding orderCreatedDlqBinding() {
        return BindingBuilder.bind(orderCreatedDlq()).to(deadLetterExchange()).with(ORDER_CREATED_DLQ);
    }

    @Bean
    public Binding inventoryReservedBinding() {
        return BindingBuilder.bind(inventoryReservedQueue()).to(tradeExchange()).with("inventory.reserved");
    }

    @Bean
    public Binding inventoryReservedDlqBinding() {
        return BindingBuilder.bind(inventoryReservedDlq()).to(deadLetterExchange()).with(INVENTORY_RESERVED_DLQ);
    }

    @Bean
    public Binding inventoryAlertBinding() {
        return BindingBuilder.bind(inventoryAlertQueue()).to(tradeExchange()).with("inventory.alert");
    }

    @Bean
    public Binding inventoryAlertDlqBinding() {
        return BindingBuilder.bind(inventoryAlertDlq()).to(deadLetterExchange()).with(INVENTORY_ALERT_DLQ);
    }

    @Bean
    public Binding orderClosedBinding() {
        return BindingBuilder.bind(orderClosedQueue()).to(tradeExchange()).with("order.closed");
    }

    @Bean
    public Binding orderClosedDlqBinding() {
        return BindingBuilder.bind(orderClosedDlq()).to(deadLetterExchange()).with(ORDER_CLOSED_DLQ);
    }

    @Bean
    public Binding orderPaidBinding() {
        return BindingBuilder.bind(orderPaidQueue()).to(tradeExchange()).with("order.paid");
    }

    @Bean
    public Binding orderPaidDlqBinding() {
        return BindingBuilder.bind(orderPaidDlq()).to(deadLetterExchange()).with(ORDER_PAID_DLQ);
    }

    private Map<String, Object> deadLetterArgs(String dlqRoutingKey) {
        return Map.of(
                "x-dead-letter-exchange", DLX_EXCHANGE,
                "x-dead-letter-routing-key", dlqRoutingKey
        );
    }
}


