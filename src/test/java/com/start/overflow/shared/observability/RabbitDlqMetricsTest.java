package com.start.overflow.shared.observability;

import com.start.overflow.shared.messaging.RabbitTopology;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RabbitDlqMetricsTest {
    @Test
    void exposesCurrentDeadLetterQueueDepth() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
        ObjectProvider<RabbitAdmin> provider = provider(rabbitAdmin);
        when(rabbitAdmin.getQueueInfo(RabbitTopology.ORDER_EVENTS_DLQ))
                .thenReturn(new QueueInformation(RabbitTopology.ORDER_EVENTS_DLQ, 7, 1));

        new RabbitDlqMetrics(registry, provider, true);

        assertThat(registry.get("orderflow.dlq.depth").gauge().value()).isEqualTo(7);
    }

    @Test
    void unavailableBrokerProducesUnknownDepthInsteadOfBreakingScrape() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
        ObjectProvider<RabbitAdmin> provider = provider(rabbitAdmin);
        when(rabbitAdmin.getQueueInfo(RabbitTopology.ORDER_EVENTS_DLQ))
                .thenThrow(new AmqpConnectException(new IllegalStateException("offline")));

        new RabbitDlqMetrics(registry, provider, true);

        assertThat(registry.get("orderflow.dlq.depth").gauge().value()).isNaN();
    }

    @Test
    void missingDeadLetterQueueProducesUnknownDepth() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
        ObjectProvider<RabbitAdmin> provider = provider(rabbitAdmin);
        when(rabbitAdmin.getQueueInfo(RabbitTopology.ORDER_EVENTS_DLQ)).thenReturn(null);

        new RabbitDlqMetrics(registry, provider, true);

        assertThat(registry.get("orderflow.dlq.depth").gauge().value()).isNaN();
    }

    @Test
    void disabledMessagingDoesNotOpenBrokerConnection() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
        ObjectProvider<RabbitAdmin> provider = provider(rabbitAdmin);

        new RabbitDlqMetrics(registry, provider, false);

        assertThat(registry.get("orderflow.dlq.depth").gauge().value()).isNaN();
        verifyNoInteractions(rabbitAdmin);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<RabbitAdmin> provider(RabbitAdmin rabbitAdmin) {
        ObjectProvider<RabbitAdmin> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(rabbitAdmin);
        return provider;
    }
}
