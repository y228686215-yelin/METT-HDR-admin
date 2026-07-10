package com.mett.hdr.common.request;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private final String headerName;

    public RequestIdFilter(@Value("${mett.hdr.request-id.header-name:X-Request-Id}") String headerName) {
        this.headerName = headerName;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incomingRequestId = request.getHeader(headerName);
        String requestId = RequestIdHolder.isValid(incomingRequestId)
                ? incomingRequestId
                : RequestIdHolder.generate();

        RequestIdHolder.set(requestId);
        MDC.put("requestId", requestId);
        response.setHeader(headerName, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
            RequestIdHolder.clear();
        }
    }
}
