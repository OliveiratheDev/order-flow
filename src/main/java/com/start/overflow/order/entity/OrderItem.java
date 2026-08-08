package com.start.overflow.order.entity;

import com.start.overflow.catalog.entity.Product;
import com.start.overflow.shared.exception.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Getter
@Table(name = "order_item")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    protected OrderItem() {
    }

    OrderItem(CustomerOrder order, Product product, int quantity, BigDecimal unitPrice) {
        if (order == null || product == null) {
            throw new ValidationException("Pedido e produto são obrigatórios");
        }
        if (quantity <= 0) {
            throw new ValidationException("A quantidade do item deve ser positiva");
        }
        this.order = order;
        this.product = product;
        this.productName = product.getName();
        this.sku = product.getSku();
        this.quantity = quantity;
        this.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        this.lineTotal = this.unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2);
    }
}
