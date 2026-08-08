package com.start.overflow.catalog.service;

import com.start.overflow.catalog.dto.CategoryResponse;
import com.start.overflow.catalog.dto.CreateCategoryRequest;
import com.start.overflow.catalog.dto.UpdateCategoryRequest;
import com.start.overflow.catalog.entity.Category;
import com.start.overflow.catalog.mapper.CategoryMapper;
import com.start.overflow.catalog.repository.CategoryRepository;
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

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    @Transactional
    @CacheEvict(cacheNames = "categories", allEntries = true)
    public CategoryResponse create(CreateCategoryRequest request) {
        Category category = new Category(request.name(), request.description());
        ensureSlugAvailable(category.getSlug());
        return categoryMapper.toResponse(categoryRepository.saveAndFlush(category));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "categories", key = "#id")
    public CategoryResponse findById(Long id) {
        return categoryMapper.toResponse(findCategoryOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> search(String name, Boolean active, Pageable pageable) {
        String normalizedName = name == null || name.isBlank() ? null : name.strip();
        Pageable bounded = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100),
                pageable.getSort());
        Page<CategoryResponse> result = categoryRepository.search(normalizedName, active, bounded)
                .map(categoryMapper::toResponse);
        return PageResponse.from(result);
    }

    @Transactional
    @CacheEvict(cacheNames = "categories", allEntries = true)
    public CategoryResponse update(Long id, UpdateCategoryRequest request) {
        Category category = findCategoryOrThrow(id);
        String candidateSlug = Category.generateSlug(request.name());
        if (categoryRepository.existsBySlugAndIdNot(candidateSlug, id)) {
            throw new BusinessRuleException("Já existe uma categoria com o identificador '" + candidateSlug + "'");
        }
        category.rename(request.name());
        category.updateDescription(request.description());
        categoryRepository.flush();
        return categoryMapper.toResponse(category);
    }

    @Transactional
    @CacheEvict(cacheNames = "categories", allEntries = true)
    public CategoryResponse activate(Long id) {
        Category category = findCategoryOrThrow(id);
        category.activate();
        return categoryMapper.toResponse(category);
    }

    @Transactional
    @CacheEvict(cacheNames = "categories", allEntries = true)
    public CategoryResponse deactivate(Long id) {
        Category category = findCategoryOrThrow(id);
        category.deactivate();
        return categoryMapper.toResponse(category);
    }

    private Category findCategoryOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada: " + id));
    }

    private void ensureSlugAvailable(String slug) {
        if (categoryRepository.existsBySlug(slug)) {
            throw new BusinessRuleException("Já existe uma categoria com o identificador '" + slug + "'");
        }
    }
}
