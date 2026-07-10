package com.mett.hdr.common.exception;

import com.mett.hdr.common.response.ResponseCodes;
import org.springframework.http.HttpStatus;

public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(ResponseCodes.FORBIDDEN, HttpStatus.FORBIDDEN, message);
    }
}
