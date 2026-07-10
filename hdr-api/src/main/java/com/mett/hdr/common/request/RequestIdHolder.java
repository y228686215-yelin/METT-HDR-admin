package com.mett.hdr.common.request;

import java.util.UUID;

public final class RequestIdHolder {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private static final String PREFIX = "req_";

    private RequestIdHolder() {
    }

    public static void set(String requestId) {
        CURRENT.set(requestId);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static String currentOrGenerate() {
        String requestId = CURRENT.get();
        if (requestId == null || requestId.isBlank()) {
            requestId = generate();
            CURRENT.set(requestId);
        }
        return requestId;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static String generate() {
        return PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    public static boolean isValid(String requestId) {
        return requestId != null
                && requestId.length() >= 8
                && requestId.length() <= 128
                && requestId.matches("[A-Za-z0-9_.:-]+");
    }
}
