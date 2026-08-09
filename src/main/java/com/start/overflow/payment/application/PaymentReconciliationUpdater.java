package com.start.overflow.payment.application;

import com.start.overflow.payment.domain.GatewayChargeResult;
import com.start.overflow.payment.domain.GatewayChargeStatus;
import com.start.overflow.payment.domain.Payment;
import com.start.overflow.payment.domain.PaymentStatus;
import com.start.overflow.payment.ports.out.OrderPaymentPort;
import com.start.overflow.payment.ports.out.PaymentRepositoryPort;
import com.start.overflow.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentReconciliationUpdater {
    private final PaymentRepositoryPort paymentRepository;
    private final OrderPaymentPort orderPaymentPort;

    public PaymentReconciliationUpdater(PaymentRepositoryPort paymentRepository,
                                        OrderPaymentPort orderPaymentPort) {
        this.paymentRepository = paymentRepository;
        this.orderPaymentPort = orderPaymentPort;
    }

    @Transactional
    public PaymentReconciliationAction apply(Long paymentId, GatewayChargeResult gatewayCharge) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pagamento não encontrado durante a conciliação: " + paymentId));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return PaymentReconciliationAction.SKIPPED;
        }
        if (isDivergent(payment, gatewayCharge)) {
            payment.markDivergent(gatewayCharge);
            paymentRepository.save(payment);
            return PaymentReconciliationAction.DIVERGENT;
        }

        payment.completeCharge(gatewayCharge);
        PaymentReconciliationAction action = switch (gatewayCharge.status()) {
            case APPROVED -> {
                orderPaymentPort.markOrderPaid(payment.getOrderId());
                yield PaymentReconciliationAction.APPROVED;
            }
            case REJECTED -> {
                orderPaymentPort.cancelOrderAndRestoreStock(payment.getOrderId());
                yield PaymentReconciliationAction.REJECTED;
            }
            case PENDING -> PaymentReconciliationAction.PENDING_UPDATED;
        };
        paymentRepository.save(payment);
        return action;
    }

    private boolean isDivergent(Payment payment, GatewayChargeResult gatewayCharge) {
        if (gatewayCharge == null || gatewayCharge.amount() == null) {
            return true;
        }
        boolean amountDiffers = payment.getAmount().compareTo(gatewayCharge.amount()) != 0;
        boolean externalIdDiffers = payment.getExternalId() != null
                && !payment.getExternalId().equals(gatewayCharge.externalId());
        return amountDiffers || externalIdDiffers;
    }
}
