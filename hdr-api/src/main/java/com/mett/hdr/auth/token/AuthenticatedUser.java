package com.mett.hdr.auth.token;

import java.util.List;

public record AuthenticatedUser(Long userId, String globalUserId, List<String> roles) {
}
