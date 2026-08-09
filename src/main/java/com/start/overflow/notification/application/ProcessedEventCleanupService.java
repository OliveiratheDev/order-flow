package com.start.overflow.notification.application;

import com.start.overflow.notification.adapter.out.persistence.NotificationDeliveryStore;
import com.start.overflow.notification.adapter.out.persistence.ProcessedEventStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ProcessedEventCleanupService {
    private final ProcessedEventStore processedEvents;
    private final NotificationDeliveryStore deliveries;

    public ProcessedEventCleanupService(
            ProcessedEventStore processedEvents,
            NotificationDeliveryStore deliveries
    ) {
        this.processedEvents = processedEvents;
        this.deliveries = deliveries;
    }

    @Transactional
    public CleanupResult deleteBefore(Instant cutoff) {
        int deletedDeliveries = deliveries.deleteBefore(cutoff);
        int deletedEvents = processedEvents.deleteBefore(cutoff);
        return new CleanupResult(deletedEvents, deletedDeliveries);
    }

    public record CleanupResult(int processedEvents, int deliveries) {
    }
}
