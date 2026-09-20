package com.example.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRecord {
    private String notificationId;
    private String title;
    private String message;
    private String imageUrl;
    private String notificationType;
    private String screen;
    private String audience;
    private String createdAt;
    private String createdBy;
    private String sentAt;
    private int totalRecipients;
    private int successfulCount;
    private int failedCount;
    private String status;
}
