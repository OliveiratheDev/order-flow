package com.start.overflow.shared.audit;

import com.start.overflow.order.event.OrderDomainEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderEventAuditListener {
    private final OrderEventAuditRepository repository;

    public OrderEventAuditListener(OrderEventAuditRepository repository) {
        this.repository = repository;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void audit(OrderDomainEvent event) {
        repository.save(new OrderEventAuditJpaEntity(event));
    }
}
