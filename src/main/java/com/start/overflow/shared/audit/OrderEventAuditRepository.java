package com.start.overflow.shared.audit;

import org.springframework.data.jpa.repository.JpaRepository;

interface OrderEventAuditRepository extends JpaRepository<OrderEventAuditJpaEntity, Long> {
}
