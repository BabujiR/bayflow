package com.bayflow.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdsTest {

    private final RequestIds.Filter filter = new RequestIds.Filter();

    @Test
    void keepsAGivenIdForTheRequestAndReturnsIt() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestIds.HEADER, "0123456789abcdef");
        var response = new MockHttpServletResponse();
        var seen = new AtomicReference<String>();

        filter.doFilter(request, response, (req, res) -> seen.set(RequestIds.current()));

        assertThat(seen.get()).isEqualTo("0123456789abcdef");
        assertThat(response.getHeader(RequestIds.HEADER)).isEqualTo("0123456789abcdef");
        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }

    @Test
    void replacesAMissingOrOddId() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestIds.HEADER, "bad id\nwith a newline");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestIds.HEADER)).matches("[0-9a-f-]{36}");
    }
}
