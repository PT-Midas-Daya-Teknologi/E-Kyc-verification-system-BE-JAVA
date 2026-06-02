package com.kyc.kyc_verification_system.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Value("${python.api.base-url:http://localhost:8000}")
    private String pythonApiBaseUrl;

    @Value("${python.api.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    @Value("${python.api.get-read-timeout-ms:15000}")
    private int getReadTimeoutMs;

    @Value("${python.api.post-read-timeout-ms:300000}")
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
