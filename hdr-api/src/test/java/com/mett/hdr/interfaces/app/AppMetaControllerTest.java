package com.mett.hdr.interfaces.app;

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
class AppMetaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void appMetaReturnsUnifiedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/app/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.service").value("mett-hdr-api"))
                .andExpect(jsonPath("$.data.apiGroup").value("app"))
                .andExpect(jsonPath("$.data.version").value("v1"));
    }
}
