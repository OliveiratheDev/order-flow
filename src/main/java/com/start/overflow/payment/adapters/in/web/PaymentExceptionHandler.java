package com.start.overflow.payment.adapters.in.web;

import com.start.overflow.payment.domain.PaymentException;
import com.start.overflow.payment.domain.PaymentGatewayUnavailableException;
import com.start.overflow.payment.domain.PaymentRejectedException;
import com.start.overflow.payment.domain.PaymentGatewayRequestException;
import com.start.overflow.shared.observability.CorrelationIdContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice(basePackageClasses = PaymentController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaymentExceptionHandler {

    @ExceptionHandler(PaymentGatewayUnavailableException.class)
    public ProblemDetail handleGatewayUnavailable(PaymentGatewayUnavailableException exception,
                                                  HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "/errors/payment-gateway-unavailable",
                "Gateway de pagamento indisponível", exception.getMessage(), request);
    }

    @ExceptionHandler(PaymentRejectedException.class)
    public ProblemDetail handleRejected(PaymentRejectedException exception,
                                        HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "/errors/payment-rejected",
                "Cobrança rejeitada", exception.getMessage(), request);
    }

    @ExceptionHandler(PaymentGatewayRequestException.class)
    public ProblemDetail handleGatewayRequest(PaymentGatewayRequestException exception,
                                              HttpServletRequest request) {
        return problem(HttpStatus.BAD_GATEWAY, "/errors/payment-gateway-request",
                "Falha permanente no gateway de pagamento", exception.getMessage(), request);
    }

    @ExceptionHandler(PaymentException.class)
    public ProblemDetail handlePaymentRule(PaymentException exception,
                                           HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "/errors/payment-rule",
                "Regra de pagamento violada", exception.getMessage(), request);
    }

    private ProblemDetail problem(HttpStatus status, String type, String title, String detail,
                                  HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://orderflow.dev" + type));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("correlationId", CorrelationIdContext.currentOrCreate());
        return problem;
    }
}
