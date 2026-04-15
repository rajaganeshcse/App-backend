package com.example.backend.model;

public class SpinResponse {

    private int reward;

    public SpinResponse(int reward) {
        this.reward = reward;
    }

    public int getReward() {
        return reward;
    }

    public void setReward(int reward) {
        this.reward = reward;
    }
}