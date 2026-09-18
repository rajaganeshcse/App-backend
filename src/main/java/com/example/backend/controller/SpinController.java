package com.example.backend.controller;

import com.example.backend.model.SpinResponse;
import com.example.backend.util.TokenUtil;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class SpinController {

    private static final int MAX_SPINS = 10;

    /* ================= COMMON METHOD ================= */

    private Map<String, Object> getUserSpinData(String userId) throws Exception {

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

        Map<String, Object> data = new HashMap<>();
        data.put("spinCount", spinCount);
        data.put("today", today);

        return data;
    }

    /* ================= SPIN STATUS ================= */

    @GetMapping("/spin-status")
    public ResponseEntity<?> spinStatus(@RequestHeader("Authorization") String token) {
        try {
            String userId = TokenUtil.verify(token);
            Map<String, Object> data = getUserSpinData(userId);

            int spinCount = (int) data.get("spinCount");
            int remaining = Math.max(0, MAX_SPINS - spinCount);

            return ResponseEntity.ok(Collections.singletonMap("remainingSpins", remaining));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or missing authorization token"));
        }
    }

    /* ================= SPIN ================= */

    @PostMapping("/spin")
    public ResponseEntity<?> spin(@RequestHeader("Authorization") String token) {
        try {
            String userId = TokenUtil.verify(token);

            Map<String, Object> data = getUserSpinData(userId);

            int spinCount = (int) data.get("spinCount");
            String today = (String) data.get("today");

            // ❌ Limit check
            if (spinCount >= MAX_SPINS) {
                return ResponseEntity.ok(new SpinResponse(0, 0));
            }

            // 🎯 Reward
            int[] rewards = {0, 5, 6, 7, 10};
            int reward = rewards[new Random().nextInt(rewards.length)];

            // 🔥 Update Firebase
            Firestore db = FirestoreClient.getFirestore();

            Map<String, Object> updates = new HashMap<>();
            updates.put("coins", FieldValue.increment(reward));
            updates.put("dailySpinCount", spinCount + 1);
            updates.put("lastSpinDate", today);

            db.collection("users")
                    .document(userId)
                    .set(updates, SetOptions.merge());

            // ✅ COIN HISTORY
            Map<String, Object> coinDetail = new HashMap<>();
            coinDetail.put("amount", reward);
            coinDetail.put("type", "Daily spin");
            coinDetail.put("status", "Credit");
            coinDetail.put("istype", "coin");
            coinDetail.put("created_at", FieldValue.serverTimestamp());

            DocumentReference userRef =
                    db.collection("users").document(userId);

            userRef.collection("coinDetails").add(coinDetail);

            int remaining = MAX_SPINS - (spinCount + 1);

            return ResponseEntity.ok(new SpinResponse(reward, remaining));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or missing authorization token"));
        }
    }
}
