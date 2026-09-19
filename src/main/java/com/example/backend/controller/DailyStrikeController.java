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

/**
 * DailyStrikeController — Backend Endpoints for Daily Streak Feature.
 *
 * Fixes Applied:
 *  - Disassociated from daily_bonus (uses independent streak_claimed_date & daily_streak keys)
 *  - GET /api/streak-status is strictly READ-ONLY and NEVER resets streak_count to 0 on Firestore
 *  - Handles raw token stripping ("Bearer ") safely
 */
@RestController
@RequestMapping("/api")
public class DailyStrikeController {

    @Autowired
    private StreakService service;

    @Autowired
    private Firestore firestore;

    // 🔥 CLAIM STREAK API
    @PostMapping("/claim-streak")
    public ResponseEntity<?> claim(
            @RequestHeader("Authorization") String token) {

        try {
            String rawToken = (token != null && token.startsWith("Bearer "))
                    ? token.substring(7).trim() : token;

            FirebaseToken decoded =
                    FirebaseAuth.getInstance().verifyIdToken(rawToken);

            String uid = decoded.getUid();

            Map<String, Object> res = service.claimStreak(uid);

            return ResponseEntity.ok(res);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", e.getMessage())
            );
        }
    }

    // 🔥 STREAK STATUS API (READ-ONLY)
    @GetMapping("/streak-status")
    public ResponseEntity<?> getStatus(
            @RequestHeader("Authorization") String token) {

        try {
            String rawToken = (token != null && token.startsWith("Bearer "))
                    ? token.substring(7).trim() : token;

            FirebaseToken decoded =
                    FirebaseAuth.getInstance().verifyIdToken(rawToken);

            String uid = decoded.getUid();

            DocumentSnapshot doc =
                    firestore.collection("users").document(uid).get().get();

            if (!doc.exists()) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "User not found")
                );
            }

            Long streak = doc.getLong("streak_count");
            if (streak == null) streak = 0L;

            String lastDate = doc.getString("streak_claimed_date");
            if (lastDate == null) {
                lastDate = doc.getString("last_streak_date");
            }
            if (lastDate == null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dailyStreakMap = (Map<String, Object>) doc.get("daily_streak");
                if (dailyStreakMap != null) {
                    lastDate = (String) dailyStreakMap.get("claimed_date");
                }
            }

            LocalDate todayDate = LocalDate.now(ZoneId.of("Asia/Kolkata"));
            String today = todayDate.toString();

            boolean claimedToday = today.equals(lastDate);

            // Strictly read-only status query — NEVER reset streak to 0 on GET!
            return ResponseEntity.ok(
                    Map.of(
                            "streak", streak,
                            "streak_count", streak,
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