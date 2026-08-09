package com.start.overflow.payment.application;

import com.start.overflow.identity.entity.AppUser;
import com.start.overflow.identity.entity.UserRole;
import com.start.overflow.identity.service.UserService;
import com.start.overflow.payment.application.dto.CreatePaymentRequest;
import com.start.overflow.payment.application.dto.PaymentResponse;
import com.start.overflow.payment.domain.ChargeRequest;
import com.start.overflow.payment.domain.GatewayChargeResult;
import com.start.overflow.payment.domain.GatewayChargeStatus;
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
import com.start.overflow.shared.observability.CorrelationIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService implements CreatePaymentUseCase, GetPaymentUseCase, CancelPaymentUseCase {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
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
        OrderPaymentPort.PayableOrder order = orderPaymentPort.loadPayableOrder(
                request.orderId(), user.getId(), user.getRole() == UserRole.ADMIN);
        if (paymentRepository.existsByOrderId(request.orderId())) {
            throw new BusinessRuleException("Já existe um pagamento para este pedido");
        }
        Payment payment = Payment.create(order.orderId(), order.payer().id(),
                order.amount(), request.method());
        GatewayChargeResult result = paymentGateway.createCharge(new ChargeRequest(
                order.orderId(), order.payer(), new PaymentAmount(order.amount()), request.method(),
                CorrelationIdContext.currentOrCreate()));
        payment.completeCharge(result);
        if (result.status() == GatewayChargeStatus.APPROVED) {
            orderPaymentPort.markOrderPaid(order.orderId());
        } else if (result.status() == GatewayChargeStatus.REJECTED) {
            orderPaymentPort.cancelOrderAndRestoreStock(order.orderId());
        }
        Payment saved = paymentRepository.save(payment);
        log.atInfo()
                .addKeyValue("paymentId", saved.getId())
                .addKeyValue("orderId", saved.getOrderId())
                .addKeyValue("paymentStatus", saved.getStatus())
                .addKeyValue("gatewayExternalId", saved.getExternalId())
                .log("Pagamento processado");
        return toResponse(saved);
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
        paymentGateway.cancelCharge(payment.getExternalId());
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
                payment.getExternalId(), payment.getPaymentUrl(), payment.getAmount(),
                payment.getMethod(), payment.getStatus(),
                payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
