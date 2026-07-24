package com.mett.hdr.payment.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class PaymentOrderCreateRequest {

    private final String globalOfferId;
    private final String subjectType;
    private final String globalTeamId;

    @JsonCreator
    public PaymentOrderCreateRequest(
            @JsonProperty("globalOfferId") String globalOfferId,
            @JsonProperty("subjectType") String subjectType,
            @JsonProperty("globalTeamId") String globalTeamId
    ) {
        this.globalOfferId = globalOfferId;
        this.subjectType = subjectType;
        this.globalTeamId = globalTeamId;
    }

    public String globalOfferId() {
        return globalOfferId;
    }

    public String subjectType() {
        return subjectType;
    }

    public String globalTeamId() {
        return globalTeamId;
    }

    @JsonAnySetter
    public void rejectUntrustedField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported order field: " + name);
    }
}
