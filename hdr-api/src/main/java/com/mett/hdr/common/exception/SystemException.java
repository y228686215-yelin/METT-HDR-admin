package com.mett.hdr.common.exception;

import com.mett.hdr.common.response.ResponseCodes;
import org.springframework.http.HttpStatus;

public class SystemException extends BusinessException {

    public SystemException(String message) {
        super(ResponseCodes.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
