package com.start.overflow.payment.adapters.in.web;

import com.start.overflow.payment.application.dto.CreatePaymentRequest;
import com.start.overflow.payment.application.dto.PaymentResponse;
import com.start.overflow.payment.ports.in.CancelPaymentUseCase;
import com.start.overflow.payment.ports.in.CreatePaymentUseCase;
import com.start.overflow.payment.ports.in.GetPaymentUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Cobranças simuladas dos pedidos")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {
    private final CreatePaymentUseCase createPayment;
    private final GetPaymentUseCase getPayment;
    private final CancelPaymentUseCase cancelPayment;

    public PaymentController(CreatePaymentUseCase createPayment,
                             GetPaymentUseCase getPayment,
                             CancelPaymentUseCase cancelPayment) {
        this.createPayment = createPayment;
        this.getPayment = getPayment;
        this.cancelPayment = cancelPayment;
    }

    @PostMapping
    @Operation(summary = "Cria e processa a cobrança de um pedido")
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentResponse response = createPayment.create(request);
        return ResponseEntity.created(URI.create("/api/v1/payments/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta uma cobrança")
    public PaymentResponse findById(@PathVariable Long id) {
        return getPayment.findById(id);
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancela uma cobrança pendente")
    public PaymentResponse cancel(@PathVariable Long id) {
        return cancelPayment.cancel(id);
    }
}
