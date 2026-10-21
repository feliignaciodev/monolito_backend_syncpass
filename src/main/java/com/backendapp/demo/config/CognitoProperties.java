package com.backendapp.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cognito")
public record CognitoProperties(String clientId, String region, String userPoolId) {
}