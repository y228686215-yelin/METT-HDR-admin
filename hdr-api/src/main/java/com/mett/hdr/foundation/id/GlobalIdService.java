package com.mett.hdr.foundation.id;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GlobalIdService {

    public String userId() {
        return generate("usr_");
    }

    public String teamId() {
        return generate("team_");
    }

    public String organizationId() {
        return generate("org_");
    }

    public String projectId() {
        return generate("prj_");
    }

    public String spaceId() {
        return generate("space_");
    }

    public String productId() {
        return generate("prd_");
    }

    public String expertId() {
        return generate("exp_");
    }

    public String fileId() {
        return generate("gfile_");
    }

    public String reportId() {
        return generate("greport_");
    }

    public String jobId() {
        return generate("job_");
    }

    private String generate(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
