package com.start.overflow.payment.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record PaymentAmount(BigDecimal value) {
    public PaymentAmount {
        if (value == null || value.signum() <= 0) {
            throw new PaymentException("O valor do pagamento deve ser positivo");
        }
        value = value.setScale(2, RoundingMode.HALF_UP);
        if (value.precision() > 12) {
            throw new PaymentException("O valor do pagamento excede o limite permitido");
        }
    }
}
