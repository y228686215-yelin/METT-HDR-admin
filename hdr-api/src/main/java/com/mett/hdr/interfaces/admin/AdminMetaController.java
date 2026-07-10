package com.mett.hdr.interfaces.admin;

import com.mett.hdr.common.response.ApiResponse;
import com.mett.hdr.foundation.meta.MetaResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminMetaController {

    @GetMapping("/meta")
    public ApiResponse<MetaResponse> meta() {
        return ApiResponse.success(new MetaResponse("mett-hdr-api", "admin", "v1"));
    }
}
