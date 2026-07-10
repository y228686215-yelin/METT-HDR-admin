package com.mett.hdr.interfaces.integration;

import com.mett.hdr.common.response.ApiResponse;
import com.mett.hdr.foundation.meta.MetaResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations/evaluation")
public class EvaluationIntegrationMetaController {

    @GetMapping("/meta")
    public ApiResponse<MetaResponse> meta() {
        return ApiResponse.success(new MetaResponse("mett-hdr-api", "integration-evaluation", "v1"));
    }
}
