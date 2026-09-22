package com.bistrobyte.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared JWT plumbing picked up by every servlet-based BistroByte service through the
 * {@code com.bistrobyte} component scan. Each service still owns its own
 * {@code SecurityFilterChain} so it can express its own endpoint rules.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtSecuritySupportConfiguration {

    @Bean
    public JwtService jwtService(JwtProperties properties) {
        return new JwtService(properties);
    }

    @Bean
    public RestAuthenticationEntryPoint restAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new RestAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    public RestAccessDeniedHandler restAccessDeniedHandler(ObjectMapper objectMapper) {
        return new RestAccessDeniedHandler(objectMapper);
    }
}
