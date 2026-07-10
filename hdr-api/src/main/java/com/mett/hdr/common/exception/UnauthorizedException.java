package com.mett.hdr.common.exception;

import com.mett.hdr.common.response.ResponseCodes;
import org.springframework.http.HttpStatus;

public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String message) {
        super(ResponseCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
    }
}
