package com.example.backend.service;

import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class SpinService {

    private static final int MAX_DAILY_SPINS = 10;

    /* ================= GET SPIN STATUS ================= */

    public int getRemainingSpins(String userId) throws Exception {

        Firestore db = FirestoreClient.getFirestore();

        DocumentSnapshot doc =
                db.collection("users").document(userId).get().get();

        int spinCount = 0;
        String lastDate = "";

        if (doc.exists()) {

            spinCount = doc.getLong("dailySpinCount") != null ?
                    doc.getLong("dailySpinCount").intValue() : 0;

            lastDate = doc.getString("lastSpinDate") != null ?
                    doc.getString("lastSpinDate") : "";
        }

        String today = LocalDate.now().toString();

        // 🔄 reset daily
        if (!today.equals(lastDate)) {
            spinCount = 0;
        }

        return MAX_DAILY_SPINS - spinCount;
    }

    /* ================= SPIN ================= */

    public SpinResult spin(String userId) throws Exception {

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

        if (!today.equals(lastDate)) {
            spinCount = 0;
        }

        if (spinCount >= MAX_DAILY_SPINS) {
            return new SpinResult(0, 0);
        }

        // 🎯 reward
        int[] rewards = {0, 5, 6, 7, 10};
        int reward = rewards[new Random().nextInt(rewards.length)];

        // 🔥 update Firebase
        Map<String, Object> updates = new HashMap<>();
        updates.put("coins", FieldValue.increment(reward));
        updates.put("dailySpinCount", spinCount + 1);
        updates.put("lastSpinDate", today);

        ref.set(updates, SetOptions.merge());

        int remaining = MAX_DAILY_SPINS - (spinCount + 1);

        return new SpinResult(reward, remaining);
    }

    /* ================= INNER CLASS ================= */

    public static class SpinResult {
        public int reward;
        public int remaining;

        public SpinResult(int reward, int remaining) {
            this.reward = reward;
            this.remaining = remaining;
        }
    }
}