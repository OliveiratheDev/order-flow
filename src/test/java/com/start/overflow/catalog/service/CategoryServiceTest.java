package com.start.overflow.catalog.service;

import com.start.overflow.catalog.dto.CategoryResponse;
import com.start.overflow.catalog.dto.CreateCategoryRequest;
import com.start.overflow.catalog.dto.UpdateCategoryRequest;
import com.start.overflow.catalog.entity.Category;
import com.start.overflow.catalog.mapper.CategoryMapper;
import com.start.overflow.catalog.repository.CategoryRepository;
import com.start.overflow.shared.exception.BusinessRuleException;
import com.start.overflow.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {
    @Mock CategoryRepository repository;
    @Mock CategoryMapper mapper;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(repository, mapper);
    }

    @Test
    void refusesDuplicateSlugBeforeWriting() {
        when(repository.existsBySlug("eletronicos")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateCategoryRequest("Eletrônicos", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("eletronicos");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void createsAvailableCategory() {
        Category category = new Category("Casa", null);
        CategoryResponse response = new CategoryResponse(null, "Casa", "casa", null,
                true, null, null);
        when(repository.saveAndFlush(any(Category.class))).thenReturn(category);
        when(mapper.toResponse(category)).thenReturn(response);

        service.create(new CreateCategoryRequest("Casa", null));

        verify(repository).saveAndFlush(any(Category.class));
    }

    @Test
    void refusesSlugCollisionOnUpdate() {
        Category category = new Category("Casa", null);
        when(repository.findById(1L)).thenReturn(Optional.of(category));
        when(repository.existsBySlugAndIdNot("eletronicos", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L,
                new UpdateCategoryRequest("Eletrônicos", null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void reportsMissingCategory() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
