package com.bistrobyte.gateway.config;

import com.bistrobyte.gateway.security.GatewayJwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GatewayJwtProperties.class)
public class GatewayConfiguration {
}
