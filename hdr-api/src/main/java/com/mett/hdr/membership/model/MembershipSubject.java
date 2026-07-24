package com.mett.hdr.membership.model;

public record MembershipSubject(
        String subjectType,
        Long userId,
        Long teamId
) {
    public static MembershipSubject user(Long userId) {
        return new MembershipSubject("USER", userId, null);
    }

    public static MembershipSubject team(Long teamId) {
        return new MembershipSubject("TEAM", null, teamId);
    }

    public String planAudience() {
        return "USER".equals(subjectType) ? "PERSONAL" : "TEAM";
    }

    public Long subjectId() {
        return "USER".equals(subjectType) ? userId : teamId;
    }
}
