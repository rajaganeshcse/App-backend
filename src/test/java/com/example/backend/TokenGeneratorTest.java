package com.example.backend;

import com.example.backend.util.TokenGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

class TokenGeneratorTest {

    @Test
    void testTokenFormatAndLength() {
        String token = TokenGenerator.generateToken();
        Assertions.assertNotNull(token);
        Assertions.assertEquals(5, token.length());
        Assertions.assertTrue(token.matches("^[A-Z0-9]{5}$"), "Token must consist of exactly 5 uppercase A-Z and 0-9 characters");
    }

    @Test
    void testTokenUniqueness() {
        int count = 1000;
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String token = TokenGenerator.generateToken();
            Assertions.assertEquals(5, token.length());
            Assertions.assertTrue(token.matches("^[A-Z0-9]{5}$"));
            tokens.add(token);
        }
        Assertions.assertTrue(tokens.size() > 950, "Generated 5-character tokens must have high uniqueness");
    }
}
