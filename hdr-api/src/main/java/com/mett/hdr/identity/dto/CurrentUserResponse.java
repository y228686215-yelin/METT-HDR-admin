package com.mett.hdr.identity.dto;

import java.util.List;

public record CurrentUserResponse(
        String globalUserId,
        String identitySource,
        List<String> roles
) {
}
