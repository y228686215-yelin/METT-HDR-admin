package com.mett.hdr.foundation.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GlobalIdServiceTest {

    private final GlobalIdService globalIdService = new GlobalIdService();

    @Test
    void generatesExpectedPrefixes() {
        assertThat(globalIdService.userId()).startsWith("usr_");
        assertThat(globalIdService.teamId()).startsWith("team_");
        assertThat(globalIdService.organizationId()).startsWith("org_");
        assertThat(globalIdService.projectId()).startsWith("prj_");
        assertThat(globalIdService.spaceId()).startsWith("space_");
        assertThat(globalIdService.productId()).startsWith("prd_");
        assertThat(globalIdService.expertId()).startsWith("exp_");
        assertThat(globalIdService.fileId()).startsWith("gfile_");
        assertThat(globalIdService.reportId()).startsWith("greport_");
        assertThat(globalIdService.jobId()).startsWith("job_");
        assertThat(globalIdService.projectNumber()).startsWith("HDR-P-");
    }

    @Test
    void generatedValuesAreNonEmptyAndUnique() {
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            String id = globalIdService.projectId();
            assertThat(id).isNotBlank();
            ids.add(id);
        }

        assertThat(ids).hasSize(100);
    }
}
