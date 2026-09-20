package com.example.backend.service;

import com.example.backend.model.DailyNotificationSettings;
import com.example.backend.model.NotificationRecord;
import com.example.backend.model.NotificationSendRequest;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.messaging.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class NotificationService {

    @Autowired
    private Firestore firestore;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    @PostConstruct
    public void initFirestoreNotificationListener() {
        System.out.println("🔥 Listening to Firestore 'notifications' collection for pending FCM dispatches...");
        try {
            firestore.collection("notifications")
                    .addSnapshotListener((snapshots, e) -> {
                        if (e != null) {
                            System.err.println("❌ Firestore notification listener error: " + e.getMessage());
                            return;
                        }
                        if (snapshots != null && !snapshots.isEmpty()) {
                            for (DocumentChange dc : snapshots.getDocumentChanges()) {
                                if (dc.getType() == DocumentChange.Type.ADDED || dc.getType() == DocumentChange.Type.MODIFIED) {
                                    DocumentSnapshot doc = dc.getDocument();
                                    String status = doc.getString("status");
                                    if ("PENDING".equalsIgnoreCase(status)) {
                                        NotificationSendRequest req = new NotificationSendRequest();
                                        req.setTitle(doc.getString("title"));
                                        req.setMessage(doc.getString("message"));
                                        req.setImageUrl(doc.getString("imageUrl"));
                                        req.setNotificationType(doc.getString("notificationType"));
                                        req.setScreen(doc.getString("screen"));
                                        req.setAudience(doc.getString("audience"));
                                        req.setTargetUserId(doc.getString("targetUserId"));
                                        req.setCreatedBy(doc.getString("createdBy"));

                                        String notifId = doc.getId();
                                        System.out.println("📬 Dispatching pending notification from Firestore: " + notifId);
                                        sendNotificationWithId(notifId, req);
                                    }
                                }
                            }
                        }
                    });
        } catch (Exception e) {
            System.err.println("⚠️ Could not initialize Firestore snapshot listener: " + e.getMessage());
        }
    }

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
        String notificationId = "notif_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6);
        return sendNotificationWithId(notificationId, request);
    }

    public NotificationRecord sendNotificationWithId(String notificationId, NotificationSendRequest request) {
        String nowIso = ISO_FORMATTER.format(Instant.now());

        NotificationRecord record = new NotificationRecord();
        record.setNotificationId(notificationId);
        record.setTitle(request.getTitle() != null ? request.getTitle() : "Rewards Planet 🌟");
        record.setMessage(request.getMessage() != null ? request.getMessage() : "Claim your daily rewards now!");
        record.setImageUrl(request.getImageUrl() != null ? request.getImageUrl() : "");
        record.setNotificationType(request.getNotificationType() != null ? request.getNotificationType() : "PROMOTION");
        record.setScreen(request.getScreen() != null ? request.getScreen() : "HOME");
        record.setAudience(request.getAudience() != null ? request.getAudience() : "ALL_USERS");
        record.setCreatedAt(nowIso);
        record.setCreatedBy(request.getCreatedBy() != null ? request.getCreatedBy() : "admin");
        record.setStatus("SENT");

        Map<String, String> userTokenMap = resolveTargetTokens(request);
        List<String> tokens = new ArrayList<>(userTokenMap.keySet());

        System.out.println("📱 Resolved " + tokens.size() + " FCM target tokens for audience: " + record.getAudience());

        if (tokens.isEmpty()) {
            record.setTotalRecipients(0);
            record.setSuccessfulCount(0);
            record.setFailedCount(0);
            record.setSentAt(nowIso);
            saveNotificationHistory(record);
            return record;
        }

        Map<String, String> dataPayload = new HashMap<>();
        dataPayload.put("notificationId", notificationId);
        dataPayload.put("title", record.getTitle());
        dataPayload.put("message", record.getMessage());
        dataPayload.put("body", record.getMessage());
        dataPayload.put("imageUrl", record.getImageUrl());
        dataPayload.put("notificationType", record.getNotificationType());
        dataPayload.put("screen", record.getScreen());
        dataPayload.put("deepLink", request.getDeepLink() != null ? request.getDeepLink() : "");
        if (request.getCustomData() != null) {
            dataPayload.putAll(request.getCustomData());
        }

        int successCount = 0;
        int failureCount = 0;

        String img = record.getImageUrl();
        boolean isValidHttpUrl = img != null && (img.startsWith("http://") || img.startsWith("https://")) && !img.startsWith("blob:");

        int batchSize = 500;
        for (int i = 0; i < tokens.size(); i += batchSize) {
            List<String> batchTokens = tokens.subList(i, Math.min(i + batchSize, tokens.size()));

            com.google.firebase.messaging.Notification.Builder notifBuilder = com.google.firebase.messaging.Notification.builder()
                    .setTitle(record.getTitle())
                    .setBody(record.getMessage());
            if (isValidHttpUrl) {
                notifBuilder.setImage(img);
            }

            AndroidNotification.Builder androidNotifBuilder = AndroidNotification.builder()
                    .setTitle(record.getTitle())
                    .setBody(record.getMessage())
                    .setChannelId("earning_notifications")
                    .setSound("default");
            if (isValidHttpUrl) {
                androidNotifBuilder.setImage(img);
            }

            MulticastMessage.Builder builder = MulticastMessage.builder()
                    .addAllTokens(batchTokens)
                    .putAllData(dataPayload)
                    .setNotification(notifBuilder.build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(androidNotifBuilder.build())
                            .build());

            try {
                BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(builder.build());
                successCount += response.getSuccessCount();
                failureCount += response.getFailureCount();

                System.out.println("✅ FCM Multicast Batch result: " + response.getSuccessCount() + " success, " + response.getFailureCount() + " failures");

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
                    String token = extractToken(userDoc);
                    Boolean enabled = userDoc.getBoolean("notificationEnabled");
                    if (token != null && !token.trim().isEmpty() && (enabled == null || enabled)) {
                        tokenUserMap.put(token, userDoc.getId());
                    }
                }
            } else {
                ApiFuture<QuerySnapshot> future = firestore.collection("users").get();
                List<QueryDocumentSnapshot> docs = future.get().getDocuments();

                for (DocumentSnapshot doc : docs) {
                    String token = extractToken(doc);
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

    private String extractToken(DocumentSnapshot doc) {
        if (doc == null) return null;
        String token = doc.getString("fcmToken");
        if (token == null || token.trim().isEmpty()) token = doc.getString("token");
        if (token == null || token.trim().isEmpty()) token = doc.getString("fcm_token");
        if (token == null || token.trim().isEmpty()) token = doc.getString("deviceToken");
        return token;
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