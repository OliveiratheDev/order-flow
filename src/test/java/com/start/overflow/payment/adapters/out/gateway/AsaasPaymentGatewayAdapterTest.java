package com.start.overflow.payment.adapters.out.gateway;

import com.start.overflow.payment.domain.ChargeRequest;
import com.start.overflow.payment.domain.GatewayChargeStatus;
import com.start.overflow.payment.domain.Payer;
import com.start.overflow.payment.domain.PaymentAmount;
import com.start.overflow.payment.domain.PaymentGatewayUnavailableException;
import com.start.overflow.payment.domain.PaymentMethod;
import com.start.overflow.payment.domain.PaymentRejectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AsaasPaymentGatewayAdapterTest {
    private static final String BASE_URL = "https://api-sandbox.asaas.com/v3";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);

    private MockRestServiceServer server;
    private AsaasPaymentGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new AsaasPaymentGatewayAdapter(builder, properties("sandbox-key"), CLOCK);
    }

    @Test
    void createsPayerAndPendingChargeWithSafeHeadersAndInternalReferences() {
        expectCustomerSearch("{\"data\":[]}");
        server.expect(requestTo(BASE_URL + "/customers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", "sandbox-key"))
                .andExpect(header(HttpHeaders.USER_AGENT, "OrderFlow/1.0 (test)"))
                .andExpect(header("X-Correlation-Id", "correlation-123"))
                .andExpect(content().json("""
                        {
                          "name":"Maria da Silva",
                          "cpfCnpj":"52998224725",
                          "email":"maria@example.com",
                          "externalReference":"7",
                          "notificationDisabled":true
                        }
                        """))
                .andRespond(withSuccess("{\"id\":\"cus_123\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", "sandbox-key"))
                .andExpect(header("X-Correlation-Id", "correlation-123"))
                .andExpect(content().json("""
                        {
                          "customer":"cus_123",
                          "billingType":"PIX",
                          "value":149.90,
                          "dueDate":"2026-08-12",
                          "description":"Pedido OrderFlow #10",
                          "externalReference":"orderflow-order-10"
                        }
                        """))
                .andRespond(withSuccess("""
                        {"id":"pay_123","status":"PENDING",
                         "invoiceUrl":"https://sandbox.asaas.com/i/123"}
                        """, MediaType.APPLICATION_JSON));

        var result = adapter.createCharge(request());

        assertThat(result.status()).isEqualTo(GatewayChargeStatus.PENDING);
        assertThat(result.externalId()).isEqualTo("pay_123");
        assertThat(result.paymentUrl()).isEqualTo("https://sandbox.asaas.com/i/123");
        server.verify();
    }

    @Test
    void reusesExistingPayerAndMapsConfirmedChargeAsApproved() {
        expectCustomerSearch("{\"data\":[{\"id\":\"cus_existing\"}]}");
        server.expect(requestTo(BASE_URL + "/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"customer\":\"cus_existing\"}"))
                .andRespond(withSuccess(
                        "{\"id\":\"pay_approved\",\"status\":\"CONFIRMED\"}",
                        MediaType.APPLICATION_JSON));

        var result = adapter.createCharge(request());

        assertThat(result.status()).isEqualTo(GatewayChargeStatus.APPROVED);
        server.verify();
    }

    @Test
    void retrievesChargeAndMapsReceivedStatus() {
        server.expect(requestTo(BASE_URL + "/payments/pay_123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", "sandbox-key"))
                .andRespond(withSuccess(
                        "{\"id\":\"pay_123\",\"status\":\"RECEIVED\"}",
                        MediaType.APPLICATION_JSON));

        var result = adapter.getCharge("pay_123");

        assertThat(result.status()).isEqualTo(GatewayChargeStatus.APPROVED);
        server.verify();
    }

    @Test
    void cancelsChargeInAsaas() {
        server.expect(requestTo(BASE_URL + "/payments/pay_123"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("access_token", "sandbox-key"))
                .andRespond(withSuccess());

        adapter.cancelCharge("pay_123");

        server.verify();
    }

    @Test
    void translatesInvalidChargeIntoDomainRejectionWithoutLeakingProviderBody() {
        expectCustomerSearch("{\"data\":[{\"id\":\"cus_existing\"}]}");
        server.expect(requestTo(BASE_URL + "/payments"))
                .andRespond(withBadRequest().body(
                        "{\"errors\":[{\"description\":\"internal provider detail\"}]}"));

        assertThatThrownBy(() -> adapter.createCharge(request()))
                .isInstanceOf(PaymentRejectedException.class)
                .hasMessage("O Asaas rejeitou os dados da cobrança")
                .hasMessageNotContaining("internal provider detail");
        server.verify();
    }

    @Test
    void translatesServerFailureIntoGatewayUnavailable() {
        server.expect(requestTo(BASE_URL + "/customers?externalReference=7&limit=1"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.createCharge(request()))
                .isInstanceOf(PaymentGatewayUnavailableException.class)
                .hasMessage("O Asaas está temporariamente indisponível");
        server.verify();
    }

    @Test
    void refusesToStartProfileWithoutApiKey() {
        RestClient.Builder builder = RestClient.builder();

        assertThatThrownBy(() -> new AsaasPaymentGatewayAdapter(
                builder, properties(" "), CLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ASAAS_API_KEY");
    }

    private void expectCustomerSearch(String response) {
        server.expect(requestTo(BASE_URL + "/customers?externalReference=7&limit=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", "sandbox-key"))
                .andExpect(header(HttpHeaders.USER_AGENT, "OrderFlow/1.0 (test)"))
                .andExpect(header("X-Correlation-Id", "correlation-123"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private ChargeRequest request() {
        return new ChargeRequest(10L,
                new Payer(7L, "Maria da Silva", "maria@example.com", "52998224725"),
                new PaymentAmount(new BigDecimal("149.90")),
                PaymentMethod.PIX,
                "correlation-123");
    }

    private AsaasProperties properties(String apiKey) {
        return new AsaasProperties(BASE_URL, apiKey,
                "OrderFlow/1.0 (test)", 3, "");
    }
}
