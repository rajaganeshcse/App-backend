package com.example.backend.service;

import com.example.backend.model.DailyNotificationSettings;
import com.example.backend.model.NotificationRecord;
import com.example.backend.model.NotificationSendRequest;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.firebase.messaging.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class NotificationService {

    @Autowired
    private Firestore firestore;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    public void send(String token, String title, String body, String amount) throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("title", title != null ? title : "");
        data.put("body", body != null ? body : "");
        data.put("amount", amount != null ? amount : "0");
        data.put("type", "withdraw");
        data.put("requestId", "REQ001");

        Message message = Message.builder()
                .setToken(token)
                .putAllData(data)
                .build();

        FirebaseMessaging.getInstance().send(message);
    }

    public NotificationRecord sendNotification(NotificationSendRequest request) {
        String notificationId = UUID.randomUUID().toString();
        String nowIso = ISO_FORMATTER.format(Instant.now());

        NotificationRecord record = new NotificationRecord();
        record.setNotificationId(notificationId);
        record.setTitle(request.getTitle());
        record.setMessage(request.getMessage());
        record.setImageUrl(request.getImageUrl() != null ? request.getImageUrl() : "");
        record.setNotificationType(request.getNotificationType() != null ? request.getNotificationType() : "PROMOTION");
        record.setScreen(request.getScreen() != null ? request.getScreen() : "HOME");
        record.setAudience(request.getAudience() != null ? request.getAudience() : "ALL_USERS");
        record.setCreatedAt(nowIso);
        record.setCreatedBy(request.getCreatedBy() != null ? request.getCreatedBy() : "admin");
        record.setStatus("PENDING");

        Map<String, String> userTokenMap = resolveTargetTokens(request);
        List<String> tokens = new ArrayList<>(userTokenMap.keySet());

        if (tokens.isEmpty()) {
            record.setTotalRecipients(0);
            record.setSuccessfulCount(0);
            record.setFailedCount(0);
            record.setSentAt(nowIso);
            record.setStatus("SENT");
            saveNotificationHistory(record);
            return record;
        }

        Map<String, String> dataPayload = new HashMap<>();
        dataPayload.put("notificationId", notificationId);
        dataPayload.put("title", request.getTitle() != null ? request.getTitle() : "");
        dataPayload.put("message", request.getMessage() != null ? request.getMessage() : "");
        dataPayload.put("body", request.getMessage() != null ? request.getMessage() : "");
        dataPayload.put("imageUrl", request.getImageUrl() != null ? request.getImageUrl() : "");
        dataPayload.put("notificationType", record.getNotificationType());
        dataPayload.put("screen", record.getScreen());
        dataPayload.put("deepLink", request.getDeepLink() != null ? request.getDeepLink() : "");
        if (request.getCustomData() != null) {
            dataPayload.putAll(request.getCustomData());
        }

        int successCount = 0;
        int failureCount = 0;

        int batchSize = 500;
        for (int i = 0; i < tokens.size(); i += batchSize) {
            List<String> batchTokens = tokens.subList(i, Math.min(i + batchSize, tokens.size()));

            MulticastMessage multicastMessage = MulticastMessage.builder()
                    .addAllTokens(batchTokens)
                    .putAllData(dataPayload)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setTitle(request.getTitle())
                                    .setBody(request.getMessage())
                                    .setChannelId("earning_notifications")
                                    .build())
                            .build())
                    .build();

            try {
                BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(multicastMessage);
                successCount += response.getSuccessCount();
                failureCount += response.getFailureCount();

                List<SendResponse> responses = response.getResponses();
                for (int j = 0; j < responses.size(); j++) {
                    SendResponse sr = responses.get(j);
                    if (!sr.isSuccessful()) {
                        FirebaseMessagingException exception = sr.getException();
                        if (exception != null) {
                            MessagingErrorCode errorCode = exception.getMessagingErrorCode();
                            if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                                String failedToken = batchTokens.get(j);
                                String failedUserId = userTokenMap.get(failedToken);
                                removeInvalidToken(failedUserId, failedToken);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ FCM Multicast dispatch error: " + e.getMessage());
                failureCount += batchTokens.size();
            }
        }

        record.setTotalRecipients(tokens.size());
        record.setSuccessfulCount(successCount);
        record.setFailedCount(failureCount);
        record.setSentAt(nowIso);
        record.setStatus("SENT");

        saveNotificationHistory(record);
        return record;
    }

    private Map<String, String> resolveTargetTokens(NotificationSendRequest request) {
        Map<String, String> tokenUserMap = new HashMap<>();
        String audience = request.getAudience() != null ? request.getAudience() : "ALL_USERS";

        try {
            if ("SPECIFIC_USER".equalsIgnoreCase(audience) && request.getTargetUserId() != null && !request.getTargetUserId().isEmpty()) {
                DocumentSnapshot userDoc = firestore.collection("users").document(request.getTargetUserId()).get().get();
                if (userDoc.exists()) {
                    String token = userDoc.getString("fcmToken");
                    Boolean enabled = userDoc.getBoolean("notificationEnabled");
                    if (token != null && !token.trim().isEmpty() && (enabled == null || enabled)) {
                        tokenUserMap.put(token, userDoc.getId());
                    }
                }
            } else {
                ApiFuture<QuerySnapshot> future = firestore.collection("users").get();
                List<QueryDocumentSnapshot> docs = future.get().getDocuments();

                for (DocumentSnapshot doc : docs) {
                    String token = doc.getString("fcmToken");
                    Boolean enabled = doc.getBoolean("notificationEnabled");
                    if (token != null && !token.trim().isEmpty() && (enabled == null || enabled)) {
                        tokenUserMap.put(token, doc.getId());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error querying target tokens from Firestore: " + e.getMessage());
        }

        return tokenUserMap;
    }

    private void removeInvalidToken(String userId, String token) {
        if (userId != null && !userId.isEmpty()) {
            try {
                Map<String, Object> updates = new HashMap<>();
                updates.put("fcmToken", FieldValue.delete());
                firestore.collection("users").document(userId).update(updates);
                System.out.println("⚠️ Cleared invalid FCM token for user: " + userId);
            } catch (Exception e) {
                System.err.println("❌ Error removing invalid FCM token for user " + userId + ": " + e.getMessage());
            }
        }
    }

    public void saveNotificationHistory(NotificationRecord record) {
        try {
            firestore.collection("notifications")
                    .document(record.getNotificationId())
                    .set(record, SetOptions.merge());
        } catch (Exception e) {
            System.err.println("❌ Error saving notification history: " + e.getMessage());
        }
    }

    public List<NotificationRecord> getNotificationHistory() {
        List<NotificationRecord> list = new ArrayList<>();
        try {
            ApiFuture<QuerySnapshot> future = firestore.collection("notifications").get();
            List<QueryDocumentSnapshot> docs = future.get().getDocuments();

            for (DocumentSnapshot doc : docs) {
                NotificationRecord record = doc.toObject(NotificationRecord.class);
                if (record != null) {
                    list.add(record);
                }
            }
            list.sort((a, b) -> {
                String t1 = a.getSentAt() != null ? a.getSentAt() : (a.getCreatedAt() != null ? a.getCreatedAt() : "");
                String t2 = b.getSentAt() != null ? b.getSentAt() : (b.getCreatedAt() != null ? b.getCreatedAt() : "");
                return t2.compareTo(t1);
            });
        } catch (Exception e) {
            System.err.println("❌ Error fetching notification history: " + e.getMessage());
        }
        return list;
    }

    public DailyNotificationSettings getDailyNotificationSettings() {
        try {
            DocumentSnapshot doc = firestore.collection("notification_settings")
                    .document("daily_earning")
                    .get()
                    .get();

            if (doc.exists()) {
                DailyNotificationSettings settings = doc.toObject(DailyNotificationSettings.class);
                if (settings != null) return settings;
            }
        } catch (Exception e) {
            System.err.println("❌ Error reading daily notification settings: " + e.getMessage());
        }
        return new DailyNotificationSettings();
    }

    public DailyNotificationSettings saveDailyNotificationSettings(DailyNotificationSettings settings) {
        settings.setUpdatedAt(ISO_FORMATTER.format(Instant.now()));
        try {
            firestore.collection("notification_settings")
                    .document("daily_earning")
                    .set(settings, SetOptions.merge());
        } catch (Exception e) {
            System.err.println("❌ Error saving daily notification settings: " + e.getMessage());
        }
        return settings;
    }
}