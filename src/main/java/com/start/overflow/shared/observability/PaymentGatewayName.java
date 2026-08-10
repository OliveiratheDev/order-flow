package com.start.overflow.shared.observability;

public enum PaymentGatewayName {
    ASAAS("asaas"),
    HTTP("http"),
    SIMULATED("simulated"),
    DECLINED("declined");

    private final String metricValue;

    PaymentGatewayName(String metricValue) {
        this.metricValue = metricValue;
    }

    public String metricValue() {
        return metricValue;
    }
}
