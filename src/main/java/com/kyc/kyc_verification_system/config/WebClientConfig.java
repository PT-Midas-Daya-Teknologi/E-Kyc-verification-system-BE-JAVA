package com.kyc.kyc_verification_system.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${python.api.base-url}")
    private String pythonApiBaseUrl;

    @Value("${python.api.connect-timeout-ms}")
    private int connectTimeoutMs;

    @Value("${python.api.get-read-timeout-ms}")
    private int getReadTimeoutMs;

    @Value("${python.api.post-read-timeout-ms}")
    private int postReadTimeoutMs;

    @Bean(name = "pythonGetWebClient")
    public WebClient pythonGetWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(getReadTimeoutMs))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs);

        return WebClient.builder()
                .baseUrl(pythonApiBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean(name = "pythonPostWebClient")
    public WebClient pythonPostWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(postReadTimeoutMs))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs);

        return WebClient.builder()
                .baseUrl(pythonApiBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
