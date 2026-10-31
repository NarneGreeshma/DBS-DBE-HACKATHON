package com.smarthire.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts every exception into the uniform error contract used by the React client:
 * <pre>{timestamp,status,error,message,path,fieldErrors}</pre>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ApiError(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path,
            Map<String, String> fieldErrors) {
    }

    private ApiError body(HttpStatus status, String message, HttpServletRequest req,
                          Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                message, req.getRequestURI(), fieldErrors);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return respond(HttpStatus.BAD_REQUEST, "Validation failed", req, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex,
                                                     HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                fields.put(v.getPropertyPath().toString(), v.getMessage()));
        return respond(HttpStatus.BAD_REQUEST, "Validation failed", req, fields);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest req) {
        return respond(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    @ExceptionHandler({BadCredentialsException.class})
    public ResponseEntity<ApiError> handleBadCredentials(Exception ex, HttpServletRequest req) {
        return respond(HttpStatus.UNAUTHORIZED, "Invalid email or password", req, null);
    }

    /**
     * Raised by {@code CurrentUser.require()} on permitAll routes (e.g. GET /api/auth/me
     * without a token). Without this rule it fell through to the generic handler as a 500.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        return respond(HttpStatus.UNAUTHORIZED, "Authentication required", req, null);
    }

    @ExceptionHandler({DisabledException.class, LockedException.class})
    public ResponseEntity<ApiError> handleAccount(Exception ex, HttpServletRequest req) {
        return respond(HttpStatus.FORBIDDEN, ex.getMessage(), req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return respond(HttpStatus.FORBIDDEN, "You do not have permission to perform this action", req, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex,
                                                 HttpServletRequest req) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), req, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex,
                                                       HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + ex.getName() + "'", req, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, "Malformed request body", req, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return respond(HttpStatus.NOT_FOUND, "Resource not found", req, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file is larger than 10 MB", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", req, null);
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, String message,
                                             HttpServletRequest req, Map<String, String> fields) {
        return ResponseEntity.status(status).body(body(status, message, req, fields));
    }
}
