package com.start.overflow.shared.messaging;

import com.start.overflow.identity.entity.AppUser;
import com.start.overflow.identity.entity.UserRole;
import com.start.overflow.identity.repository.UserRepository;
import com.start.overflow.notification.adapter.in.messaging.OrderPaidMessage;
import com.start.overflow.notification.adapter.out.persistence.NotificationDeliveryStore;
import com.start.overflow.order.adapter.out.messaging.OrderStatusChangedPayload;
import com.start.overflow.order.entity.CustomerOrder;
import com.start.overflow.order.entity.OrderProductSnapshot;
import com.start.overflow.order.repository.OrderRepository;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest(properties = {
        "orderflow.security.jwt.secret=test-secret-with-at-least-thirty-two-bytes",
        "orderflow.bootstrap.admin.enabled=false",
        "spring.cache.type=none",
        "orderflow.messaging.rabbit.enabled=true",
        "orderflow.messaging.rabbit.publisher-confirm-timeout=2s",
        "spring.rabbitmq.listener.simple.retry.max-attempts=3",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=200ms",
        "spring.rabbitmq.listener.simple.concurrency=1",
        "spring.rabbitmq.listener.simple.max-concurrency=1",
        "spring.rabbitmq.listener.simple.prefetch=1",
        "orderflow.payment.reconciliation.cron=0 0 0 1 1 *",
        "orderflow.notification.cleanup-cron=0 0 0 1 1 *"
})
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.Random.class)
class MessagingIntegrationTest {
    private static final String LISTENER_ID = "orderPaidNotificationListener";
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    @Container
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    @Autowired RabbitAdmin rabbitAdmin;
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired RabbitListenerEndpointRegistry listenerRegistry;
    @Autowired Jackson2JsonMessageConverter messageConverter;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean NotificationDeliveryStore deliveries;
    private TransactionTemplate transactions;

    @BeforeEach
    void resetState() {
        transactions = new TransactionTemplate(transactionManager);
        stopConsumer();
        purgeQueues();
        reset(deliveries);
        jdbc.execute("TRUNCATE TABLE notification_delivery, processed_event, "
                + "order_event_audit, payment, order_item, customer_order, product, "
                + "category, app_user RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO category (name, slug, active) "
                + "VALUES ('Categoria', 'categoria', true)");
        jdbc.update("""
                INSERT INTO product (category_id, name, sku, price, stock, active)
                VALUES (1, 'Produto', 'SKU-1', 49.90, 100, true)
                """);
    }

    @AfterEach
    void stopListenerAndClearMdc() {
        stopConsumer();
        MDC.clear();
    }

    @Test
    void createdOrderPublishesCompleteEnvelope() throws Exception {
        CustomerOrder order = createCommittedOrder();

        awaitQueueCount(RabbitTopology.NOTIFICATION_ORDER_CREATED_QUEUE, 1);
        Message message = receive(RabbitTopology.NOTIFICATION_ORDER_CREATED_QUEUE);
        JsonNode json = JsonMapper.builder().findAndAddModules().build()
                .readTree(message.getBody());

        assertThat(json.get("eventId").asText()).isNotBlank();
        assertThat(json.get("eventType").asText()).isEqualTo("OrderCreated");
        assertThat(json.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(Instant.parse(json.get("occurredAt").asText())).isNotNull();
        assertThat(json.get("correlationId").asText()).isNotBlank();
        assertThat(json.get("payload").get("orderId").asLong()).isEqualTo(order.getId());
        assertThat(json.get("payload").get("customerId").asLong())
                .isEqualTo(order.getCustomer().getId());
        assertThat(json.get("payload").get("items").size()).isEqualTo(1);
    }

    @Test
    void rolledBackOrderDoesNotPublishMessage() {
        AppUser customer = customer();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            orderRepository.saveAndFlush(newOrder(customer));
            throw new ExpectedRollbackException();
        })).isInstanceOf(ExpectedRollbackException.class);

        awaitCondition().during(Duration.ofMillis(500)).untilAsserted(() -> {
            assertThat(queueCount(RabbitTopology.NOTIFICATION_ORDER_CREATED_QUEUE)).isZero();
            assertThat(queueCount(RabbitTopology.AUDIT_QUEUE)).isZero();
        });
    }

    @Test
    void paidRoutingKeyReachesOnlyPaidNotificationQueue() {
        publish(RabbitTopology.ORDER_PAID_ROUTING_KEY,
                envelope("OrderPaid", UUID.randomUUID(), new BigDecimal("249.90"),
                        Instant.parse("2026-08-09T20:00:00Z")));

        awaitQueueCount(RabbitTopology.NOTIFICATION_ORDER_PAID_QUEUE, 1);
        awaitCondition().during(Duration.ofMillis(500)).untilAsserted(() ->
                assertThat(queueCount(RabbitTopology.NOTIFICATION_ORDER_CREATED_QUEUE))
                        .isZero());
        assertThat(receive(RabbitTopology.NOTIFICATION_ORDER_PAID_QUEUE)
                .getMessageProperties().getReceivedRoutingKey()).isEqualTo("order.paid");
    }

    @Test
    void auditBindingReceivesAllOrderEventTypes() throws Exception {
        publish(RabbitTopology.ORDER_CREATED_ROUTING_KEY,
                envelope("OrderCreated", UUID.randomUUID(), BigDecimal.ONE, Instant.now()));
        publish(RabbitTopology.ORDER_PAID_ROUTING_KEY,
                envelope("OrderPaid", UUID.randomUUID(), BigDecimal.TEN, Instant.now()));
        publish(RabbitTopology.ORDER_CANCELLED_ROUTING_KEY,
                envelope("OrderCancelled", UUID.randomUUID(), BigDecimal.TEN, Instant.now()));

        awaitQueueCount(RabbitTopology.AUDIT_QUEUE, 3);
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        List<String> eventTypes = List.of(
                mapper.readTree(receive(RabbitTopology.AUDIT_QUEUE).getBody())
                .get("eventType").asText(),
                mapper.readTree(receive(RabbitTopology.AUDIT_QUEUE).getBody())
                .get("eventType").asText(),
                mapper.readTree(receive(RabbitTopology.AUDIT_QUEUE).getBody())
                .get("eventType").asText());

        assertThat(eventTypes)
                .containsExactly("OrderCreated", "OrderPaid", "OrderCancelled");
    }

    @Test
    void paidEventCreatesExactlyOneNotification() {
        startConsumer();
        publishPaid(UUID.randomUUID());

        awaitCondition().untilAsserted(() -> {
            assertThat(tableCount("notification_delivery")).isEqualTo(1);
            assertThat(tableCount("processed_event")).isEqualTo(1);
        });
    }

    @Test
    void duplicatePaidEventCreatesOneNotification() {
        startConsumer();
        UUID eventId = UUID.randomUUID();

        publishPaid(eventId);
        publishPaid(eventId);

        awaitCondition().during(Duration.ofMillis(500)).untilAsserted(() -> {
            assertThat(tableCount("notification_delivery")).isEqualTo(1);
            assertThat(tableCount("processed_event")).isEqualTo(1);
            assertThat(queueCount(RabbitTopology.NOTIFICATION_ORDER_PAID_QUEUE)).isZero();
        });
    }

    @Test
    void transientFailureSucceedsOnThirdAttempt() {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() < 3) {
                throw new TransientDataAccessResourceException("banco temporariamente indisponível");
            }
            return invocation.callRealMethod();
        }).when(deliveries).record(any(), anyString());
        startConsumer();

        publishPaid(UUID.randomUUID());

        awaitCondition().untilAsserted(() -> {
            assertThat(attempts).hasValue(3);
            assertThat(tableCount("notification_delivery")).isEqualTo(1);
            assertThat(tableCount("processed_event")).isEqualTo(1);
        });
    }

    @Test
    void poisonMessageMovesToDlqAndLeavesOriginalQueue() {
        startConsumer();

        publishPoison();

        awaitCondition().untilAsserted(() -> {
            assertThat(queueCount(RabbitTopology.ORDER_EVENTS_DLQ)).isEqualTo(1);
            assertThat(queueCount(RabbitTopology.NOTIFICATION_ORDER_PAID_QUEUE)).isZero();
        });
    }

    @Test
    void poisonMessageDoesNotBlockFollowingValidMessage() {
        startConsumer();

        publishPoison();
        publishPaid(UUID.randomUUID());

        awaitCondition().untilAsserted(() -> {
            assertThat(queueCount(RabbitTopology.ORDER_EVENTS_DLQ)).isEqualTo(1);
            assertThat(queueCount(RabbitTopology.NOTIFICATION_ORDER_PAID_QUEUE)).isZero();
            assertThat(tableCount("notification_delivery")).isEqualTo(1);
        });
    }

    @Test
    void deadLetterContainsOriginAndReasonHeaders() {
        startConsumer();
        publishPoison();

        awaitQueueCount(RabbitTopology.ORDER_EVENTS_DLQ, 1);
        Message deadLetter = receive(RabbitTopology.ORDER_EVENTS_DLQ);
        List<java.util.Map<String, ?>> deaths =
                deadLetter.getMessageProperties().getXDeathHeader();

        assertThat(deaths).isNotEmpty();
        assertThat(deaths.getFirst().get("queue"))
                .isEqualTo(RabbitTopology.NOTIFICATION_ORDER_PAID_QUEUE);
        assertThat(deaths.getFirst().get("reason")).isEqualTo("rejected");
    }

    @Test
    void serializationPreservesAmountScaleAndInstantPrecision() {
        BigDecimal amount = new BigDecimal("249.90");
        Instant occurredAt = Instant.parse("2026-08-09T20:00:00.123456789Z");
        EventEnvelope<OrderStatusChangedPayload> outbound = envelope(
                "OrderPaid", UUID.randomUUID(), amount, occurredAt);

        publish(RabbitTopology.ORDER_PAID_ROUTING_KEY, outbound);

        awaitQueueCount(RabbitTopology.NOTIFICATION_ORDER_PAID_QUEUE, 1);
        Message message = receive(RabbitTopology.NOTIFICATION_ORDER_PAID_QUEUE);
        var targetType = new ParameterizedTypeReference<EventEnvelope<OrderPaidMessage>>() {
        };
        EventEnvelope<?> converted = (EventEnvelope<?>)
                messageConverter.fromMessage(message, targetType);
        OrderPaidMessage payload = (OrderPaidMessage) converted.payload();

        assertThat(payload.total()).isEqualByComparingTo(amount);
        assertThat(payload.total().scale()).isEqualTo(2);
        assertThat(converted.occurredAt()).isEqualTo(occurredAt);
    }

    private void publishPaid(UUID eventId) {
        publish(RabbitTopology.ORDER_PAID_ROUTING_KEY,
                envelope("OrderPaid", eventId, new BigDecimal("249.90"),
                        Instant.parse("2026-08-09T20:00:00Z")));
    }

    private void publishPoison() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(UUID.randomUUID().toString());
        rabbitTemplate.send(
                RabbitTopology.ORDER_EVENTS_EXCHANGE,
                RabbitTopology.ORDER_PAID_ROUTING_KEY,
                new Message("{invalid".getBytes(StandardCharsets.UTF_8), properties));
    }

    private void publish(
            String routingKey,
            EventEnvelope<OrderStatusChangedPayload> envelope
    ) {
        rabbitTemplate.convertAndSend(
                RabbitTopology.ORDER_EVENTS_EXCHANGE, routingKey, envelope);
    }

    private EventEnvelope<OrderStatusChangedPayload> envelope(
            String eventType,
            UUID eventId,
            BigDecimal amount,
            Instant occurredAt
    ) {
        return new EventEnvelope<>(
                eventId, eventType, 1, occurredAt, "correlation-integration-test",
                new OrderStatusChangedPayload(42L, 7L, amount));
    }

    private CustomerOrder createCommittedOrder() {
        AppUser customer = customer();
        return transactions.execute(status -> orderRepository.saveAndFlush(newOrder(customer)));
    }

    private AppUser customer() {
        return userRepository.saveAndFlush(new AppUser(
                "Maria", "maria@example.com", "52998224725", "hash", UserRole.CUSTOMER));
    }

    private CustomerOrder newOrder(AppUser customer) {
        CustomerOrder order = CustomerOrder.builder()
                .customer(customer)
                .shippingAddress("Rua A, 123")
                .addItem(new OrderProductSnapshot(
                        1L, "Produto", "SKU-1", new BigDecimal("49.90")), 1)
                .build();
        order.awaitPayment();
        return order;
    }

    private void startConsumer() {
        MessageListenerContainer listener = listener();
        listener.start();
        awaitCondition().untilAsserted(() -> assertThat(listener.isRunning()).isTrue());
    }

    private void stopConsumer() {
        MessageListenerContainer listener = listenerRegistry.getListenerContainer(LISTENER_ID);
        if (listener != null && listener.isRunning()) {
            listener.stop();
            awaitCondition().untilAsserted(() -> assertThat(listener.isRunning()).isFalse());
        }
    }

    private MessageListenerContainer listener() {
        MessageListenerContainer listener = listenerRegistry.getListenerContainer(LISTENER_ID);
        assertThat(listener).isNotNull();
        return listener;
    }

    private void purgeQueues() {
        rabbitAdmin.purgeQueue(RabbitTopology.NOTIFICATION_ORDER_CREATED_QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitTopology.NOTIFICATION_ORDER_PAID_QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitTopology.AUDIT_QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitTopology.ORDER_EVENTS_DLQ, false);
    }

    private void awaitQueueCount(String queue, long expected) {
        awaitCondition().untilAsserted(() -> assertThat(queueCount(queue)).isEqualTo(expected));
    }

    private long queueCount(String queue) {
        var information = rabbitAdmin.getQueueInfo(queue);
        assertThat(information).isNotNull();
        return information.getMessageCount();
    }

    private Message receive(String queue) {
        Message message = rabbitTemplate.receive(queue);
        assertThat(message).isNotNull();
        return message;
    }

    private long tableCount(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private ConditionFactory awaitCondition() {
        return await().atMost(WAIT_TIMEOUT).pollInterval(POLL_INTERVAL);
    }

    private static final class ExpectedRollbackException extends RuntimeException {
    }
}
