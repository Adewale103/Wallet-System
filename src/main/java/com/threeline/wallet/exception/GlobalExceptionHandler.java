package com.threeline.wallet.exception;

import com.threeline.wallet.dto.ApiError;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        log.warn("Handled API exception: {}", ex.getMessage());
        ApiError body = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatus().value())
                .error(ex.getStatus().getReasonPhrase())
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        ApiError body = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Validation failed")
                .details(details)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Thrown when a wallet update hits a concurrency conflict -- an optimistic-lock version
     * mismatch, or a pessimistic lock-wait timeout (covered by Spring's
     * {@link ConcurrencyFailureException} hierarchy either way). Transfers already retry this
     * internally a bounded number of times ({@code WalletServiceImpl.transfer}), so reaching this
     * handler means every retry was also lost to contention. Surfaced as 409 so the caller knows
     * the request itself was valid and can choose to retry, rather than treating it as a
     * permanent failure.
     */
    @ExceptionHandler({OptimisticLockException.class, ConcurrencyFailureException.class})
    public ResponseEntity<ApiError> handleConcurrentUpdate(Exception ex) {
        log.warn("Concurrent wallet update detected: {}", ex.getMessage());
        ApiError body = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.getReasonPhrase())
                .message("This wallet was updated concurrently. Please retry the operation.")
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ApiError body = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("An unexpected error occurred")
                .build();
        return ResponseEntity.internalServerError().body(body);
    }
}
