package com.mett.hdr.common.exception;

import com.mett.hdr.common.response.ResponseCodes;
import org.springframework.http.HttpStatus;

public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(ResponseCodes.NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}
