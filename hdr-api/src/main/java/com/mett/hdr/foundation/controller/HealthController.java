package com.mett.hdr.foundation.controller;

import com.mett.hdr.common.response.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(Map.of(
                "service", "mett-hdr-api",
                "status", "UP"
        ));
    }

    @GetMapping("/liveness")
    public ApiResponse<Map<String, Object>> liveness() {
        return ApiResponse.success(Map.of(
                "service", "mett-hdr-api",
                "status", "UP"
        ));
    }

    @GetMapping("/readiness")
    public ApiResponse<Map<String, Object>> readiness() {
        return ApiResponse.success(Map.of(
                "service", "mett-hdr-api",
                "status", "UNKNOWN",
                "checks", Map.of(
                        "database", "UNKNOWN",
                        "redis", "UNKNOWN"
                )
        ));
    }
}
