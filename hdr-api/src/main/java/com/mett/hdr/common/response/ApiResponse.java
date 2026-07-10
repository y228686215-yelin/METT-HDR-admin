package com.mett.hdr.common.response;

import com.mett.hdr.common.request.RequestIdHolder;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId,
        OffsetDateTime timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return of(ResponseCodes.SUCCESS, "Success.", data);
    }

    public static <T> ApiResponse<T> error(ResponseCodes code, String message) {
        return of(code, message, null);
    }

    public static <T> ApiResponse<T> of(ResponseCodes code, String message, T data) {
        return new ApiResponse<>(
                code.name(),
                message,
                data,
                RequestIdHolder.currentOrGenerate(),
                OffsetDateTime.now(ZoneOffset.ofHours(8))
        );
    }
}
