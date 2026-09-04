package com.porbe.app.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Declara el cliente HTTP privado que comunica Spring Boot con el servicio Node. */
@Configuration
@EnableConfigurationProperties(WhatsAppProperties.class)
public class WhatsAppConfig {

    @Bean
    RestClient whatsAppRestClient(WhatsAppProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds()));

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("X-Porbe-Internal-Token", properties.internalToken())
                .build();
    }
}
