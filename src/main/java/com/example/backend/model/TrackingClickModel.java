package com.example.backend.model;

import java.util.Map;

public class TrackingClickModel {
    private String clickId;
    private String userId;
    private String offerId;
    private String sessionId;
    private Long createdAt;
    private Long clickedAt;
    private Long redirectedAt;
    private String status; // CLICKED, REDIRECTED, CONVERTED, EXPIRED, INVALID
    private String conversionId;
    private Long expiresAt;
    private Map<String, String> metadata;
    private boolean isNewUser = true;
    private int clickCount = 1;
    private String ipAddress;
    private String deviceInfo;
    private Long lastClickedAt;

    public TrackingClickModel() {}

    public String getClickId() { return clickId; }
    public void setClickId(String clickId) { this.clickId = clickId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public Long getClickedAt() { return clickedAt; }
    public void setClickedAt(Long clickedAt) { this.clickedAt = clickedAt; }

    public Long getRedirectedAt() { return redirectedAt; }
    public void setRedirectedAt(Long redirectedAt) { this.redirectedAt = redirectedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getConversionId() { return conversionId; }
    public void setConversionId(String conversionId) { this.conversionId = conversionId; }

    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    public boolean isNewUser() { return isNewUser; }
    public void setNewUser(boolean newUser) { isNewUser = newUser; }
    public void setIsNewUser(boolean newUser) { isNewUser = newUser; }

    public int getClickCount() { return clickCount; }
    public void setClickCount(int clickCount) { this.clickCount = clickCount; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getDeviceInfo() { return deviceInfo; }
    public void setDeviceInfo(String deviceInfo) { this.deviceInfo = deviceInfo; }

    public Long getLastClickedAt() { return lastClickedAt; }
    public void setLastClickedAt(Long lastClickedAt) { this.lastClickedAt = lastClickedAt; }
}
