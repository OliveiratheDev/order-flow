package com.start.overflow.order.repository;

import com.start.overflow.order.entity.CustomerOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {
    @Query("""
            SELECT DISTINCT o FROM CustomerOrder o
            JOIN FETCH o.customer
            JOIN FETCH o.items i
            JOIN FETCH i.product
            WHERE o.id = :id
            """)
    Optional<CustomerOrder> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CustomerOrder o JOIN FETCH o.customer WHERE o.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "customer")
    Page<CustomerOrder> findByCustomerId(Long customerId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "customer")
    Page<CustomerOrder> findAll(Pageable pageable);
}
