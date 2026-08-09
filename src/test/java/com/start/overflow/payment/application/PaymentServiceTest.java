package com.start.overflow.payment.application;

import com.start.overflow.identity.entity.AppUser;
import com.start.overflow.identity.entity.UserRole;
import com.start.overflow.identity.service.UserService;
import com.start.overflow.payment.application.dto.CreatePaymentRequest;
import com.start.overflow.payment.application.dto.PaymentResponse;
import com.start.overflow.payment.domain.ChargeRequest;
import com.start.overflow.payment.domain.GatewayChargeResult;
import com.start.overflow.payment.domain.Payment;
import com.start.overflow.payment.domain.PaymentGatewayUnavailableException;
import com.start.overflow.payment.domain.PaymentMethod;
import com.start.overflow.payment.domain.PaymentStatus;
import com.start.overflow.payment.domain.Payer;
import com.start.overflow.payment.ports.out.OrderPaymentPort;
import com.start.overflow.payment.ports.out.PaymentGatewayPort;
import com.start.overflow.payment.ports.out.PaymentRepositoryPort;
import com.start.overflow.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    private static final Payer PAYER = new Payer(
            7L, "Maria", "maria@example.com", "52998224725");
    @Mock PaymentRepositoryPort paymentRepository;
    @Mock OrderPaymentPort orderPaymentPort;
    @Mock PaymentGatewayPort paymentGateway;
    @Mock UserService userService;
    @Mock AppUser user;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentRepository, orderPaymentPort, paymentGateway,
                userService);
    }

    @Test
    void chargesTheAmountCalculatedByTheOrderAndMarksItPaid() {
        authenticateCustomer();
        when(orderPaymentPort.loadPayableOrder(10L, 7L, false))
                .thenReturn(new OrderPaymentPort.PayableOrder(
                        10L, PAYER, new BigDecimal("499.90")));
        when(paymentGateway.createCharge(any(ChargeRequest.class)))
                .thenReturn(GatewayChargeResult.approved("gateway-10"));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.create(
                new CreatePaymentRequest(10L, PaymentMethod.CREDIT_CARD));

        ArgumentCaptor<ChargeRequest> charge = ArgumentCaptor.forClass(ChargeRequest.class);
        verify(paymentGateway).createCharge(charge.capture());
        assertThat(charge.getValue().amount().value()).isEqualByComparingTo("499.90");
        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.externalId()).isEqualTo("gateway-10");
        verify(orderPaymentPort).markOrderPaid(10L);
        verify(orderPaymentPort, never()).cancelOrderAndRestoreStock(any());
    }

    @Test
    void rejectedChargeCancelsTheOrderAndRestoresStock() {
        authenticateCustomer();
        when(orderPaymentPort.loadPayableOrder(10L, 7L, false))
                .thenReturn(new OrderPaymentPort.PayableOrder(
                        10L, PAYER, new BigDecimal("49.90")));
        when(paymentGateway.createCharge(any(ChargeRequest.class)))
                .thenReturn(GatewayChargeResult.rejected("declined-10", "Sem limite"));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.create(
                new CreatePaymentRequest(10L, PaymentMethod.CREDIT_CARD));

        assertThat(response.status()).isEqualTo(PaymentStatus.REJECTED);
        verify(orderPaymentPort).cancelOrderAndRestoreStock(10L);
        verify(orderPaymentPort, never()).markOrderPaid(any());
    }

    @Test
    void pendingChargeKeepsOrderAwaitingPaymentAndReturnsPaymentUrl() {
        authenticateCustomer();
        when(orderPaymentPort.loadPayableOrder(10L, 7L, false))
                .thenReturn(new OrderPaymentPort.PayableOrder(
                        10L, PAYER, new BigDecimal("49.90")));
        when(paymentGateway.createCharge(any(ChargeRequest.class)))
                .thenReturn(GatewayChargeResult.pending(
                        "pay_123", "https://sandbox.asaas.com/i/123"));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.create(
                new CreatePaymentRequest(10L, PaymentMethod.PIX));

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.paymentUrl()).isEqualTo("https://sandbox.asaas.com/i/123");
        verify(orderPaymentPort, never()).markOrderPaid(any());
        verify(orderPaymentPort, never()).cancelOrderAndRestoreStock(any());
    }

    @Test
    void duplicatePaymentIsRejectedBeforeCallingTheGateway() {
        authenticateCustomer();
        when(orderPaymentPort.loadPayableOrder(10L, 7L, false))
                .thenReturn(new OrderPaymentPort.PayableOrder(
                        10L, PAYER, new BigDecimal("49.90")));
        when(paymentRepository.existsByOrderId(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreatePaymentRequest(10L, PaymentMethod.PIX)))
                .isInstanceOf(BusinessRuleException.class);

        verify(paymentGateway, never()).createCharge(any());
        verify(orderPaymentPort).loadPayableOrder(10L, 7L, false);
    }

    @Test
    void cancellingPendingPaymentAlsoCancelsExternalCharge() {
        authenticateCustomer();
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        Payment payment = Payment.restore(30L, 10L, 7L, new BigDecimal("49.90"),
                PaymentMethod.PIX, "pay_123", "https://sandbox.asaas.com/i/123",
                PaymentStatus.PENDING, now, now);
        when(paymentRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        PaymentResponse response = service.cancel(30L);

        assertThat(response.status()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentGateway).cancelCharge("pay_123");
        verify(orderPaymentPort).cancelOrderAndRestoreStock(10L);
    }

    @Test
    void gatewayFailureDoesNotPersistOrChangeTheOrder() {
        authenticateCustomer();
        when(orderPaymentPort.loadPayableOrder(10L, 7L, false))
                .thenReturn(new OrderPaymentPort.PayableOrder(10L, PAYER, BigDecimal.TEN));
        when(paymentGateway.createCharge(any(ChargeRequest.class)))
                .thenThrow(new PaymentGatewayUnavailableException("Gateway indisponível"));

        assertThatThrownBy(() -> service.create(
                new CreatePaymentRequest(10L, PaymentMethod.PIX)))
                .isInstanceOf(PaymentGatewayUnavailableException.class);

        verify(paymentRepository, never()).save(any());
        verify(orderPaymentPort, never()).markOrderPaid(any());
        verify(orderPaymentPort, never()).cancelOrderAndRestoreStock(any());
    }

    private void authenticateCustomer() {
        when(userService.currentUserEntity()).thenReturn(user);
        when(user.getId()).thenReturn(7L);
        when(user.getRole()).thenReturn(UserRole.CUSTOMER);
    }
}
