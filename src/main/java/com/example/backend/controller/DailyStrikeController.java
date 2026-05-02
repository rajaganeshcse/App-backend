package com.example.backend.controller;


import com.example.backend.service.StreakService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
    @RequestMapping("/api")
    public class DailyStrikeController {

        @Autowired
        private StreakService service;

        @PostMapping("/claim-streak")
        public ResponseEntity<?> claim(
                @RequestHeader("Authorization") String token) throws Exception {

            FirebaseToken decoded =
                    FirebaseAuth.getInstance().verifyIdToken(token);

            String uid = decoded.getUid();

            Map<String, Object> res = service.claimStreak(uid);

            return ResponseEntity.ok(res);
        }
    }

