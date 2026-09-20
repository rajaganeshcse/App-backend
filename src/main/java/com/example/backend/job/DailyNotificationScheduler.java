package com.example.backend.job;

import com.example.backend.model.DailyNotificationSettings;
import com.example.backend.model.NotificationSendRequest;
import com.example.backend.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class DailyNotificationScheduler {

    @Autowired
    private NotificationService notificationService;

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Kolkata")
    public void executeDailyEarningNotification() {
        System.out.println("⏰ [DAILY SCHEDULER] Running 6:00 AM IST Daily Earning Notification Check...");

        DailyNotificationSettings settings = notificationService.getDailyNotificationSettings();

        if (!settings.isEnabled()) {
            System.out.println("⏸️ [DAILY SCHEDULER] Daily notifications are currently DISABLED in settings. Skipping.");
            return;
        }

        String todayIstDate = LocalDate.now(IST_ZONE).toString();

        if (todayIstDate.equals(settings.getLastSentDate())) {
            System.out.println("🛡️ [DAILY SCHEDULER] Daily notification already sent for date " + todayIstDate + ". Preventing duplicate execution.");
            return;
        }

        NotificationSendRequest request = new NotificationSendRequest();
        request.setTitle(settings.getTitle() != null && !settings.getTitle().isEmpty() ? settings.getTitle() : "🎁 Your Daily Rewards Are Ready!");
        request.setMessage(settings.getMessage() != null && !settings.getMessage().isEmpty() ? settings.getMessage() : "Claim your daily bonus and start earning today.");
        request.setImageUrl(settings.getImageUrl());
        request.setNotificationType("DAILY_EARNING");
        request.setScreen(settings.getScreen() != null && !settings.getScreen().isEmpty() ? settings.getScreen() : "DAILY_BONUS");
        request.setAudience("ALL_USERS");
        request.setCreatedBy("SYSTEM_SCHEDULER");

        System.out.println("🚀 [DAILY SCHEDULER] Dispatching daily notification to all users...");
        notificationService.sendNotification(request);

        settings.setLastSentDate(todayIstDate);
        notificationService.saveDailyNotificationSettings(settings);

        System.out.println("✅ [DAILY SCHEDULER] Daily 6:00 AM IST notification completed successfully for " + todayIstDate);
    }
}
