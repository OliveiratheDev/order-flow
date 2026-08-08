package com.start.overflow.payment.application;

import com.start.overflow.identity.entity.AppUser;
import com.start.overflow.identity.entity.UserRole;
import com.start.overflow.identity.service.UserService;
import com.start.overflow.payment.application.dto.CreatePaymentRequest;
import com.start.overflow.payment.application.dto.PaymentResponse;
import com.start.overflow.payment.domain.ChargeRequest;
import com.start.overflow.payment.domain.GatewayChargeResult;
import com.start.overflow.payment.domain.Payment;
import com.start.overflow.payment.domain.PaymentAmount;
import com.start.overflow.payment.ports.in.CancelPaymentUseCase;
import com.start.overflow.payment.ports.in.CreatePaymentUseCase;
import com.start.overflow.payment.ports.in.GetPaymentUseCase;
import com.start.overflow.payment.ports.out.OrderPaymentPort;
import com.start.overflow.payment.ports.out.PaymentGatewayPort;
import com.start.overflow.payment.ports.out.PaymentRepositoryPort;
import com.start.overflow.shared.exception.BusinessRuleException;
import com.start.overflow.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService implements CreatePaymentUseCase, GetPaymentUseCase, CancelPaymentUseCase {
    private final PaymentRepositoryPort paymentRepository;
    private final OrderPaymentPort orderPaymentPort;
    private final PaymentGatewayPort paymentGateway;
    private final UserService userService;

    public PaymentService(PaymentRepositoryPort paymentRepository,
                          OrderPaymentPort orderPaymentPort,
                          PaymentGatewayPort paymentGateway,
                          UserService userService) {
        this.paymentRepository = paymentRepository;
        this.orderPaymentPort = orderPaymentPort;
        this.paymentGateway = paymentGateway;
        this.userService = userService;
    }

    @Override
    @Transactional
    public PaymentResponse create(CreatePaymentRequest request) {
        AppUser user = userService.currentUserEntity();
        if (paymentRepository.existsByOrderId(request.orderId())) {
            throw new BusinessRuleException("Já existe um pagamento para este pedido");
        }
        OrderPaymentPort.PayableOrder order = orderPaymentPort.loadPayableOrder(
                request.orderId(), user.getId(), user.getRole() == UserRole.ADMIN);
        Payment payment = Payment.create(order.orderId(), order.customerId(),
                order.amount(), request.method());
        GatewayChargeResult result = paymentGateway.createCharge(new ChargeRequest(
                order.orderId(), new PaymentAmount(order.amount()), request.method(),
                UUID.randomUUID().toString()));
        payment.completeCharge(result);
        if (result.approved()) {
            orderPaymentPort.markOrderPaid(order.orderId());
        } else {
            orderPaymentPort.cancelOrderAndRestoreStock(order.orderId());
        }
        return toResponse(paymentRepository.save(payment));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse findById(Long id) {
        AppUser user = userService.currentUserEntity();
        Payment payment = findOrThrow(id);
        ensureOwnerOrAdmin(payment, user);
        return toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse cancel(Long id) {
        AppUser user = userService.currentUserEntity();
        Payment payment = findForUpdateOrThrow(id);
        ensureOwnerOrAdmin(payment, user);
        payment.cancel();
        orderPaymentPort.cancelOrderAndRestoreStock(payment.getOrderId());
        return toResponse(paymentRepository.save(payment));
    }

    private Payment findOrThrow(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado: " + id));
    }

    private Payment findForUpdateOrThrow(Long id) {
        return paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado: " + id));
    }

    private void ensureOwnerOrAdmin(Payment payment, AppUser user) {
        if (user.getRole() != UserRole.ADMIN && !payment.getCustomerId().equals(user.getId())) {
            throw new ResourceNotFoundException("Pagamento não encontrado: " + payment.getId());
        }
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getCustomerId(),
                payment.getExternalId(), payment.getAmount(), payment.getMethod(), payment.getStatus(),
                payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
