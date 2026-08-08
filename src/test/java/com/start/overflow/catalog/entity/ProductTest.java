package com.start.overflow.catalog.entity;

import com.start.overflow.shared.exception.BusinessRuleException;
import com.start.overflow.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {
    private final Category category = new Category("Eletrônicos", null);

    @Test
    void normalizesSkuPriceAndStock() {
        Product product = new Product(category, "Fone", " fone-01 ", null,
                new BigDecimal("249.9"), 10);

        assertThat(product.getSku()).isEqualTo("FONE-01");
        assertThat(product.getPrice()).isEqualByComparingTo("249.90");
        assertThat(product.getStock()).isEqualTo(10);
    }

    @Test
    void adjustsStockWithPositiveAndNegativeDelta() {
        Product product = productWithStock(10);

        product.adjustStock(5);
        product.adjustStock(-3);

        assertThat(product.getStock()).isEqualTo(12);
    }

    @Test
    void refusesNegativeStockAfterAdjustment() {
        Product product = productWithStock(2);

        assertThatThrownBy(() -> product.adjustStock(-3))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(product.getStock()).isEqualTo(2);
    }

    @Test
    void reservesAndRestoresStock() {
        Product product = productWithStock(5);

        product.reserveStock(3);
        product.restoreStock(3);

        assertThat(product.getStock()).isEqualTo(5);
    }

    @Test
    void rejectsZeroPrice() {
        assertThatThrownBy(() -> new Product(category, "Fone", "F-1", null,
                BigDecimal.ZERO, 0)).isInstanceOf(ValidationException.class);
    }

    private Product productWithStock(int stock) {
        return new Product(category, "Fone", "F-1", null, BigDecimal.TEN, stock);
    }
}
