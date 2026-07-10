package com.mett.hdr.common.request;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mett.hdr.HdrApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = HdrApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestIdFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reusesValidIncomingRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/app/meta").header("X-Request-Id", "req_external_123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "req_external_123"))
                .andExpect(jsonPath("$.requestId").value("req_external_123"));
    }

    @Test
    void generatesRequestIdWhenIncomingValueIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/app/meta").header("X-Request-Id", "bad value with spaces"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", startsWith("req_")))
                .andExpect(jsonPath("$.requestId", startsWith("req_")));
    }
}
