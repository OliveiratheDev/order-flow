package com.start.overflow.payment.adapters.in.webhook;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookTemplateArchitectureTest {

    @Test
    void templateMethodCannotBeOverriddenByEventHandlers() throws Exception {
        Method handle = AsaasWebhookHandler.class.getDeclaredMethod(
                "handle", RawWebhookRequest.class);

        assertThat(Modifier.isFinal(handle.getModifiers())).isTrue();
    }
}
