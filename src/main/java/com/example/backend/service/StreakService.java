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

        // Get last claimed date
        Map<String, Object> dailyBonus = (Map<String, Object>) doc.get("daily_bonus");
        String lastDate = dailyBonus != null ? (String) dailyBonus.get("claimed_date") : null;

        LocalDate lastClaimDate = lastDate != null ? LocalDate.parse(lastDate) : null;

        Long coins = doc.getLong("coins");
        if (coins == null) coins = 0L;

        Long streak = doc.getLong("streak_count");
        if (streak == null) streak = 0L;

        // ❌ Already claimed today
        if (today.equals(lastDate)) {
            throw new RuntimeException("Already claimed today");
        }

        // 🔥 Midnight Reset Logic
        // If user missed even 1 day → reset streak to 0
        if (lastClaimDate == null || !lastClaimDate.plusDays(1).equals(todayDate)) {
            streak = 0L;
        }

        // ✅ Today claim → increment
        streak++;

        // Loop after 7 days
        if (streak > 7) streak = 1L;

        int reward = getReward(streak.intValue());

        // 🔄 Update Firestore
        Map<String, Object> updates = new HashMap<>();
        updates.put("coins", coins + reward);
        updates.put("streak_count", streak);

        Map<String, Object> bonus = new HashMap<>();
        bonus.put("claimed_date", today);

        updates.put("daily_bonus", bonus);

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
                    "🔥 Awesome Streak! " + streak,
                    "Come back tomorrow for even more coins 🎉",
                    reward + " 🪙"
            );
        }

        // 📤 Response
        return Map.of(
                "reward", reward,
                "streak", streak
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