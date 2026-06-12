package com.example.backend.service;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Component
public class ReferralUtil {

    public static String generateCode() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10)
                .toUpperCase();
    }
}
