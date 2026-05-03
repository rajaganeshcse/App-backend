package com.example.backend.controller;

import com.example.backend.service.StreakService;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DailyStrikeController {

    @Autowired
    private StreakService service;

    @Autowired
    private Firestore firestore; // ✅ FIXED

    // 🔥 CLAIM API
    @PostMapping("/claim-streak")
    public ResponseEntity<?> claim(
            @RequestHeader("Authorization") String token) {

        try {
            FirebaseToken decoded =
                    FirebaseAuth.getInstance().verifyIdToken(token);

            String uid = decoded.getUid();

            Map<String, Object> res = service.claimStreak(uid);

            return ResponseEntity.ok(res);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", e.getMessage())
            );
        }
    }

    // 🔥 STATUS API
    @GetMapping("/streak-status")
    public ResponseEntity<?> getStatus(
            @RequestHeader("Authorization") String token) {

        try {
            FirebaseToken decoded =
                    FirebaseAuth.getInstance().verifyIdToken(token);

            String uid = decoded.getUid();

            DocumentSnapshot doc =
                    firestore.collection("users").document(uid).get().get();

            // ❌ user not found
            if (!doc.exists()) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "User not found")
                );
            }

            Long streak = doc.getLong("streak_count");
            if (streak == null) streak = 0L;

            Map<String, Object> dailyBonus =
                    (Map<String, Object>) doc.get("daily_bonus");

            String today = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();

            String lastDate = dailyBonus != null
                    ? (String) dailyBonus.get("claimed_date")
                    : null;

            boolean claimedToday = today.equals(lastDate);

            return ResponseEntity.ok(
                    Map.of(
                            "streak", streak,
                            "claimedToday", claimedToday
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", e.getMessage())
            );
        }
    }
}