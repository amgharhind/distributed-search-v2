package com.search.distributed.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${reranking.service.url}")
    private String rerankingServiceUrl;

    @Bean
    public WebClient rerankingWebClient() {
        return WebClient.builder()
                .baseUrl(rerankingServiceUrl)
                .build();
    }
}