package com.start.overflow.shared.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesValidHeaderToMdcAndResponseThenAlwaysClearsIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader(CorrelationIdFilter.HEADER, "support-case-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> valueInsideChain = new AtomicReference<>();

        filter.doFilter(request, response,
                (currentRequest, currentResponse) -> valueInsideChain.set(
                        MDC.get(CorrelationIdContext.MDC_KEY)));

        assertThat(valueInsideChain.get()).isEqualTo("support-case-123");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER))
                .isEqualTo("support-case-123");
        assertThat(MDC.get(CorrelationIdContext.MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeExternalHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader(CorrelationIdFilter.HEADER, "invalid header with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (currentRequest, currentResponse) -> { });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER))
                .isNotBlank()
                .isNotEqualTo("invalid header with spaces");
    }
}
