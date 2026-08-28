package com.porbe.app.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MarketDataProperties.class)
/** Declara la infraestructura HTTP usada por el proveedor de datos de mercado. */
public class MarketDataConfig {

    @Bean
    RestClient yahooFinanceRestClient(MarketDataProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds()));

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", properties.userAgent())
                .build();
    }

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }
}
