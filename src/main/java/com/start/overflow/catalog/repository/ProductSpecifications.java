package com.start.overflow.catalog.repository;

import com.start.overflow.catalog.dto.ProductFilter;
import com.start.overflow.catalog.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

public final class ProductSpecifications {
    private ProductSpecifications() {
    }

    public static Specification<Product> from(ProductFilter filter) {
        return Stream.of(
                        nameContains(filter.name()),
                        categoryId(filter.categoryId()),
                        minimumPrice(filter.minPrice()),
                        maximumPrice(filter.maxPrice()),
                        active(filter.active()))
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElseGet(Specification::unrestricted);
    }

    public static Specification<Product> nameContains(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String pattern = "%" + name.strip().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern);
    }

    public static Specification<Product> categoryId(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> minimumPrice(BigDecimal minimumPrice) {
        if (minimumPrice == null) {
            return null;
        }
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minimumPrice);
    }

    public static Specification<Product> maximumPrice(BigDecimal maximumPrice) {
        if (maximumPrice == null) {
            return null;
        }
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(root.get("price"), maximumPrice);
    }

    public static Specification<Product> active(Boolean active) {
        if (active == null) {
            return null;
        }
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("active"), active);
    }
}
