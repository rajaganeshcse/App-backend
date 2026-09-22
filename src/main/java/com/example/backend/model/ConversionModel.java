package com.example.backend.model;

import java.util.Map;

public class ConversionModel {
    private String conversionId;
    private String clickId;
    private String userId;
    private String offerId;
    private String event;
    private String externalReference;
    private String status; // PENDING, VALIDATING, APPROVED, REJECTED, REVERSED
    private long rewardCoins;
    private Long createdAt;
    private Long validatedAt;
    private Long approvedAt;
    private Long rejectedAt;
    private Long reversedAt;
    private String rejectionReason;
    private Map<String, String> metadata;

    public ConversionModel() {}

    public String getConversionId() { return conversionId; }
    public void setConversionId(String conversionId) { this.conversionId = conversionId; }

    public String getClickId() { return clickId; }
    public void setClickId(String clickId) { this.clickId = clickId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }

    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getRewardCoins() { return rewardCoins; }
    public void setRewardCoins(long rewardCoins) { this.rewardCoins = rewardCoins; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public Long getValidatedAt() { return validatedAt; }
    public void setValidatedAt(Long validatedAt) { this.validatedAt = validatedAt; }

    public Long getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Long approvedAt) { this.approvedAt = approvedAt; }

    public Long getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(Long rejectedAt) { this.rejectedAt = rejectedAt; }

    public Long getReversedAt() { return reversedAt; }
    public void setReversedAt(Long reversedAt) { this.reversedAt = reversedAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}
