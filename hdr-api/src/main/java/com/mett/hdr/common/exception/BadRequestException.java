package com.mett.hdr.common.exception;

import com.mett.hdr.common.response.ResponseCodes;
import org.springframework.http.HttpStatus;

public class BadRequestException extends BusinessException {

    public BadRequestException(String message) {
        super(ResponseCodes.BAD_REQUEST, HttpStatus.BAD_REQUEST, message);
    }
}
