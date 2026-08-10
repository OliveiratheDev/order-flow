package com.start.overflow.payment.adapters.out.gateway;

import com.start.overflow.shared.observability.MdcPropagatingExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@Profile("payment-asaas & !payment-http & !payment-declined")
class AsaasResilienceConfiguration {

    static final String EXECUTOR = "asaasPaymentExecutor";

    @Bean(name = EXECUTOR, destroyMethod = "shutdown", defaultCandidate = false)
    ExecutorService asaasPaymentExecutor() {
        return new MdcPropagatingExecutorService(new ThreadPoolExecutor(
                4,
                8,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(50),
                Thread.ofPlatform().name("asaas-payment-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()));
    }
}
