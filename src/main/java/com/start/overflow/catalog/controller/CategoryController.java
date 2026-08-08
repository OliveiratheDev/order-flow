package com.start.overflow.catalog.controller;

import com.start.overflow.catalog.dto.CategoryResponse;
import com.start.overflow.catalog.dto.CreateCategoryRequest;
import com.start.overflow.catalog.dto.UpdateCategoryRequest;
import com.start.overflow.catalog.service.CategoryService;
import com.start.overflow.catalog.service.ProductService;
import com.start.overflow.catalog.dto.ProductResponse;
import com.start.overflow.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories")
public class CategoryController {
    private final CategoryService categoryService;
    private final ProductService productService;

    public CategoryController(CategoryService categoryService, ProductService productService) {
        this.categoryService = categoryService;
        this.productService = productService;
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a category")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = categoryService.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(response.id()).toUri())
                .body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Find a category by id")
    public CategoryResponse findById(@PathVariable Long id) {
        return categoryService.findById(id);
    }

    @GetMapping
    @Operation(summary = "Search categories")
    public PageResponse<CategoryResponse> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return categoryService.search(name, active, pageable);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update a category")
    public CategoryResponse update(@PathVariable Long id,
                                   @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.update(id, request);
    }

    @PatchMapping("/{id}/activate")
    @SecurityRequirement(name = "bearerAuth")
    public CategoryResponse activate(@PathVariable Long id) {
        return categoryService.activate(id);
    }

    @PatchMapping("/{id}/deactivate")
    @SecurityRequirement(name = "bearerAuth")
    public CategoryResponse deactivate(@PathVariable Long id) {
        return categoryService.deactivate(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    public void delete(@PathVariable Long id) {
        categoryService.deactivate(id);
    }

    @GetMapping("/{id}/products")
    @Operation(summary = "List products from a category")
    public PageResponse<ProductResponse> products(@PathVariable Long id,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return productService.searchByCategory(id, pageable);
    }
}
