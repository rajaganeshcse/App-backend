package com.example.backend.service;

import com.google.errorprone.annotations.NoAllocation;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationService {

    public void send(String token,String tittle,String body,String amount) throws Exception {

        Map<String, String> data = new HashMap<>();
        data.put("title",tittle);
        data.put("body", body);
        data.put("amount",""+ amount);
        data.put("type", "withdraw");
        data.put("requestId", "REQ001");

        Message message = Message.builder()
                .setToken(token)
                .putAllData(data)
                .build();

        FirebaseMessaging.getInstance().send(message);
    }
}