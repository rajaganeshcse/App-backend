package com.example.backend.controller;

import com.example.backend.model.DailyNotificationSettings;
import com.example.backend.model.NotificationRecord;
import com.example.backend.model.NotificationSendRequest;
import com.example.backend.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/admin/notifications")
@CrossOrigin(origins = "*")
public class NotificationAdminController {

    @Autowired
    private NotificationService notificationService;

    @PostMapping("/send")
    public ResponseEntity<?> sendNotification(@RequestBody NotificationSendRequest request) {
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Notification title is required"));
        }
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Notification message is required"));
        }

        if (NotificationService.isTokenInContent(request.getTitle()) || NotificationService.isTokenInContent(request.getMessage())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Corrupted notification rejected: FCM token detected in title or message"));
        }

        try {
            NotificationRecord record = notificationService.sendNotification(request);
            return ResponseEntity.ok(record);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to dispatch notification: " + e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<NotificationRecord>> getNotificationHistory() {
        List<NotificationRecord> history = notificationService.getNotificationHistory();
        return ResponseEntity.ok(history);
    }

    @GetMapping("/settings/daily")
    public ResponseEntity<DailyNotificationSettings> getDailySettings() {
        DailyNotificationSettings settings = notificationService.getDailyNotificationSettings();
        return ResponseEntity.ok(settings);
    }

    @PostMapping("/settings/daily")
    public ResponseEntity<DailyNotificationSettings> updateDailySettings(@RequestBody DailyNotificationSettings settings) {
        DailyNotificationSettings saved = notificationService.saveDailyNotificationSettings(settings);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/upload-image")
    public ResponseEntity<?> uploadNotificationImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Uploaded image file is empty"));
        }

        long maxSize = 5 * 1024 * 1024; // 5MB
        if (file.getSize() > maxSize) {
            return ResponseEntity.badRequest().body(Map.of("error", "Image file exceeds maximum allowed size of 5MB"));
        }

        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        boolean isValidType = (contentType != null && (contentType.contains("jpeg") || contentType.contains("jpg") || contentType.contains("png") || contentType.contains("webp")))
                || originalFilename.endsWith(".jpg") || originalFilename.endsWith(".jpeg") || originalFilename.endsWith(".png") || originalFilename.endsWith(".webp");

        if (!isValidType) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid file type. Only JPG, JPEG, PNG, and WEBP images are allowed."));
        }

        try {
            String extension = ".png";
            if (originalFilename.endsWith(".jpg") || originalFilename.endsWith(".jpeg")) extension = ".jpg";
            else if (originalFilename.endsWith(".webp")) extension = ".webp";

            String filename = "notification_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
            String uploadsDir = Paths.get(System.getProperty("user.dir"), "uploads", "notifications").toString();

            File dir = new File(uploadsDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File destFile = new File(dir, filename);
            try (InputStream is = file.getInputStream(); OutputStream os = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }

            String imageUrl = "/uploads/notifications/" + filename;
            return ResponseEntity.ok(Map.of("imageUrl", imageUrl, "filename", filename));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to upload image: " + e.getMessage()));
        }
    }
}
