package com.example.backend.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * StreakService — Handles Daily Streak claim logic on the backend.
 *
 * Fixes Applied:
 *  - Disassociated from daily_bonus (uses independent streak_claimed_date & daily_streak keys)
 *  - Leaves daily_bonus untouched so Daily Bonus card on home screen is completely independent
 *  - Calculates 7-day cycle progressive rewards (+10, +20, +30, +40, +50, +75, +100)
 */
@Service
public class StreakService {

    @Autowired
    private Firestore firestore;

    @Autowired
    private NotificationService notificationService;

    public Map<String, Object> claimStreak(String uid) throws Exception {

        DocumentReference ref = firestore.collection("users").document(uid);
        DocumentSnapshot doc = ref.get().get();

        if (!doc.exists()) throw new RuntimeException("User not found");

        // ✅ Use IST timezone (important for midnight reset)
        LocalDate todayDate = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        String today = todayDate.toString();

        // Get last claimed date for STREAK (not daily_bonus)
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

        LocalDate lastClaimDate = null;
        if (lastDate != null && !lastDate.trim().isEmpty()) {
            try {
                // If timestamp format e.g. 2026-09-19T10:00:00, extract 10-char date part
                String cleanDate = lastDate.length() >= 10 ? lastDate.substring(0, 10) : lastDate;
                lastClaimDate = LocalDate.parse(cleanDate);
            } catch (Exception ignored) {}
        }

        Long coins = doc.getLong("coins");
        if (coins == null) coins = 0L;

        Long streak = doc.getLong("streak_count");
        if (streak == null) streak = 0L;

        // ❌ Already claimed today
        if (today.equals(lastDate)) {
            throw new RuntimeException("Already claimed today");
        }

        // 🔥 Midnight Reset Logic
        // If user missed even 1 day (last claim was prior to yesterday) → reset streak to 0
        if (lastClaimDate != null && !lastClaimDate.plusDays(1).equals(todayDate)) {
            streak = 0L;
        }

        // ✅ Today claim → increment total continuous streak count
        streak++;

        // Calculate 1..7 cycle day position for reward lookup
        int cycleDay = (int) ((streak - 1) % 7 + 1);
        int reward = getReward(cycleDay);

        // 🔄 Update Firestore (independent streak keys)
        Map<String, Object> updates = new HashMap<>();
        updates.put("coins", coins + reward);
        updates.put("streak_count", streak);
        updates.put("streak_claimed_date", today);
        updates.put("last_streak_date", today);

        Map<String, Object> streakMap = new HashMap<>();
        streakMap.put("claimed_date", today);
        updates.put("daily_streak", streakMap);

        ref.update(updates);

        // 💰 Save transaction
        Map<String, Object> coinDetail = new HashMap<>();
        coinDetail.put("amount", reward);
        coinDetail.put("type", "Daily Streak");
        coinDetail.put("status", "Credit");
        coinDetail.put("istype", "coin");
        coinDetail.put("created_at", FieldValue.serverTimestamp());

        ref.collection("coinDetails").add(coinDetail);

        // 🔔 Send notification
        String token = doc.getString("fcmToken");
        if (token != null && !token.isEmpty()) {
            notificationService.send(
                    token,
                    "🔥 Awesome Streak! " + streak + " Days",
                    "Come back tomorrow for even more coins 🎉",
                    reward + " 🪙"
            );
        }

        // 📤 Response
        return Map.of(
                "reward", reward,
                "streak", streak,
                "claimedToday", true
        );
    }

    // 🎁 Reward Logic
    private int getReward(int day) {
        switch (day) {
            case 1: return 10;
            case 2: return 20;
            case 3: return 30;
            case 4: return 40;
            case 5: return 50;
            case 6: return 75;
            case 7: return 100;
            default: return 10;
        }
    }
}