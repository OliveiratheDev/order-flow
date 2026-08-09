package com.start.overflow.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Pattern(regexp = "[0-9.\\-/]{11,18}",
                message = "deve ser um CPF ou CNPJ válido") String document,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
