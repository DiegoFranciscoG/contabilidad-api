package io.github.diegofranciscog.accounting.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error is an RFC 9457 problem detail. Messages are the business messages written in this code base;
 * internal details (SQL, stack traces, class names) never reach the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String RULE_PREFIX = "accounting_rule:";
    private static final String UNIQUE_VIOLATION = "23505";

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Regla de negocio incumplida", e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "No encontrado", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, "Conflicto", e.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ProblemDetail> tooManyRequests(TooManyRequestsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "60")
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes", e.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate() {
        return problem(HttpStatus.CONFLICT, "Conflicto", "El registro fue modificado por otra persona; vuelve a cargarlo");
    }

    /** Violations raised by the database triggers and constraints (the last line of defence). */
    @ExceptionHandler({DataIntegrityViolationException.class, JpaSystemException.class, TransactionSystemException.class})
    ProblemDetail database(RuntimeException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(RULE_PREFIX)) {
                String rule = message.substring(message.indexOf(RULE_PREFIX) + RULE_PREFIX.length()).lines()
                        .findFirst().orElse("").strip();
                return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Regla contable incumplida", rule);
            }
            if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                return problem(HttpStatus.CONFLICT, "Conflicto", "El registro ya existe");
            }
        }
        log.warn("Integridad de datos rechazada: {}", e.getClass().getSimpleName());
        return problem(HttpStatus.CONFLICT, "Conflicto", "La operación viola una restricción de datos");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden() {
        return problem(HttpStatus.FORBIDDEN, "Acceso denegado", "Tu rol no permite esta operación");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail unauthorized() {
        return problem(HttpStatus.UNAUTHORIZED, "No autenticado", "Credenciales inválidas");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Error no controlado", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Ocurrió un error inesperado");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(error -> errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Datos inválidos", "Revisa los campos indicados");
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "Cuerpo inválido",
                "El JSON está mal formado o tiene tipos incorrectos"));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> errors.putIfAbsent(
                result.getMethodParameter().getParameterName(),
                result.getResolvableErrors().getFirst().getDefaultMessage()));
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Parámetros inválidos", "Revisa los parámetros indicados");
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
