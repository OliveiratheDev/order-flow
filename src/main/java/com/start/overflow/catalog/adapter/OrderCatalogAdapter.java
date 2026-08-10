package com.start.overflow.catalog.adapter;

import com.start.overflow.catalog.entity.Product;
import com.start.overflow.catalog.repository.ProductRepository;
import com.start.overflow.order.entity.OrderProductSnapshot;
import com.start.overflow.order.port.out.OrderCatalogPort;
import com.start.overflow.shared.exception.ResourceNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderCatalogAdapter implements OrderCatalogPort {
    private final ProductRepository productRepository;

    public OrderCatalogAdapter(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @CacheEvict(cacheNames = "products", allEntries = true)
    @Transactional(propagation = Propagation.REQUIRED)
    public OrderProductSnapshot reserveStock(Long productId, int quantity) {
        Product product = findProductForUpdate(productId);
        product.reserveStock(quantity);
        return new OrderProductSnapshot(product.getId(), product.getName(), product.getSku(),
                product.getPrice());
    }

    @Override
    @CacheEvict(cacheNames = "products", allEntries = true)
    public void restoreStock(Long productId, int quantity) {
        findProductForUpdate(productId).restoreStock(quantity);
    }

    private Product findProductForUpdate(Long id) {
        return productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Produto não encontrado: " + id));
    }
}
