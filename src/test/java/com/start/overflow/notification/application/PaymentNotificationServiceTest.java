package com.start.overflow.notification.application;

import com.start.overflow.notification.adapter.out.persistence.NotificationDeliveryStore;
import com.start.overflow.notification.adapter.out.persistence.ProcessedEventStore;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PaymentNotificationServiceTest {
    private final ProcessedEventStore processedEvents = mock(ProcessedEventStore.class);
    private final NotificationDeliveryStore deliveries = mock(NotificationDeliveryStore.class);
    private final PaymentNotificationService service =
            new PaymentNotificationService(processedEvents, deliveries);

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCommands")
    void rejectsInvalidCommands(String scenario, PaymentNotificationCommand command) {
        assertThatThrownBy(() -> service.notifyPayment(command))
                .isInstanceOf(InvalidNotificationEventException.class);

        verifyNoInteractions(processedEvents, deliveries);
    }

    static Stream<Arguments> invalidCommands() {
        UUID eventId = UUID.randomUUID();
        return Stream.of(
                Arguments.of("comando ausente", null),
                Arguments.of("evento ausente", command(null, "correlation", 1L, 1L,
                        BigDecimal.ONE)),
                Arguments.of("correlação vazia", command(eventId, " ", 1L, 1L,
                        BigDecimal.ONE)),
                Arguments.of("pedido inválido", command(eventId, "correlation", 0L, 1L,
                        BigDecimal.ONE)),
                Arguments.of("cliente inválido", command(eventId, "correlation", 1L, 0L,
                        BigDecimal.ONE)),
                Arguments.of("valor ausente", command(eventId, "correlation", 1L, 1L,
                        null)),
                Arguments.of("valor inválido", command(eventId, "correlation", 1L, 1L,
                        BigDecimal.ZERO)));
    }

    private static PaymentNotificationCommand command(
            UUID eventId,
            String correlationId,
            Long orderId,
            Long customerId,
            BigDecimal amount
    ) {
        return new PaymentNotificationCommand(
                eventId, correlationId, orderId, customerId, amount);
    }
}
