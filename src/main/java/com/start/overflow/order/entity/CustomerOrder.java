package com.start.overflow.order.entity;

import com.start.overflow.catalog.entity.Product;
import com.start.overflow.identity.entity.AppUser;
import com.start.overflow.order.state.OrderState;
import com.start.overflow.shared.exception.ValidationException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Getter
@Table(name = "customer_order")
public class CustomerOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private AppUser customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Transient
    private OrderState state;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "shipping_address", nullable = false, length = 255)
    private String shippingAddress;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected CustomerOrder() {
    }

    private CustomerOrder(Builder builder) {
        if (builder.customer == null || !Boolean.TRUE.equals(builder.customer.getActive())) {
            throw new ValidationException("O cliente ativo é obrigatório");
        }
        if (builder.shippingAddress == null || builder.shippingAddress.isBlank()) {
            throw new ValidationException("O endereço de entrega é obrigatório");
        }
        if (builder.items.isEmpty()) {
            throw new ValidationException("O pedido deve possuir ao menos um item");
        }
        this.customer = builder.customer;
        this.shippingAddress = normalizeAddress(builder.shippingAddress);
        this.status = OrderStatus.CREATED;
        this.state = OrderState.from(status);
        builder.items.forEach(item -> this.items.add(
                new OrderItem(this, item.product(), item.quantity(), item.product().getPrice())));
        this.subtotal = items.stream().map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        this.discount = normalizeDiscount(builder.discount);
        if (discount.compareTo(subtotal) > 0) {
            throw new ValidationException("O desconto não pode superar o subtotal");
        }
        this.total = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
    }

    public static Builder builder() {
        return new Builder();
    }

    public void awaitPayment() {
        transition(state.awaitPayment());
    }

    public void markPaid() {
        transition(state.pay());
    }

    public void ship() {
        transition(state.ship());
    }

    public void deliver() {
        transition(state.deliver());
    }

    public void cancel() {
        transition(state.cancel());
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    @PostLoad
    private void restoreState() {
        this.state = OrderState.from(status);
    }

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = Instant.now();
    }

    private void transition(OrderState nextState) {
        this.state = nextState;
        this.status = nextState.status();
    }

    private static String normalizeAddress(String value) {
        String normalized = value.strip();
        if (normalized.length() > 255) {
            throw new ValidationException("O endereço deve ter no máximo 255 caracteres");
        }
        return normalized;
    }

    private static BigDecimal normalizeDiscount(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (value.signum() < 0) {
            throw new ValidationException("O desconto não pode ser negativo");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static final class Builder {
        private AppUser customer;
        private String shippingAddress;
        private BigDecimal discount = BigDecimal.ZERO;
        private final List<ItemDraft> items = new ArrayList<>();

        private Builder() { }

        public Builder customer(AppUser customer) {
            this.customer = customer;
            return this;
        }

        public Builder shippingAddress(String shippingAddress) {
            this.shippingAddress = shippingAddress;
            return this;
        }

        public Builder discount(BigDecimal discount) {
            this.discount = discount;
            return this;
        }

        public Builder addItem(Product product, int quantity) {
            if (product == null) {
                throw new ValidationException("O produto é obrigatório");
            }
            if (quantity <= 0) {
                throw new ValidationException("A quantidade do item deve ser positiva");
            }
            this.items.add(new ItemDraft(product, quantity));
            return this;
        }

        public CustomerOrder build() {
            return new CustomerOrder(this);
        }
    }

    private record ItemDraft(Product product, int quantity) { }
}
