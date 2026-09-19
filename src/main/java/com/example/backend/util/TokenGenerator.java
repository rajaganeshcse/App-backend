package com.example.backend.util;

import java.security.SecureRandom;

public class TokenGenerator {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int LENGTH = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generates a cryptographically secure 5-character token consisting of
     * uppercase characters A-Z and digits 0-9.
     */
    public static String generateToken() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            int index = SECURE_RANDOM.nextInt(CHARS.length());
            sb.append(CHARS.charAt(index));
        }
        return sb.toString();
    }

    /** Alias for backward compatibility */
    public static String generate5CharToken() {
        return generateToken();
    }

    /** Alias for backward compatibility */
    public static String generate10CharToken() {
        return generateToken();
    }
}
