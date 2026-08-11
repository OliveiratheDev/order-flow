package com.start.overflow.catalog.dto;

import com.start.overflow.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductFilterTest {
    @Test
    void deveAceitarFiltro_quandoFaixaDePrecoForValida() {
        ProductFilter filter = new ProductFilter(null, null,
                new BigDecimal("10.00"), new BigDecimal("20.00"), null);

        assertThatCode(filter::validatePriceRange).doesNotThrowAnyException();
    }

    @Test
    void deveRecusarFiltro_quandoPrecoMinimoSuperarMaximo() {
        ProductFilter filter = new ProductFilter(null, null,
                new BigDecimal("20.00"), new BigDecimal("10.00"), null);

        assertThatThrownBy(filter::validatePriceRange)
                .isInstanceOf(ValidationException.class)
                .hasMessage("A faixa de preço informada é inválida");
    }
}
