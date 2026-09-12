package com.sinognss.cloud.vantix.common.exception;

import com.sinognss.cloud.base.common.api.CommonResult;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import com.sinognss.cloud.vantix.application.offline.OfflineImportFailure;
import com.sinognss.cloud.vantix.application.offline.OfflineImportValidationException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OfflineImportValidationException.class)
    public ResponseEntity<OfflineImportFailure> handleOfflineImport(OfflineImportValidationException exception) {
        return ResponseEntity.badRequest().body(new OfflineImportFailure(false, exception.getErrors()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<CommonResult<?>> handleBusiness(BusinessException exception) {
        HttpStatus status = exception.getVantixErrorCode() == ErrorCode.NOT_FOUND
                ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(CommonResultAdapter.failure(exception.getVantixErrorCode().value(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResult<?>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(CommonResultAdapter.failure(ErrorCode.INVALID_ARGUMENT.value(), message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CommonResult<?>> handleConstraint(ConstraintViolationException exception) {
        return ResponseEntity.badRequest()
                .body(CommonResultAdapter.failure(ErrorCode.INVALID_ARGUMENT.value(), exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResult<?>> handleUnexpected(Exception exception) {
        log.error("Unhandled Vantix request error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResultAdapter.failure(ErrorCode.INTERNAL_ERROR.value(), "系统内部错误"));
    }
}
