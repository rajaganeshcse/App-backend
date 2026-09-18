package com.example.backend.util;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;

public class TokenUtil {

    public static String verify(String token) throws Exception {

        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing authorization token");
        }

        token = token.trim();

        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        FirebaseToken decoded =
                FirebaseAuth.getInstance().verifyIdToken(token);

        return decoded.getUid();
    }
}