package com.example.backend.job;

import com.example.backend.model.DailyNotificationSettings;
import com.example.backend.model.NotificationSendRequest;
import com.example.backend.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class DailyNotificationScheduler {

    @Autowired
    private NotificationService notificationService;

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    // Check every minute on 00 seconds in Asia/Kolkata timezone
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Kolkata")
    public void executeDailyEarningNotificationCheck() {
        DailyNotificationSettings settings = notificationService.getDailyNotificationSettings();

        if (!settings.isEnabled()) {
            return;
        }

        LocalTime nowIst = LocalTime.now(IST_ZONE);
        int currentHour = nowIst.getHour();
        int currentMinute = nowIst.getMinute();

        int targetHour = settings.getHour();
        int targetMinute = settings.getMinute();

        if (currentHour == targetHour && currentMinute == targetMinute) {
            String todayIstDate = LocalDate.now(IST_ZONE).toString();

            if (todayIstDate.equals(settings.getLastSentDate())) {
                return;
            }

            System.out.println("⏰ [DYNAMIC DAILY SCHEDULER] Matched configured IST time " 
                    + String.format("%02d:%02d", targetHour, targetMinute) 
                    + ". Dispatching automated notification...");

            NotificationSendRequest request = new NotificationSendRequest();
            request.setTitle(settings.getTitle() != null && !settings.getTitle().isEmpty() ? settings.getTitle() : "🎁 Your Daily Rewards Are Ready!");
            request.setMessage(settings.getMessage() != null && !settings.getMessage().isEmpty() ? settings.getMessage() : "Claim your daily bonus and start earning today.");
            request.setImageUrl(settings.getImageUrl());
            request.setNotificationType("DAILY_EARNING");
            request.setScreen(settings.getScreen() != null && !settings.getScreen().isEmpty() ? settings.getScreen() : "DAILY_BONUS");
            request.setAudience("ALL_USERS");
            request.setCreatedBy("AUTOMATED_DAILY_SCHEDULER");

            notificationService.sendNotification(request);

            settings.setLastSentDate(todayIstDate);
            notificationService.saveDailyNotificationSettings(settings);

            System.out.println("✅ [DYNAMIC DAILY SCHEDULER] Automated daily notification completed successfully for " + todayIstDate);
        }
    }
}
