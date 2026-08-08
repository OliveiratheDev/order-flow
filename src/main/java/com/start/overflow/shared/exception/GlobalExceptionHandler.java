package com.start.overflow.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildProblemDetail(HttpStatus.NOT_FOUND, "/errors/resource-not-found",
                "Recurso não encontrado", ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return buildProblemDetail(HttpStatus.CONFLICT, "/errors/business-rule",
                "Regra de negócio violada", ex.getMessage(), request);
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleDomainValidation(ValidationException ex, HttpServletRequest request) {
        return buildProblemDetail(HttpStatus.BAD_REQUEST, "/errors/validation",
                "Erro de validação", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = buildProblemDetail(HttpStatus.BAD_REQUEST, "/errors/validation",
                "Erro de validação", "Um ou mais campos são inválidos", request);

        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> Map.of(
                        "field", fieldError.getField(),
                        "message", fieldError.getDefaultMessage()))
                .collect(Collectors.toList());

        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        ProblemDetail problem = buildProblemDetail(HttpStatus.BAD_REQUEST, "/errors/validation",
                "Erro de validação", "Um ou mais parâmetros são inválidos", request);

        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", violation.getPropertyPath().toString(),
                        "message", violation.getMessage()))
                .collect(Collectors.toList());

        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedRequest(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildProblemDetail(HttpStatus.BAD_REQUEST, "/errors/malformed-request",
                "Requisição malformada", "O corpo da requisição não pôde ser interpretado", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String detail = String.format("O parâmetro '%s' deveria ser do tipo %s",
                ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "outro");
        return buildProblemDetail(HttpStatus.BAD_REQUEST, "/errors/type-mismatch",
                "Parâmetro inválido", detail, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        return buildProblemDetail(HttpStatus.CONFLICT, "/errors/conflict",
                "Conflito ao salvar", "O registro conflita com dados já existentes", request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                               HttpServletRequest request) {
        return buildProblemDetail(HttpStatus.CONFLICT, "/errors/concurrent-update",
                "Conflito de atualização",
                "O recurso foi alterado por outra operação. Recarregue os dados e tente novamente.", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return buildProblemDetail(HttpStatus.UNAUTHORIZED, "/errors/unauthorized",
                "Credenciais inválidas", "Credenciais inválidas", request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString().substring(0, 12);
        try {
            MDC.put("traceId", traceId);
            log.error("Erro não tratado [traceId={}]", traceId, ex);
        } finally {
            MDC.remove("traceId");
        }

        return buildProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "/errors/internal",
                "Erro interno",
                "Erro interno. Informe o código " + traceId + " ao suporte.",
                request);
    }

    private ProblemDetail buildProblemDetail(HttpStatus status, String type, String title,
                                             String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://orderflow.dev" + type));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
