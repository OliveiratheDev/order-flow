package com.start.overflow.payment.ports.out;

import com.start.overflow.payment.domain.Payer;

import java.math.BigDecimal;

public interface OrderPaymentPort {
    PayableOrder loadPayableOrder(Long orderId, Long requesterId, boolean admin);

    void markOrderPaid(Long orderId);

    void cancelOrderAndRestoreStock(Long orderId);

    record PayableOrder(Long orderId, Payer payer, BigDecimal amount) {
    }
}
