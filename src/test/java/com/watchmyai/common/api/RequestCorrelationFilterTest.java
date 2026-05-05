package com.watchmyai.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void echoesValidRequestIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelation.REQUEST_ID_HEADER, "watch-test-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestCorrelation.REQUEST_ID_HEADER)).isEqualTo("watch-test-123");
        assertThat(request.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE)).isEqualTo("watch-test-123");
    }

    @Test
    void generatesRequestIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestCorrelation.REQUEST_ID_HEADER)).isNotBlank();
        assertThat(request.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE)).isEqualTo(
                response.getHeader(RequestCorrelation.REQUEST_ID_HEADER)
        );
    }
}
