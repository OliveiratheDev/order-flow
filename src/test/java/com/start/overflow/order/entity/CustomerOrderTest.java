package com.start.overflow.order.entity;

import com.start.overflow.identity.entity.AppUser;
import com.start.overflow.identity.entity.UserRole;
import com.start.overflow.shared.exception.InvalidTransitionException;
import com.start.overflow.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerOrderTest {
    @Test
    void followsEveryValidStateTransition() {
        CustomerOrder order = order();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        order.awaitPayment();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        order.markPaid();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        order.ship();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        order.deliver();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void allowsCancellationBeforeShipping() {
        CustomerOrder created = order();
        created.cancel();
        assertThat(created.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        CustomerOrder awaiting = order();
        awaiting.awaitPayment();
        awaiting.cancel();
        assertThat(awaiting.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        CustomerOrder paid = order();
        paid.awaitPayment();
        paid.markPaid();
        paid.cancel();
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void rejectsInvalidTransitionsInCreatedAndAwaitingPayment() {
        CustomerOrder created = order();
        assertThatThrownBy(created::markPaid).isInstanceOf(InvalidTransitionException.class);

        CustomerOrder awaiting = order();
        awaiting.awaitPayment();
        assertThatThrownBy(awaiting::ship).isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void shippedOrderCannotBeCancelled() {
        CustomerOrder shipped = paidOrder();
        shipped.ship();

        assertThatThrownBy(shipped::cancel).isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void finalStatesRejectEveryFurtherTransition() {
        CustomerOrder delivered = paidOrder();
        delivered.ship();
        delivered.deliver();
        assertThatThrownBy(delivered::cancel).isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(delivered::deliver).isInstanceOf(InvalidTransitionException.class);

        CustomerOrder cancelled = order();
        cancelled.cancel();
        assertThatThrownBy(cancelled::awaitPayment).isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(cancelled::markPaid).isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void builderCalculatesSnapshotTotalsOnServer() {
        OrderProductSnapshot product = product(new BigDecimal("249.90"));
        CustomerOrder order = CustomerOrder.builder()
                .customer(customer())
                .shippingAddress("Rua A, 123")
                .addItem(product, 2)
                .discount(new BigDecimal("10.00"))
                .build();

        assertThat(order.getSubtotal()).isEqualByComparingTo("499.80");
        assertThat(order.getDiscount()).isEqualByComparingTo("10.00");
        assertThat(order.getTotal()).isEqualByComparingTo("489.80");
        assertThat(order.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("249.90");
    }

    @Test
    void builderRejectsOrderWithoutItems() {
        assertThatThrownBy(() -> CustomerOrder.builder()
                .customer(customer())
                .shippingAddress("Rua A, 123")
                .build()).isInstanceOf(ValidationException.class);
    }

    private CustomerOrder order() {
        return CustomerOrder.builder()
                .customer(customer())
                .shippingAddress("Rua A, 123")
                .addItem(product(BigDecimal.TEN), 1)
                .build();
    }

    private CustomerOrder paidOrder() {
        CustomerOrder order = order();
        order.awaitPayment();
        order.markPaid();
        return order;
    }

    private AppUser customer() {
        return new AppUser("Maria", "maria@example.com", "52998224725",
                "$2a$12$hash", UserRole.CUSTOMER);
    }

    private OrderProductSnapshot product(BigDecimal price) {
        return new OrderProductSnapshot(1L, "Fone", "FONE-1", price);
    }
}
