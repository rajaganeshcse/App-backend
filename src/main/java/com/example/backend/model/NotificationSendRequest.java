package com.example.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSendRequest {
    private String title;
    private String message;
    private String imageUrl;
    private String notificationType;
    private String screen;
    private String audience;
    private String targetUserId;
    private String deepLink;
    private Map<String, String> customData;
    private String createdBy;
}
