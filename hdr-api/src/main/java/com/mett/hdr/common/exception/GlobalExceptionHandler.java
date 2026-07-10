package com.mett.hdr.common.exception;

import com.mett.hdr.common.request.RequestIdHolder;
import com.mett.hdr.common.response.ApiResponse;
import com.mett.hdr.common.response.ResponseCodes;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ResponseCodes.BAD_REQUEST, blankToDefault(message, "Invalid request.")));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ResponseCodes.BAD_REQUEST, "Invalid request."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ResponseCodes.NOT_FOUND, "Resource not found."));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.warn("requestId={} business exception code={} status={} message={}",
                RequestIdHolder.get(), ex.code(), ex.status().value(), ex.getMessage());
        return ResponseEntity.status(ex.status())
                .body(ApiResponse.error(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("requestId={} unexpected exception", RequestIdHolder.get(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ResponseCodes.INTERNAL_SERVER_ERROR, "Internal server error."));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + " " + blankToDefault(error.getDefaultMessage(), "is invalid");
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
