package com.start.overflow.notification.adapter.in.scheduling;

import com.start.overflow.notification.application.NotificationRetentionProperties;
import com.start.overflow.notification.application.ProcessedEventCleanupService;
import com.start.overflow.shared.observability.CorrelationIdContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessedEventCleanupJobTest {
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void deletesRecordsOlderThanRetentionAndRestoresSchedulerMdc() {
        ProcessedEventCleanupService service = mock(ProcessedEventCleanupService.class);
        when(service.deleteBefore(any()))
                .thenReturn(new ProcessedEventCleanupService.CleanupResult(2, 1));
        var properties = new NotificationRetentionProperties(Duration.ofDays(30));
        var job = new ProcessedEventCleanupJob(service, properties);
        MDC.put(CorrelationIdContext.MDC_KEY, "previous-correlation");

        job.cleanup();

        verify(service).deleteBefore(any());
        assertThat(MDC.get(CorrelationIdContext.MDC_KEY)).isEqualTo("previous-correlation");
    }

    @Test
    void cleanupScheduleIsExternallyConfigurable() throws Exception {
        Method method = ProcessedEventCleanupJob.class.getMethod("cleanup");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${orderflow.notification.cleanup-cron}");
    }
}
