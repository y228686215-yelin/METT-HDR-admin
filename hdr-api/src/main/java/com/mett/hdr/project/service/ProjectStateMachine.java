package com.mett.hdr.project.service;

import com.mett.hdr.common.exception.ConflictException;
import org.springframework.stereotype.Service;

@Service
public class ProjectStateMachine {

    public void requireTransition(String current, String target) {
        boolean allowed = "DRAFT".equals(current)
                && ("ACTIVE".equals(target) || "ARCHIVED".equals(target))
                || "ACTIVE".equals(current) && "ARCHIVED".equals(target)
                || "ARCHIVED".equals(current) && "ACTIVE".equals(target);
        if (!allowed) {
            throw new ConflictException("Invalid project status transition.");
        }
    }
}
