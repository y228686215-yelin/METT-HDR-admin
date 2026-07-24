package com.mett.hdr.auth.token;

public record TokenIssueResult(String accessToken, long expiresIn) {
}
