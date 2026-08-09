package com.start.overflow.notification.adapter.in.scheduling;

import com.start.overflow.notification.application.NotificationRetentionProperties;
import com.start.overflow.notification.application.ProcessedEventCleanupService;
import com.start.overflow.shared.observability.CorrelationIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "orderflow.messaging.rabbit.enabled", havingValue = "true")
public class ProcessedEventCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(ProcessedEventCleanupJob.class);

    private final ProcessedEventCleanupService cleanupService;
    private final NotificationRetentionProperties properties;

    public ProcessedEventCleanupJob(
            ProcessedEventCleanupService cleanupService,
            NotificationRetentionProperties properties
    ) {
        this.cleanupService = cleanupService;
        this.properties = properties;
    }

    @Scheduled(cron = "${orderflow.notification.cleanup-cron}")
    public void cleanup() {
        String previousCorrelationId = MDC.get(CorrelationIdContext.MDC_KEY);
        try {
            MDC.put(CorrelationIdContext.MDC_KEY, CorrelationIdContext.currentOrCreate());
            Instant cutoff = Instant.now().minus(properties.processedEventRetention());
            var result = cleanupService.deleteBefore(cutoff);
            log.atInfo()
                    .addKeyValue("processedEventsDeleted", result.processedEvents())
                    .addKeyValue("deliveriesDeleted", result.deliveries())
                    .addKeyValue("cutoff", cutoff)
                    .log("Expurgo de idempotência de notificações concluído");
        } finally {
            restoreCorrelationId(previousCorrelationId);
        }
    }

    private void restoreCorrelationId(String previousCorrelationId) {
        if (previousCorrelationId == null) {
            MDC.remove(CorrelationIdContext.MDC_KEY);
        } else {
            MDC.put(CorrelationIdContext.MDC_KEY, previousCorrelationId);
        }
    }
}
