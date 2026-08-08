package com.start.overflow.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank(message = "O endereço de entrega é obrigatório")
        @Size(max = 255, message = "O endereço deve ter no máximo 255 caracteres")
        String shippingAddress,
        @NotEmpty List<@Valid CreateOrderItemRequest> items
) {
}
