package com.start.overflow.shared.observability;

import com.start.overflow.payment.domain.GatewayChargeResult;
import com.start.overflow.payment.domain.PaymentGatewayUnavailableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentGatewayMetricsAspectTest {
    private SimpleMeterRegistry registry;
    private PaymentGatewayMetricsAspect aspect;
    private ObservedPaymentGateway observedGateway;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        aspect = new PaymentGatewayMetricsAspect(registry);
        observedGateway = ObservedAsaasGateway.class
                .getAnnotation(ObservedPaymentGateway.class);
    }

    @Test
    void recordsSuccessfulGatewayDuration() throws Throwable {
        ProceedingJoinPoint invocation = invocation(
                "createCharge", GatewayChargeResult.approved("pay-1"));

        aspect.measure(invocation, observedGateway);

        assertThat(registry.get("orderflow.payment.gateway.duration")
                .tags("gateway", "asaas", "operation", "create")
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    void rejectedResultRecordsBoundedFailureReason() throws Throwable {
        ProceedingJoinPoint invocation = invocation(
                "createCharge", GatewayChargeResult.rejected("pay-1", "recusada"));

        aspect.measure(invocation, observedGateway);

        assertThat(registry.get("orderflow.payments.failed")
                .tags("gateway", "asaas", "reason", "rejected")
                .counter()
                .count()).isEqualTo(1);
        assertThat(registry.get("orderflow.payment.gateway.duration")
                .tags("gateway", "asaas", "operation", "create")
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    void timeoutCauseIsClassifiedWithoutUsingExceptionMessage() throws Throwable {
        PaymentGatewayUnavailableException failure = new PaymentGatewayUnavailableException(
                "indisponível", new TimeoutException("timeout"));
        ProceedingJoinPoint invocation = invocation("getCharge", failure);

        assertThatThrownBy(() -> aspect.measure(invocation, observedGateway))
                .isSameAs(failure);

        assertThat(registry.get("orderflow.payments.failed")
                .tags("gateway", "asaas", "reason", "timeout")
                .counter()
                .count()).isEqualTo(1);
    }

    private ProceedingJoinPoint invocation(String method, Object result) throws Throwable {
        ProceedingJoinPoint invocation = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn(method);
        when(invocation.getSignature()).thenReturn(signature);
        if (result instanceof Throwable failure) {
            when(invocation.proceed()).thenThrow(failure);
        } else {
            when(invocation.proceed()).thenReturn(result);
        }
        return invocation;
    }

    @ObservedPaymentGateway(PaymentGatewayName.ASAAS)
    private static final class ObservedAsaasGateway {
    }
}
