package com.example.backend.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
public class StreakService {

    @Autowired
    private Firestore firestore;

    public Map<String, Object> claimStreak(String uid) throws Exception {

        DocumentReference ref = firestore.collection("users").document(uid);
        DocumentSnapshot doc = ref.get().get();

        if (!doc.exists()) throw new RuntimeException("User not found");

        String today = LocalDate.now().toString();

        // get last claimed date
        Map<String, Object> dailyBonus = (Map<String, Object>) doc.get("daily_bonus");
        String lastDate = dailyBonus != null ? (String) dailyBonus.get("claimed_date") : null;

        Long coins = doc.getLong("coins");
        Long streak = doc.getLong("streak_count");
        if (streak == null) streak = 0L;

        // already claimed
        if (today.equals(lastDate)) {
            throw new RuntimeException("Already claimed today");
        }

        // check yesterday
        if (lastDate != null &&
                LocalDate.parse(lastDate).plusDays(1).equals(LocalDate.now())) {
            streak++;
        } else {
            streak = 1L;
        }

        if (streak > 7) streak = 1L;

        int reward = getReward(streak.intValue());

        Map<String, Object> updates = new HashMap<>();
        updates.put("coins", coins + reward);
        updates.put("streak_count", streak);

        Map<String, Object> bonus = new HashMap<>();
        bonus.put("claimed_date", today);

        updates.put("daily_bonus", bonus);

        ref.update(updates);

        return Map.of(
                "reward", reward,
                "streak", streak
        );
    }

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