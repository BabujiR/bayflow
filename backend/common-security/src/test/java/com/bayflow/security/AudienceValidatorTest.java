package com.bayflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("bayflow");

    @Test
    void acceptsMatchingAudience() {
        assertThat(validator.validate(TestJwts.jwt(Map.of("sub", "x", "aud", List.of("bayflow")))).hasErrors())
                .isFalse();
    }

    @Test
    void rejectsOtherAudience() {
        assertThat(validator.validate(TestJwts.jwt(Map.of("sub", "x", "aud", List.of("other")))).hasErrors())
                .isTrue();
    }

    @Test
    void rejectsMissingAudience() {
        assertThat(validator.validate(TestJwts.jwt(Map.of("sub", "x"))).hasErrors()).isTrue();
    }
}
