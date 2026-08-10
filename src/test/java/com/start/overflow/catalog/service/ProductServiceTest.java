package com.start.overflow.catalog.service;

import com.start.overflow.catalog.dto.CreateProductRequest;
import com.start.overflow.catalog.dto.UpdateStockRequest;
import com.start.overflow.catalog.entity.Category;
import com.start.overflow.catalog.entity.Product;
import com.start.overflow.catalog.mapper.ProductMapper;
import com.start.overflow.catalog.repository.CategoryRepository;
import com.start.overflow.catalog.repository.ProductRepository;
import com.start.overflow.shared.exception.BusinessRuleException;
import com.start.overflow.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ProductMapper mapper;
    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(productRepository, categoryRepository, mapper);
    }

    @Test
    void refusesDuplicateSku() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessRuleException.class);
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportsMissingCategoryWhenCreating() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Categoria");
    }

    @Test
    void appliesStockDeltaUnderPessimisticLock() {
        Product product = new Product(new Category("Casa", null), "Produto", "SKU-1", null,
                BigDecimal.TEN, 5);
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));

        service.adjustStock(10L, new UpdateStockRequest(-2));

        assertThat(product.getStock()).isEqualTo(3);
        verify(productRepository).findByIdForUpdate(10L);
    }

    private CreateProductRequest request() {
        return new CreateProductRequest(1L, "Produto", "sku-1", null,
                BigDecimal.TEN, 0);
    }
}
