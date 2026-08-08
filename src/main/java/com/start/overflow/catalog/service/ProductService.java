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
import com.start.overflow.shared.dto.PageResponse;
import com.start.overflow.shared.exception.BusinessRuleException;
import com.start.overflow.shared.exception.ResourceNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMapper = productMapper;
    }

    @Transactional
    @CacheEvict(cacheNames = "products", allEntries = true)
    public ProductResponse create(CreateProductRequest request) {
        String normalizedSku = request.sku().strip().toUpperCase(Locale.ROOT);
        if (productRepository.existsBySku(normalizedSku)) {
            throw new BusinessRuleException("Já existe um produto com o SKU '" + normalizedSku + "'");
        }
        Category category = findCategoryOrThrow(request.categoryId());
        Product product = new Product(category, request.name(), normalizedSku,
                request.description(), request.price(), request.stock());
        return productMapper.toResponse(productRepository.saveAndFlush(product));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "products", key = "#id")
    public ProductResponse findById(Long id) {
        return productMapper.toResponse(findProductOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String name, Long categoryId,
                                                Boolean active, Pageable pageable) {
        String normalizedName = name == null || name.isBlank() ? null : name.strip();
        Pageable bounded = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100),
                pageable.getSort());
        Page<ProductResponse> result = productRepository
                .search(normalizedName, categoryId, active, bounded)
                .map(productMapper::toResponse);
        return PageResponse.from(result);
    }

    @Transactional
    @CacheEvict(cacheNames = "products", allEntries = true)
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = findProductOrThrow(id);
        Category category = findCategoryOrThrow(request.categoryId());
        product.update(category, request.name(), request.description(), request.price());
        return productMapper.toResponse(product);
    }

    @Transactional
    @CacheEvict(cacheNames = "products", allEntries = true)
    public ProductResponse adjustStock(Long id, UpdateStockRequest request) {
        Product product = findProductForUpdateOrThrow(id);
        product.adjustStock(request.quantity());
        return productMapper.toResponse(product);
    }

    @Transactional
    @CacheEvict(cacheNames = "products", allEntries = true)
    public ProductResponse activate(Long id) {
        Product product = findProductOrThrow(id);
        product.activate();
        return productMapper.toResponse(product);
    }

    @Transactional
    @CacheEvict(cacheNames = "products", allEntries = true)
    public ProductResponse deactivate(Long id) {
        Product product = findProductOrThrow(id);
        product.deactivate();
        return productMapper.toResponse(product);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchByCategory(Long categoryId, Pageable pageable) {
        findCategoryOrThrow(categoryId);
        return search(null, categoryId, null, pageable);
    }

    private Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + id));
    }

    private Product findProductForUpdateOrThrow(Long id) {
        return productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + id));
    }

    private Category findCategoryOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada: " + id));
    }
}
