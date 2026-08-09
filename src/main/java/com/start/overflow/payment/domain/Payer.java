package com.start.overflow.payment.domain;

import java.util.Locale;

public record Payer(
        Long id,
        String name,
        String email,
        String taxId
) {
    public Payer {
        if (id == null || id <= 0) {
            throw new PaymentException("O identificador do pagador é obrigatório");
        }
        if (name == null || name.isBlank()) {
            throw new PaymentException("O nome do pagador é obrigatório");
        }
        name = name.strip();
        if (email == null || email.isBlank()) {
            throw new PaymentException("O e-mail do pagador é obrigatório");
        }
        email = email.strip().toLowerCase(Locale.ROOT);
        if (taxId == null || taxId.isBlank()) {
            throw new PaymentException("O CPF ou CNPJ do pagador é obrigatório");
        }
        taxId = taxId.replaceAll("\\D", "");
        if (taxId.length() != 11 && taxId.length() != 14) {
            throw new PaymentException("O CPF ou CNPJ do pagador deve possuir 11 ou 14 dígitos");
        }
    }
}
