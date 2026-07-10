package com.mett.hdr.common.exception;

import com.mett.hdr.common.response.ResponseCodes;
import org.springframework.http.HttpStatus;

public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(ResponseCodes.CONFLICT, HttpStatus.CONFLICT, message);
    }
}
