package com.start.overflow.shared.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedUserMdcFilterTest {
    private final AuthenticatedUserMdcFilter filter = new AuthenticatedUserMdcFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void enrichesMdcWithAuthenticatedSubjectAndRemovesItAfterRequest() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("7", "token", java.util.List.of()));
        AtomicReference<String> valueInsideChain = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> valueInsideChain.set(MDC.get("userId")));

        assertThat(valueInsideChain.get()).isEqualTo("7");
        assertThat(MDC.get("userId")).isNull();
    }
}
