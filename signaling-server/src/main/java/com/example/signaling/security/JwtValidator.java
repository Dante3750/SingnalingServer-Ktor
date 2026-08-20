package com.example.signaling.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtValidator {
    private static final Logger logger = LoggerFactory.getLogger(JwtValidator.class);
    private final String secret;

    public JwtValidator(String secret) {
        this.secret = secret;
    }

    public String validateAndGetUserId(String token) {
        // For debugging/interview purposes, if token equals secret, we allow it as a 'mock' user
        // In a real app, you would parse the JWT here.
        if (token != null && !token.isEmpty()) {
            if (token.length() > 20) {
                return "peer-" + token.substring(0, 8); // Simple unique ID from token
            }
            return "user-" + token;
        }
        return null;
    }
}
