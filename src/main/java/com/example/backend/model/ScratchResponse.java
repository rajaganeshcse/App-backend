package com.example.backend.model;

public class ScratchResponse {

    private boolean allowed;

    private int reward;

    private int coins;

    private int remaining;

    private String message;

    public ScratchResponse() {
    }

    public ScratchResponse(
            boolean allowed,
            int reward,
            int coins,
            int remaining,
            String message
    ) {
        this.allowed = allowed;
        this.reward = reward;
        this.coins = coins;
        this.remaining = remaining;
        this.message = message;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(
            boolean allowed
    ) {
        this.allowed = allowed;
    }

    public int getReward() {
        return reward;
    }

    public void setReward(
            int reward
    ) {
        this.reward = reward;
    }

    public int getCoins() {
        return coins;
    }

    public void setCoins(
            int coins
    ) {
        this.coins = coins;
    }

    public int getRemaining() {
        return remaining;
    }

    public void setRemaining(
            int remaining
    ) {
        this.remaining = remaining;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(
            String message
    ) {
        this.message = message;
    }
}