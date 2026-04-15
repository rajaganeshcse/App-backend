package com.example.backend.controller;

import com.example.backend.model.SpinResponse;
import com.example.backend.service.SpinService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SpinController {

    @Autowired
    private SpinService spinService;

    @PostMapping("/spin")
    public ResponseEntity<?> spin(
            @RequestHeader("Authorization") String token,
            @RequestParam String userId
    ) {

        // 🔐 TODO: Verify Firebase token

        if (!spinService.canSpin(userId)) {
            return ResponseEntity.badRequest().body("Daily limit reached");
        }

        int reward = spinService.generateReward();

        // 🔥 UPDATE FIREBASE HERE
        spinService.addCoinsToFirebase(userId, reward);

        spinService.increaseSpin(userId);

        return ResponseEntity.ok(new SpinResponse(reward));
    }
}