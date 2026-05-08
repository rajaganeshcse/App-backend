package com.example.backend.Firebase;

import com.google.cloud.Timestamp;
import java.util.Map;

public class Firebase_user {

    private String uid;
    private String name;
    private String email;

    private long coins;
    private long tickets;

    private long streak_count;
    private long dailySpinCount;
    private long daily_ads_count;

    private String referralCode;
    private String lastSpinDate;

    private String fcmToken;

    private Timestamp created_at;

    private Map<String, Object> daily_bonus;
    private Map<String, Object> game_ids;

    // Empty constructor required for Firestore
    public Firebase_user() {
    }

    // Constructor with only UID
    public Firebase_user(String uid) {
        this.uid = uid;
    }

    // ================= GETTERS AND SETTERS =================

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public long getCoins() {
        return coins;
    }

    public void setCoins(long coins) {
        this.coins = coins;
    }

    public long getTickets() {
        return tickets;
    }

    public void setTickets(long tickets) {
        this.tickets = tickets;
    }

    public long getStreak_count() {
        return streak_count;
    }

    public void setStreak_count(long streak_count) {
        this.streak_count = streak_count;
    }

    public long getDailySpinCount() {
        return dailySpinCount;
    }

    public void setDailySpinCount(long dailySpinCount) {
        this.dailySpinCount = dailySpinCount;
    }

    public long getDaily_ads_count() {
        return daily_ads_count;
    }

    public void setDaily_ads_count(long daily_ads_count) {
        this.daily_ads_count = daily_ads_count;
    }

    public String getReferralCode() {
        return referralCode;
    }

    public void setReferralCode(String referralCode) {
        this.referralCode = referralCode;
    }

    public String getLastSpinDate() {
        return lastSpinDate;
    }

    public void setLastSpinDate(String lastSpinDate) {
        this.lastSpinDate = lastSpinDate;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public Timestamp getCreated_at() {
        return created_at;
    }

    public void setCreated_at(Timestamp created_at) {
        this.created_at = created_at;
    }

    public Map<String, Object> getDaily_bonus() {
        return daily_bonus;
    }

    public void setDaily_bonus(Map<String, Object> daily_bonus) {
        this.daily_bonus = daily_bonus;
    }

    public Map<String, Object> getGame_ids() {
        return game_ids;
    }

    public void setGame_ids(Map<String, Object> game_ids) {
        this.game_ids = game_ids;
    }
}