package com.example.backend.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationService {

    public void send(String token) throws Exception {

        Map<String, String> data = new HashMap<>();
        data.put("title", "🎉 Withdraw Success");
        data.put("body", "₹200 credited");
        data.put("amount", "200");
        data.put("type", "withdraw");
        data.put("requestId", "REQ001");

        Message message = Message.builder()
                .setToken(token)
                .putAllData(data)
                .build();

        FirebaseMessaging.getInstance().send(message);
    }
}