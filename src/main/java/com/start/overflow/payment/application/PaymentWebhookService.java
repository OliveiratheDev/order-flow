package com.start.overflow.payment.application;

import com.start.overflow.payment.domain.Payment;
import com.start.overflow.payment.domain.PaymentStatus;
import com.start.overflow.payment.ports.in.PaymentWebhookUseCase;
import com.start.overflow.payment.ports.out.OrderPaymentPort;
import com.start.overflow.payment.ports.out.PaymentRepositoryPort;
import com.start.overflow.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PaymentWebhookService implements PaymentWebhookUseCase {
    private final PaymentRepositoryPort paymentRepository;
    private final OrderPaymentPort orderPaymentPort;

    public PaymentWebhookService(PaymentRepositoryPort paymentRepository,
                                 OrderPaymentPort orderPaymentPort) {
        this.paymentRepository = paymentRepository;
        this.orderPaymentPort = orderPaymentPort;
    }

    @Override
    public void confirm(WebhookPaymentCommand command) {
        Payment payment = loadAndValidate(command);
        if (payment.getStatus() == PaymentStatus.APPROVED) {
            return;
        }
        requireStatus(payment, PaymentStatus.PENDING, "confirmar");
        payment.approve();
        orderPaymentPort.markOrderPaid(payment.getOrderId());
        paymentRepository.save(payment);
    }

    @Override
    public void refuse(WebhookPaymentCommand command) {
        Payment payment = loadAndValidate(command);
        if (payment.getStatus() == PaymentStatus.REJECTED) {
            return;
        }
        requireStatus(payment, PaymentStatus.PENDING, "recusar");
        payment.reject();
        orderPaymentPort.cancelOrderAndRestoreStock(payment.getOrderId());
        paymentRepository.save(payment);
    }

    @Override
    public void refund(WebhookPaymentCommand command) {
        Payment payment = loadAndValidate(command);
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        requireStatus(payment, PaymentStatus.APPROVED, "estornar");
        payment.refund();
        orderPaymentPort.cancelOrderAndRestoreStock(payment.getOrderId());
        paymentRepository.save(payment);
    }

    private Payment loadAndValidate(WebhookPaymentCommand command) {
        if (command == null || command.externalId() == null
                || command.externalId().isBlank() || command.amount() == null) {
            throw new WebhookReconciliationRequiredException(
                    "O evento não possui os dados financeiros obrigatórios");
        }
        Payment payment = paymentRepository.findByExternalIdForUpdate(command.externalId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pagamento externo ainda não registrado"));
        if (payment.getAmount().compareTo(command.amount()) != 0) {
            throw new WebhookReconciliationRequiredException(
                    "O valor do evento diverge do pagamento registrado");
        }
        String expectedReference = "orderflow-order-" + payment.getOrderId();
        if (!expectedReference.equals(command.externalReference())) {
            throw new WebhookReconciliationRequiredException(
                    "A referência do evento diverge do pedido registrado");
        }
        return payment;
    }

    private void requireStatus(Payment payment, PaymentStatus expected, String action) {
        if (payment.getStatus() != expected) {
            throw new WebhookReconciliationRequiredException(
                    "Não é possível " + action + " um pagamento " + payment.getStatus());
        }
    }
}
