package com.example.backend.controller;

import com.example.backend.model.SpinResponse;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class SpinController {

    private static final int MAX_SPINS = 10;

    @PostMapping("/spin")
    public SpinResponse spin(
            @RequestHeader("Authorization") String token,
            @RequestParam String userId) throws Exception {

        Firestore db = FirestoreClient.getFirestore();

        DocumentReference ref = db.collection("users").document(userId);
        DocumentSnapshot doc = ref.get().get();

        int spinCount = 0;
        String lastDate = "";

        if (doc.exists()) {
            spinCount = doc.getLong("dailySpinCount") != null ?
                    doc.getLong("dailySpinCount").intValue() : 0;

            lastDate = doc.getString("lastSpinDate") != null ?
                    doc.getString("lastSpinDate") : "";
        }

        String today = LocalDate.now().toString();

        // 🔄 Reset daily
        if (!today.equals(lastDate)) {
            spinCount = 0;
        }

        // ❌ Limit check
        if (spinCount >= MAX_SPINS) {
            return new SpinResponse(0, 0);
        }

        // 🎯 Reward
        int[] rewards = {0, 5, 6, 7, 10};
        int reward = rewards[new Random().nextInt(rewards.length)];

        // 🔥 Update Firebase
        Map<String, Object> updates = new HashMap<>();
        updates.put("coins", FieldValue.increment(reward));
        updates.put("dailySpinCount", spinCount + 1);
        updates.put("lastSpinDate", today);

        ref.set(updates, SetOptions.merge());

        int remaining = MAX_SPINS - (spinCount + 1);

        return new SpinResponse(reward, remaining);
    }
}