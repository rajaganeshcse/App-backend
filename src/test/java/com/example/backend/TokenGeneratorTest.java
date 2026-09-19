package com.example.backend;

import com.example.backend.util.TokenGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

class TokenGeneratorTest {

    @Test
    void testTokenFormatAndLength() {
        String token = TokenGenerator.generate10CharToken();
        Assertions.assertNotNull(token);
        Assertions.assertEquals(10, token.length());
        Assertions.assertTrue(token.matches("^[A-Z0-9]{10}$"), "Token must consist of exactly 10 uppercase A-Z and 0-9 characters");
    }

    @Test
    void testTokenUniqueness() {
        int count = 1000;
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String token = TokenGenerator.generate10CharToken();
            Assertions.assertEquals(10, token.length());
            Assertions.assertTrue(token.matches("^[A-Z0-9]{10}$"));
            tokens.add(token);
        }
        Assertions.assertEquals(count, tokens.size(), "All 1,000 generated 10-character tokens must be unique");
    }
}
