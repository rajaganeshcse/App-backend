package com.example.backend.model;

import java.util.List;

public class OfferModel {
    private String offerId;
    private String title;
    private String shortDescription;
    private String description;
    private String category;
    private String logoUrl;
    private String bannerUrl;
    private String destinationUrl;
    private long rewardCoins;
    private String conversionEvent;
    private List<String> howItWorks;
    private List<String> termsAndConditions;
    private int priority;
    private String status; // ACTIVE, INACTIVE
    private Long startDate;
    private Long endDate;
    private Long createdAt;
    private Long updatedAt;

    // Referral Task & Proof Tracking
    private String offerType; // AFFILIATE (default), REFERRAL_TASK
    private String referralCode; // e.g. "gp1234"
    private boolean proofRequired;
    private String proofLabel; // e.g. "Enter your GPay registered mobile number or UPI reference"

    // Funnel attribution metrics
    private long totalInstalls;
    private long totalRegistrations;

    public OfferModel() {}

    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getBannerUrl() { return bannerUrl; }
    public void setBannerUrl(String bannerUrl) { this.bannerUrl = bannerUrl; }

    public String getDestinationUrl() { return destinationUrl; }
    public void setDestinationUrl(String destinationUrl) { this.destinationUrl = destinationUrl; }

    public long getRewardCoins() { return rewardCoins; }
    public void setRewardCoins(long rewardCoins) { this.rewardCoins = rewardCoins; }

    public String getConversionEvent() { return conversionEvent; }
    public void setConversionEvent(String conversionEvent) { this.conversionEvent = conversionEvent; }

    public List<String> getHowItWorks() { return howItWorks; }
    public void setHowItWorks(List<String> howItWorks) { this.howItWorks = howItWorks; }

    public List<String> getTermsAndConditions() { return termsAndConditions; }
    public void setTermsAndConditions(List<String> termsAndConditions) { this.termsAndConditions = termsAndConditions; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getStartDate() { return startDate; }
    public void setStartDate(Long startDate) { this.startDate = startDate; }

    public Long getEndDate() { return endDate; }
    public void setEndDate(Long endDate) { this.endDate = endDate; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }

    public String getOfferType() { return offerType; }
    public void setOfferType(String offerType) { this.offerType = offerType; }

    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }

    public boolean isProofRequired() { return proofRequired; }
    public void setProofRequired(boolean proofRequired) { this.proofRequired = proofRequired; }

    public String getProofLabel() { return proofLabel; }
    public void setProofLabel(String proofLabel) { this.proofLabel = proofLabel; }

    public long getTotalInstalls() { return totalInstalls; }
    public void setTotalInstalls(long totalInstalls) { this.totalInstalls = totalInstalls; }

    public long getTotalRegistrations() { return totalRegistrations; }
    public void setTotalRegistrations(long totalRegistrations) { this.totalRegistrations = totalRegistrations; }
}
