package com.example.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyNotificationSettings {
    private boolean enabled = true;
    private int hour = 6;
    private int minute = 0;
    private String timezone = "Asia/Kolkata";
    private String title = "🎁 Your Daily Rewards Are Ready!";
    private String message = "Claim your daily bonus, complete tasks, and start earning today.";
    private String imageUrl = "";
    private String screen = "DAILY_BONUS";
    private String updatedAt;
    private String lastSentDate;
}
