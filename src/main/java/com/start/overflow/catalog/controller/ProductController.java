package com.start.overflow.catalog.controller;

import com.start.overflow.catalog.dto.CreateProductRequest;
import com.start.overflow.catalog.dto.ProductResponse;
import com.start.overflow.catalog.dto.UpdateProductRequest;
import com.start.overflow.catalog.dto.UpdateStockRequest;
import com.start.overflow.catalog.service.ProductService;
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
@RequestMapping("/api/v1/products")
@Tag(name = "Products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = productService.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(response.id()).toUri())
                .body(response);
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @GetMapping
    @Operation(summary = "Search products")
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return productService.search(name, categoryId, active, pageable);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public ProductResponse update(@PathVariable Long id,
                                  @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request);
    }

    @PatchMapping("/{id}/stock")
    @SecurityRequirement(name = "bearerAuth")
    public ProductResponse adjustStock(@PathVariable Long id,
                                       @Valid @RequestBody UpdateStockRequest request) {
        return productService.adjustStock(id, request);
    }

    @PatchMapping("/{id}/activate")
    @SecurityRequirement(name = "bearerAuth")
    public ProductResponse activate(@PathVariable Long id) {
        return productService.activate(id);
    }

    @PatchMapping("/{id}/deactivate")
    @SecurityRequirement(name = "bearerAuth")
    public ProductResponse deactivate(@PathVariable Long id) {
        return productService.deactivate(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    public void delete(@PathVariable Long id) {
        productService.deactivate(id);
    }
}
