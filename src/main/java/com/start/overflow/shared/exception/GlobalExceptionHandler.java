package com.start.overflow.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import com.start.overflow.shared.observability.CorrelationIdContext;
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
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    @ExceptionHandler(InvalidTransitionException.class)
    public ProblemDetail handleInvalidTransition(InvalidTransitionException ex,
                                                  HttpServletRequest request) {
        return buildProblemDetail(HttpStatus.UNPROCESSABLE_CONTENT, "/errors/invalid-transition",
                "Transição de estado inválida", ex.getMessage(), request);
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleDomainValidation(ValidationException ex, HttpServletRequest request) {
        return buildProblemDetail(HttpStatus.BAD_REQUEST, "/errors/validation",
                "Erro de validação", ex.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyKeyRequiredException.class)
    public ProblemDetail handleMissingIdempotencyKey(IdempotencyKeyRequiredException ex,
                                                     HttpServletRequest request) {
        return buildProblemDetail(HttpStatus.BAD_REQUEST, "/errors/idempotency-key-required",
                "Chave de idempotência inválida", ex.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(IdempotencyConflictException ex,
                                                   HttpServletRequest request) {
        return buildProblemDetail(HttpStatus.CONFLICT, "/errors/idempotency-in-progress",
                "Requisição em processamento", ex.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyPayloadMismatchException.class)
    public ProblemDetail handleIdempotencyPayloadMismatch(IdempotencyPayloadMismatchException ex,
                                                          HttpServletRequest request) {
        return buildProblemDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                "/errors/idempotency-payload-mismatch",
                "Chave reutilizada com outro conteúdo", ex.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyUnavailableException.class)
    public ProblemDetail handleIdempotencyUnavailable(IdempotencyUnavailableException ex,
                                                       HttpServletRequest request) {
        return buildProblemDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "/errors/idempotency-unavailable",
                "Idempotência indisponível", ex.getMessage(), request);
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

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(HttpServletRequest request) {
        return buildProblemDetail(HttpStatus.NOT_FOUND, "/errors/endpoint-not-found",
                "Endpoint não encontrado", "O endpoint solicitado não existe", request);
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
        String correlationId = CorrelationIdContext.currentOrCreate();
        log.atError().setCause(ex)
                .addKeyValue("correlationId", correlationId)
                .log("Erro não tratado");

        return buildProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "/errors/internal",
                "Erro interno",
                "Erro interno. Informe o código " + correlationId + " ao suporte.",
                request);
    }

    private ProblemDetail buildProblemDetail(HttpStatus status, String type, String title,
                                             String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://orderflow.dev" + type));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("correlationId", CorrelationIdContext.currentOrCreate());
        return problem;
    }
}
