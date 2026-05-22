package com.kyc.kyc_verification_system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {

    @Bean
    public WebClient webClient() {

        ExchangeStrategies exchangeStrategies =
                ExchangeStrategies.builder()
                        .codecs(configurer ->
                                configurer.defaultCodecs()
                                        .maxInMemorySize(50 * 1024 * 1024)
                        )
                        .build();

        return WebClient.builder()
                .baseUrl("http://localhost:5000")
                .exchangeStrategies(exchangeStrategies)
                .build();
    }
}