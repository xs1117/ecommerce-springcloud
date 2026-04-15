package org.example.payment.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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
    public static final String PAYMENT_SUCCESS_QUEUE = "order.payment.success.queue";
    public static final String PAYMENT_SUCCESS_DLQ = "order.payment.success.dlq";

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
    public Queue paymentSuccessQueue() {
        return QueueBuilder.durable(PAYMENT_SUCCESS_QUEUE)
                .withArguments(deadLetterArgs(PAYMENT_SUCCESS_DLQ))
                .build();
    }

    @Bean
    public Queue paymentSuccessDlq() {
        return new Queue(PAYMENT_SUCCESS_DLQ, true);
    }

    @Bean
    public Binding paymentSuccessBinding() {
        return BindingBuilder.bind(paymentSuccessQueue()).to(tradeExchange()).with("payment.success");
    }

    @Bean
    public Binding paymentSuccessDlqBinding() {
        return BindingBuilder.bind(paymentSuccessDlq()).to(deadLetterExchange()).with(PAYMENT_SUCCESS_DLQ);
    }

    private Map<String, Object> deadLetterArgs(String dlqRoutingKey) {
        return Map.of(
                "x-dead-letter-exchange", DLX_EXCHANGE,
                "x-dead-letter-routing-key", dlqRoutingKey
        );
    }
}

