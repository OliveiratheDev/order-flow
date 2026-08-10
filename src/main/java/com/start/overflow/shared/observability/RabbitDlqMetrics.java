package com.start.overflow.shared.observability;

import com.start.overflow.shared.messaging.RabbitTopology;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class RabbitDlqMetrics {
    private final boolean messagingEnabled;
    private final ObjectProvider<RabbitAdmin> rabbitAdminProvider;

    public RabbitDlqMetrics(
            MeterRegistry registry,
            ObjectProvider<RabbitAdmin> rabbitAdminProvider,
            @Value("${orderflow.messaging.rabbit.enabled:false}") boolean messagingEnabled
    ) {
        this.messagingEnabled = messagingEnabled;
        this.rabbitAdminProvider = rabbitAdminProvider;
        Gauge.builder("orderflow.dlq.depth", this, RabbitDlqMetrics::readDepth)
                .register(registry);
    }

    private double readDepth() {
        if (!messagingEnabled) {
            return Double.NaN;
        }
        RabbitAdmin rabbitAdmin = rabbitAdminProvider.getIfAvailable();
        if (rabbitAdmin == null) {
            return Double.NaN;
        }
        try {
            var information = rabbitAdmin.getQueueInfo(RabbitTopology.ORDER_EVENTS_DLQ);
            return information == null ? Double.NaN : information.getMessageCount();
        } catch (AmqpException exception) {
            return Double.NaN;
        }
    }
}
