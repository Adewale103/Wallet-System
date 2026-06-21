package com.threeline.wallet.config;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class AccountNumberGenerator {

    private static final int LENGTH = 10;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        sb.append(1 + random.nextInt(9));
        for (int i = 1; i < LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
