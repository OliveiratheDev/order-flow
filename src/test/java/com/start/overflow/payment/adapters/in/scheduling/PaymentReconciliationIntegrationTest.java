package com.start.overflow.payment.adapters.in.scheduling;

import com.start.overflow.payment.application.PaymentReconciliationAction;
import com.start.overflow.payment.application.PaymentReconciliationUpdater;
import com.start.overflow.payment.domain.GatewayChargeResult;
import com.start.overflow.payment.domain.GatewayChargeStatus;
import com.start.overflow.payment.ports.out.PaymentRepositoryPort;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "orderflow.security.jwt.secret=test-secret-with-at-least-thirty-two-bytes",
        "orderflow.bootstrap.admin.enabled=false",
        "spring.cache.type=none",
        "orderflow.payment.reconciliation.cron=0 0 0 1 1 *"
})
@Testcontainers(disabledWithoutDocker = true)
class PaymentReconciliationIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PaymentRepositoryPort paymentRepository;
    @Autowired PaymentReconciliationUpdater updater;

    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE TABLE shedlock, payment, order_item, customer_order, product, "
                + "category, app_user RESTART IDENTITY CASCADE");
    }

    @Test
    void selectsOnlyPendingAwaitingPaymentInsideAgeWindowAndHonorsLimit() {
        insertCustomerAndOrders();
        insertPayment(1, "PENDING", "2026-08-09T11:00:00Z");
        insertPayment(2, "PENDING", "2026-08-09T11:30:00Z");
        insertPayment(3, "APPROVED", "2026-08-09T11:20:00Z");
        insertPayment(4, "PENDING", "2026-08-09T11:10:00Z");

        var selected = paymentRepository.findPendingIdsForReconciliation(
                Instant.parse("2026-08-02T12:00:00Z"),
                Instant.parse("2026-08-09T11:50:00Z"),
                1);

        assertThat(selected).containsExactly(1L);
    }

    @Test
    void twoApplicationInstancesCannotAcquireTheSameJobLock() {
        LockProvider firstInstance = provider();
        LockProvider secondInstance = provider();
        LockConfiguration configuration = new LockConfiguration(
                Instant.now(), "paymentReconciliation-integration-test",
                Duration.ofMinutes(1), Duration.ZERO);

        var firstLock = firstInstance.lock(configuration);
        var secondLock = secondInstance.lock(configuration);

        assertThat(firstLock).isPresent();
        assertThat(secondLock).isEmpty();
        firstLock.orElseThrow().unlock();
    }

    @Test
    void concurrentWebhookEquivalentUpdateIsAppliedOnlyOnce() throws Exception {
        insertCustomerAndOrders();
        insertPayment(1, "PENDING", "2026-08-09T11:00:00Z");
        GatewayChargeResult approved = new GatewayChargeResult(
                "pay_1", GatewayChargeStatus.APPROVED, null, null,
                new BigDecimal("100.00"));
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return updater.apply(1L, approved);
            });
            var second = executor.submit(() -> {
                start.await();
                return updater.apply(1L, approved);
            });
            start.countDown();

            List<PaymentReconciliationAction> actions = List.of(
                    first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(actions).containsExactlyInAnyOrder(
                    PaymentReconciliationAction.APPROVED,
                    PaymentReconciliationAction.SKIPPED);
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM payment WHERE id = 1", String.class))
                    .isEqualTo("APPROVED");
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM customer_order WHERE id = 1", String.class))
                    .isEqualTo("PAID");
        } finally {
            executor.shutdownNow();
        }
    }

    private LockProvider provider() {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }

    private void insertCustomerAndOrders() {
        jdbc.update("""
                INSERT INTO app_user
                    (name, email, document, password_hash, role, active)
                VALUES ('Cliente', 'cliente@example.com', '52998224725', 'hash', 'CUSTOMER', true)
                """);
        jdbc.update("""
                INSERT INTO customer_order
                    (customer_id, status, subtotal, discount, total, shipping_address)
                VALUES
                    (1, 'AWAITING_PAYMENT', 100, 0, 100, 'Rua 1'),
                    (1, 'AWAITING_PAYMENT', 100, 0, 100, 'Rua 2'),
                    (1, 'AWAITING_PAYMENT', 100, 0, 100, 'Rua 3'),
                    (1, 'CANCELLED', 100, 0, 100, 'Rua 4')
                """);
    }

    private void insertPayment(long orderId, String status, String createdAt) {
        jdbc.update("""
                INSERT INTO payment
                    (order_id, customer_id, external_id, amount, method, status,
                     created_at, updated_at)
                VALUES (?, 1, ?, 100.00, 'PIX', ?, ?::timestamptz, ?::timestamptz)
                """, orderId, "pay_" + orderId, status, createdAt, createdAt);
    }
}
