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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
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
                        10L, 7L, new BigDecimal("499.90")));
        when(paymentGateway.createCharge(any(ChargeRequest.class)))
                .thenReturn(new GatewayChargeResult("gateway-10", true, null));
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
                        10L, 7L, new BigDecimal("49.90")));
        when(paymentGateway.createCharge(any(ChargeRequest.class)))
                .thenReturn(new GatewayChargeResult("declined-10", false, "Sem limite"));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.create(
                new CreatePaymentRequest(10L, PaymentMethod.CREDIT_CARD));

        assertThat(response.status()).isEqualTo(PaymentStatus.REJECTED);
        verify(orderPaymentPort).cancelOrderAndRestoreStock(10L);
        verify(orderPaymentPort, never()).markOrderPaid(any());
    }

    @Test
    void duplicatePaymentIsRejectedBeforeCallingTheGateway() {
        when(userService.currentUserEntity()).thenReturn(user);
        when(paymentRepository.existsByOrderId(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreatePaymentRequest(10L, PaymentMethod.PIX)))
                .isInstanceOf(BusinessRuleException.class);

        verify(paymentGateway, never()).createCharge(any());
        verify(orderPaymentPort, never()).loadPayableOrder(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void gatewayFailureDoesNotPersistOrChangeTheOrder() {
        authenticateCustomer();
        when(orderPaymentPort.loadPayableOrder(10L, 7L, false))
                .thenReturn(new OrderPaymentPort.PayableOrder(10L, 7L, BigDecimal.TEN));
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
