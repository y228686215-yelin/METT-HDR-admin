package com.mett.hdr.common.exception;

import com.mett.hdr.common.response.ResponseCodes;
import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final ResponseCodes code;
    private final HttpStatus status;

    public BusinessException(String message) {
        this(ResponseCodes.UNPROCESSABLE_ENTITY, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public BusinessException(ResponseCodes code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public ResponseCodes code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
