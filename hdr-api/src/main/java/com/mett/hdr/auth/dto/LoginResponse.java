package com.mett.hdr.auth.dto;

public record LoginResponse(String accessToken, long expiresIn) {
}
