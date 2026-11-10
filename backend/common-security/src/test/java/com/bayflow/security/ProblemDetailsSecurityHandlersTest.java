package com.bayflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class ProblemDetailsSecurityHandlersTest {

    private final ProblemDetailsSecurityHandlers handlers = new ProblemDetailsSecurityHandlers();

    @Test
    void unauthenticatedRequestGets401ProblemDetail() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/things");
        var response = new MockHttpServletResponse();

        handlers.commence(request, response, new BadCredentialsException("bad"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentAsString())
                .contains("\"status\":401")
                .contains("\"type\":\"urn:bayflow:problem:unauthorized\"")
                .contains("\"instance\":\"/api/v1/things\"");
    }

    @Test
    void forbiddenRequestGets403ProblemDetail() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/things");
        var response = new MockHttpServletResponse();

        handlers.handle(request, response, new AccessDeniedException("no"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"status\":403").contains("forbidden");
    }

    @Test
    void jsonEscapesSpecialCharacters() {
        assertThat(ProblemDetailsSecurityHandlers.json("a\"b\\c\n\u0001"))
                .isEqualTo("\"a\\\"b\\\\c\\n\\u0001\"");
        assertThat(ProblemDetailsSecurityHandlers.json(null)).isEqualTo("null");
    }
}
