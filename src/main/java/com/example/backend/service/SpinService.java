package com.example.backend.service;

import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class SpinService {

    private final Map<String, Integer> spinCount = new HashMap<>();
    private final Map<String, LocalDate> lastSpinDate = new HashMap<>();

    private static final int MAX_DAILY_SPINS = 10;

    /* ================= VALIDATION ================= */

    public boolean canSpin(String userId) {

        LocalDate today = LocalDate.now();

        if (!today.equals(lastSpinDate.get(userId))) {
            spinCount.put(userId, 0);
            lastSpinDate.put(userId, today);
        }

        return spinCount.getOrDefault(userId, 0) < MAX_DAILY_SPINS;
    }

    public void increaseSpin(String userId) {
        spinCount.put(userId, spinCount.getOrDefault(userId, 0) + 1);
    }

    /* ================= REWARD ================= */

    public int generateReward() {
        int[] rewards = {0, 5, 6, 7, 10};
        return rewards[new Random().nextInt(rewards.length)];
    }

    /* ================= FIREBASE UPDATE ================= */

    public void addCoinsToFirebase(String userId, int reward) {

        try {
            Firestore db = FirestoreClient.getFirestore();

            DocumentReference userRef =
                    db.collection("users").document(userId);

            // 🔥 Atomic increment
            Map<String, Object> updates = new HashMap<>();
            updates.put("coins", FieldValue.increment(reward));

            userRef.update(updates);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}