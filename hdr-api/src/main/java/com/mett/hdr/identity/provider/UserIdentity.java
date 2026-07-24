package com.mett.hdr.identity.provider;

import java.util.List;

public record UserIdentity(Long userId, String globalUserId, String identitySource, List<String> roles) {
}
