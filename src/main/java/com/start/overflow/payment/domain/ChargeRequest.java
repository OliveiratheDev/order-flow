package com.start.overflow.payment.domain;

public record ChargeRequest(
        Long orderId,
        Payer payer,
        PaymentAmount amount,
        PaymentMethod method,
        String correlationId
) {
    public ChargeRequest {
        if (orderId == null || payer == null || amount == null || method == null) {
            throw new PaymentException("Pedido, pagador, valor e método são obrigatórios para cobrar");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new PaymentException("O identificador de correlação é obrigatório");
        }
        correlationId = correlationId.strip();
    }
}
