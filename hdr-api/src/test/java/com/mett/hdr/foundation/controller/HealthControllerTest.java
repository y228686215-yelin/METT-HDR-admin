package com.mett.hdr.foundation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsUnifiedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.data.service").value("mett-hdr-api"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void readinessReportsUnknownForUnwiredDependencies() throws Exception {
        mockMvc.perform(get("/api/v1/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.checks.database").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.checks.redis").value("UNKNOWN"));
    }
}
