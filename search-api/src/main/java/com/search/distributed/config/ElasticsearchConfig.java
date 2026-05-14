package com.search.distributed.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.uris}")
    private String elasticsearchUris;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        HttpHost[] hosts = Arrays.stream(elasticsearchUris.split(","))
                .map(String::trim)
                .map(HttpHost::create)
                .toArray(HttpHost[]::new);

        RestClient restClient = RestClient.builder(hosts).build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}