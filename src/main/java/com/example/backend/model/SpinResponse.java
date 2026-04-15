package com.example.backend.model;

public class SpinResponse {
    public int reward;
    public int remainingSpins;

    public SpinResponse(int reward, int remainingSpins) {
        this.reward = reward;
        this.remainingSpins = remainingSpins;
    }
}