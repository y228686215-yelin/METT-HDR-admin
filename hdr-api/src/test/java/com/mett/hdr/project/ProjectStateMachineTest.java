package com.mett.hdr.project;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.project.service.ProjectStateMachine;
import org.junit.jupiter.api.Test;

class ProjectStateMachineTest {

    private final ProjectStateMachine stateMachine = new ProjectStateMachine();

    @Test
    void permitsOnlyDocumentedTransitions() {
        assertThatCode(() -> stateMachine.requireTransition("DRAFT", "ACTIVE"))
                .doesNotThrowAnyException();
        assertThatCode(() -> stateMachine.requireTransition("DRAFT", "ARCHIVED"))
                .doesNotThrowAnyException();
        assertThatCode(() -> stateMachine.requireTransition("ACTIVE", "ARCHIVED"))
                .doesNotThrowAnyException();
        assertThatCode(() -> stateMachine.requireTransition("ARCHIVED", "ACTIVE"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> stateMachine.requireTransition("ACTIVE", "DRAFT"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> stateMachine.requireTransition("ACTIVE", "ACTIVE"))
                .isInstanceOf(ConflictException.class);
    }
}
