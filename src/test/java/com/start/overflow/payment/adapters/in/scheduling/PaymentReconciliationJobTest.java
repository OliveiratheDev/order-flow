package com.start.overflow.payment.adapters.in.scheduling;

import com.start.overflow.payment.application.PaymentReconciliationService;
import com.start.overflow.shared.observability.CorrelationIdContext;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentReconciliationJobTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void delegatesWithCorrelationIdAndCleansSchedulerThread() {
        PaymentReconciliationService service = mock(PaymentReconciliationService.class);
        PaymentReconciliationJob job = new PaymentReconciliationJob(service);

        job.reconcile();

        verify(service).reconcilePendingPayments();
        assertThat(MDC.get(CorrelationIdContext.MDC_KEY)).isNull();
    }

    @Test
    void scheduleAndDistributedLockAreExternallyConfigurable() throws Exception {
        Method method = PaymentReconciliationJob.class.getMethod("reconcile");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        SchedulerLock lock = method.getAnnotation(SchedulerLock.class);

        assertThat(scheduled.cron())
                .isEqualTo("${orderflow.payment.reconciliation.cron}");
        assertThat(lock.name()).isEqualTo("paymentReconciliation");
        assertThat(lock.lockAtMostFor())
                .isEqualTo("${orderflow.payment.reconciliation.lock-at-most-for}");
        assertThat(lock.lockAtLeastFor())
                .isEqualTo("${orderflow.payment.reconciliation.lock-at-least-for}");
    }
}
