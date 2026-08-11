package com.start.overflow.catalog.service;

import com.start.overflow.catalog.dto.CreateProductRequest;
import com.start.overflow.catalog.dto.ProductResponse;
import com.start.overflow.catalog.dto.UpdateProductRequest;
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
import org.mockito.ArgumentCaptor;
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
    @Mock
    ProductRepository productRepository;
    @Mock
    CategoryRepository categoryRepository;
    @Mock
    ProductMapper mapper;
    private ProductService service;

    @BeforeEach
    void configurarCenario() {
        service = new ProductService(productRepository, categoryRepository, mapper);
    }

    @Test
    void deveRecusarCriacao_quandoSkuEstiverDuplicado() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BusinessRuleException.class);
        verify(productRepository, never()).saveAndFlush(any());
        verify(categoryRepository, never()).findById(any());
    }

    @Test
    void deveInformarCategoriaAusente_quandoCriarProduto() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Categoria");
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarCriacao_quandoCategoriaEstiverInativa() {
        Category category = new Category("Casa", null);
        category.deactivate();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BusinessRuleException.class);
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveCriarProduto_quandoDadosForemValidos() {
        Category category = new Category("Casa", null);
        ProductResponse response = response();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Product.class))).thenReturn(response);

        ProductResponse result = service.create(createRequest());

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSku()).isEqualTo("SKU-1");
        assertThat(result).isSameAs(response);
    }

    @Test
    void deveAplicarAjuste_quandoEstoqueForSuficiente() {
        Product product = productWithStock(5);
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(mapper.toResponse(product)).thenReturn(response());

        service.adjustStock(10L, new UpdateStockRequest(-2));

        assertThat(product.getStock()).isEqualTo(3);
        verify(productRepository).findByIdForUpdate(10L);
    }

    @Test
    void deveRecusarAjuste_quandoEstoqueFicarNegativo() {
        Product product = productWithStock(1);
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.adjustStock(10L, new UpdateStockRequest(-2)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(product.getStock()).isEqualTo(1);
        verify(mapper, never()).toResponse(any(Product.class));
    }

    @Test
    void deveInformarProdutoAusente_quandoBuscarPorId() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deveTrocarCategoria_quandoAtualizarProduto() {
        Category original = new Category("Casa", null);
        Category target = new Category("Tecnologia", null);
        Product product = new Product(original, "Produto", "SKU-1", null, BigDecimal.TEN, 5);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(target));
        when(mapper.toResponse(product)).thenReturn(response());

        service.update(10L, new UpdateProductRequest(2L, "Produto novo", null,
                new BigDecimal("20.00")));

        assertThat(product.getCategory()).isSameAs(target);
        assertThat(product.getName()).isEqualTo("Produto novo");
    }

    @Test
    void deveDesativarProduto_quandoProdutoExistir() {
        Product product = productWithStock(5);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(mapper.toResponse(product)).thenReturn(response());

        service.deactivate(10L);

        assertThat(product.getActive()).isFalse();
    }

    private CreateProductRequest createRequest() {
        return new CreateProductRequest(1L, "Produto", "sku-1", null,
                BigDecimal.TEN, 0);
    }

    private Product productWithStock(int stock) {
        return new Product(new Category("Casa", null), "Produto", "SKU-1", null,
                BigDecimal.TEN, stock);
    }

    private ProductResponse response() {
        return new ProductResponse(10L, "Produto", "SKU-1", null, BigDecimal.TEN,
                5, true, null, null, null);
    }
}
