package com.start.overflow.catalog.repository;

import com.start.overflow.catalog.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsBySku(String sku);

    @Override
    @EntityGraph(attributePaths = "category")
    Optional<Product> findById(Long id);

    @EntityGraph(attributePaths = "category")
    @Query("""
            SELECT p FROM Product p
            WHERE (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')))
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:active IS NULL OR p.active = :active)
            """)
    Page<Product> search(@Param("name") String name,
                         @Param("categoryId") Long categoryId,
                         @Param("active") Boolean active,
                         Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
