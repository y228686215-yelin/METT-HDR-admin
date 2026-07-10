package com.mett.hdr.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.mett.hdr.common.request.RequestIdHolder;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @AfterEach
    void tearDown() {
        RequestIdHolder.clear();
    }

    @Test
    void successResponseContainsRequiredShape() {
        RequestIdHolder.set("req_test_123");

        ApiResponse<Map<String, String>> response = ApiResponse.success(Map.of("service", "mett-hdr-api"));

        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.message()).isEqualTo("Success.");
        assertThat(response.data()).containsEntry("service", "mett-hdr-api");
        assertThat(response.requestId()).isEqualTo("req_test_123");
        assertThat(response.timestamp()).isNotNull();
    }
}
