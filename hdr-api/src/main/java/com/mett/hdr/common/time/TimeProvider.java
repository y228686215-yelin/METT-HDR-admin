package com.mett.hdr.common.time;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class TimeProvider {

    public OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.ofHours(8));
    }
}
