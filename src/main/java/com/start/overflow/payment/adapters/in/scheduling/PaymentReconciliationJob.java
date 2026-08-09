package com.start.overflow.payment.adapters.in.scheduling;

import com.start.overflow.payment.application.PaymentReconciliationService;
import com.start.overflow.shared.observability.CorrelationIdContext;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentReconciliationJob {
    private final PaymentReconciliationService reconciliationService;

    public PaymentReconciliationJob(PaymentReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(cron = "${orderflow.payment.reconciliation.cron}")
    @SchedulerLock(
            name = "paymentReconciliation",
            lockAtMostFor = "${orderflow.payment.reconciliation.lock-at-most-for}",
            lockAtLeastFor = "${orderflow.payment.reconciliation.lock-at-least-for}")
    public void reconcile() {
        String previousCorrelationId = MDC.get(CorrelationIdContext.MDC_KEY);
        try {
            MDC.put(CorrelationIdContext.MDC_KEY, CorrelationIdContext.currentOrCreate());
            reconciliationService.reconcilePendingPayments();
        } finally {
            if (previousCorrelationId == null) {
                MDC.remove(CorrelationIdContext.MDC_KEY);
            } else {
                MDC.put(CorrelationIdContext.MDC_KEY, previousCorrelationId);
            }
        }
    }
}
