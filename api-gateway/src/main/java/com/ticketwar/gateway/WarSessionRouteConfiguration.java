package com.ticketwar.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class WarSessionRouteConfiguration {

    @Bean
    RouteLocator warSessionRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("war-session-service", route -> route
                        .path("/api/war-sessions", "/api/war-sessions/**")
                        .uri("http://localhost:8082"))
                .build();
    }
}
