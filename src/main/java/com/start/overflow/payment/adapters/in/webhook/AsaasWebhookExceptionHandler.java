package com.start.overflow.payment.adapters.in.webhook;

import com.start.overflow.shared.observability.CorrelationIdContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice(assignableTypes = AsaasWebhookController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AsaasWebhookExceptionHandler {

    @ExceptionHandler(InvalidWebhookTokenException.class)
    public ProblemDetail invalidToken(HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "/errors/invalid-webhook-token",
                "Webhook não autorizado", "O token do webhook é inválido", request);
    }

    @ExceptionHandler({InvalidWebhookPayloadException.class,
            UnsupportedWebhookEventException.class})
    public ProblemDetail invalidPayload(RuntimeException exception,
                                        HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "/errors/invalid-webhook-payload",
                "Webhook inválido", exception.getMessage(), request);
    }

    @ExceptionHandler(WebhookProcessingException.class)
    public ProblemDetail processingFailure(HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "/errors/webhook-processing-failure",
                "Falha ao processar webhook",
                "O evento não pôde ser processado; uma nova tentativa é necessária", request);
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
