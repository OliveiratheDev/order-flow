package com.start.overflow.order.event;

import com.start.overflow.identity.entity.AppUser;
import com.start.overflow.identity.entity.UserRole;
import com.start.overflow.identity.repository.UserRepository;
import com.start.overflow.order.entity.CustomerOrder;
import com.start.overflow.order.entity.OrderProductSnapshot;
import com.start.overflow.order.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = {
        "orderflow.security.jwt.secret=test-secret-with-at-least-thirty-two-bytes",
        "orderflow.bootstrap.admin.enabled=false",
        "spring.cache.type=none",
        "orderflow.payment.reconciliation.cron=0 0 0 1 1 *",
        "orderflow.messaging.rabbit.enabled=true",
        "orderflow.messaging.rabbit.publisher-confirm-timeout=1s"
})
@Testcontainers(disabledWithoutDocker = true)
class OrderDomainEventIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MeterRegistry meterRegistry;
    @MockitoBean RabbitTemplate rabbitTemplate;
    private TransactionTemplate transactions;

    @BeforeEach
    void resetState() {
        transactions = new TransactionTemplate(transactionManager);
        clearInvocations(rabbitTemplate);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(), any(MessagePostProcessor.class),
                any(CorrelationData.class));
        jdbc.execute("TRUNCATE TABLE order_event_audit, payment, order_item, customer_order, "
                + "product, category, app_user RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO category (name, slug, active) "
                + "VALUES ('Categoria', 'categoria', true)");
        jdbc.update("""
                INSERT INTO product (category_id, name, sku, price, stock, active)
                VALUES (1, 'Produto', 'SKU-1', 49.90, 10, true)
                """);
    }

    @Test
    void commitPublishesCreatedEventAndPersistsAuditInTheSameTransaction() {
        double before = metric("OrderCreated");

        CustomerOrder order = createCommittedOrder();

        assertThat(order.getId()).isNotNull();
        assertThat(auditTypes()).containsExactly("OrderCreated");
        assertThat(metric("OrderCreated") - before).isEqualTo(1);
        verify(rabbitTemplate).convertAndSend(
                eq("order.events"), eq("order.created"), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void rollbackDoesNotExecuteBeforeOrAfterCommitListeners() {
        AppUser customer = customer();
        double before = metric("OrderCreated");

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            orderRepository.saveAndFlush(newOrder(customer));
            throw new ExpectedRollbackException();
        })).isInstanceOf(ExpectedRollbackException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_order", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_event_audit", Long.class))
                .isZero();
        assertThat(metric("OrderCreated") - before).isZero();
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void persistedTransitionsPublishPaidAndCancelledFacts() {
        CustomerOrder created = createCommittedOrder();
        double paidBefore = metric("OrderPaid");
        double cancelledBefore = metric("OrderCancelled");

        transactions.executeWithoutResult(status -> {
            CustomerOrder order = orderRepository.findByIdForUpdate(created.getId()).orElseThrow();
            order.markPaid();
            orderRepository.save(order);
        });
        transactions.executeWithoutResult(status -> {
            CustomerOrder order = orderRepository.findByIdForUpdate(created.getId()).orElseThrow();
            order.cancel();
            orderRepository.save(order);
        });

        assertThat(auditTypes())
                .containsExactly("OrderCreated", "OrderPaid", "OrderCancelled");
        assertThat(metric("OrderPaid") - paidBefore).isEqualTo(1);
        assertThat(metric("OrderCancelled") - cancelledBefore).isEqualTo(1);
        verify(rabbitTemplate).convertAndSend(
                eq("order.events"), eq("order.created"), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));
        verify(rabbitTemplate).convertAndSend(
                eq("order.events"), eq("order.paid"), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));
        verify(rabbitTemplate).convertAndSend(
                eq("order.events"), eq("order.cancelled"), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));
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

    private List<String> auditTypes() {
        return jdbc.queryForList(
                "SELECT event_type FROM order_event_audit ORDER BY id", String.class);
    }

    private double metric(String eventType) {
        return meterRegistry.find("orderflow.order.events")
                .tag("event.type", eventType)
                .counter()
                .count();
    }

    private static final class ExpectedRollbackException extends RuntimeException {
    }
}
