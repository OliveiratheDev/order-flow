package com.start.overflow.shared.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "orderflow.messaging.rabbit.enabled", havingValue = "true")
public class RabbitMessagingConfig {
    private static final Logger log = LoggerFactory.getLogger(RabbitMessagingConfig.class);

    @Bean
    TopicExchange orderEventsExchange() {
        return new TopicExchange(RabbitTopology.ORDER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange orderEventsDeadLetterExchange() {
        return new DirectExchange(RabbitTopology.ORDER_EVENTS_DLX, true, false);
    }

    @Bean
    Queue notificationOrderCreatedQueue() {
        return workQueue(RabbitTopology.NOTIFICATION_ORDER_CREATED_QUEUE);
    }

    @Bean
    Queue notificationOrderPaidQueue() {
        return workQueue(RabbitTopology.NOTIFICATION_ORDER_PAID_QUEUE);
    }

    @Bean
    Queue auditQueue() {
        return workQueue(RabbitTopology.AUDIT_QUEUE);
    }

    @Bean
    Queue orderEventsDeadLetterQueue() {
        return QueueBuilder.durable(RabbitTopology.ORDER_EVENTS_DLQ).build();
    }

    @Bean
    Binding notificationOrderCreatedBinding(TopicExchange orderEventsExchange,
                                            Queue notificationOrderCreatedQueue) {
        return BindingBuilder.bind(notificationOrderCreatedQueue)
                .to(orderEventsExchange)
                .with(RabbitTopology.ORDER_CREATED_ROUTING_KEY);
    }

    @Bean
    Binding notificationOrderPaidBinding(TopicExchange orderEventsExchange,
                                         Queue notificationOrderPaidQueue) {
        return BindingBuilder.bind(notificationOrderPaidQueue)
                .to(orderEventsExchange)
                .with(RabbitTopology.ORDER_PAID_ROUTING_KEY);
    }

    @Bean
    Binding auditBinding(TopicExchange orderEventsExchange, Queue auditQueue) {
        return BindingBuilder.bind(auditQueue)
                .to(orderEventsExchange)
                .with(RabbitTopology.ALL_ORDER_EVENTS_PATTERN);
    }

    @Bean
    Binding deadLetterBinding(DirectExchange orderEventsDeadLetterExchange,
                              Queue orderEventsDeadLetterQueue) {
        return BindingBuilder.bind(orderEventsDeadLetterQueue)
                .to(orderEventsDeadLetterExchange)
                .with(RabbitTopology.DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    MessageConverter rabbitJsonMessageConverter() {
        return new JacksonJsonMessageConverter("com.start.overflow");
    }

    @Bean
    SmartInitializingSingleton rabbitPublisherCallbacks(RabbitTemplate rabbitTemplate) {
        return () -> {
            rabbitTemplate.setConfirmCallback((correlation, acknowledged, cause) -> {
                if (!acknowledged) {
                    log.atError()
                            .addKeyValue("correlationData", correlation)
                            .addKeyValue("cause", cause)
                            .log("Broker não confirmou a publicação AMQP");
                }
            });
            rabbitTemplate.setReturnsCallback(returned -> log.atWarn()
                    .addKeyValue("exchange", returned.getExchange())
                    .addKeyValue("routingKey", returned.getRoutingKey())
                    .addKeyValue("replyCode", returned.getReplyCode())
                    .addKeyValue("replyText", returned.getReplyText())
                    .log("Mensagem AMQP não encontrou binding"));
        };
    }

    private Queue workQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(RabbitTopology.ORDER_EVENTS_DLX)
                .deadLetterRoutingKey(RabbitTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
    }
}
