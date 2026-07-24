package com.mett.hdr.auth.dto;

public record RefreshResponse(String accessToken, long expiresIn) {
}
