package com.example.backend.controller;

import com.example.backend.model.LoginRequest;
import com.example.backend.service.NotificationService;
import com.example.backend.service.ReferralUtil;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class AuthController {

    @Autowired
    NotificationService service;

    @Autowired
    ReferralUtil Referral;

    @GetMapping("/send")
    public String send(@RequestParam String token) throws Exception {
        service.send(token, "🎉 Withdraw Success", "₹200 credited", "100");
        return "Sent";
    }

    @PostMapping("/auth")
    public ResponseEntity<?> auth(@RequestBody LoginRequest request) {

        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(request.token);

            String uid = decoded.getUid();
            String name = decoded.getName() != null ? decoded.getName() : "";
            String email = decoded.getEmail() != null ? decoded.getEmail() : "";
            String picture = decoded.getPicture() != null ? decoded.getPicture() : "";

            Firestore db = FirestoreClient.getFirestore();
            DocumentReference ref = db.collection("users").document(uid);

            DocumentSnapshot doc = ref.get().get();

            if (!doc.exists()) {
                // CASE A — NEW USER: Create user document with defaults & server timestamps
                String code = Referral.generateCode();

                Map<String, Object> user = new HashMap<>();

                user.put("uid", uid);
                user.put("name", name);
                user.put("email", email);
                user.put("profile_pic", picture);
                user.put("referralCode", code);

                // 🔐 MAIN BALANCE DEFAULTS
                user.put("coins", 100);
                user.put("tickets", 10);

                // 📊 COUNTERS & STREAK DEFAULTS
                user.put("streak_count", 0);
                user.put("dailySpinCount", 0);
                user.put("dailyScratchCount", 0);
                user.put("daily_ads_count", 0);

                // 📅 DATE DEFAULTS
                user.put("dailyBonusClaimDate", "");
                user.put("dailyScratchDate", "");
                user.put("lastSpinDate", "");

                Map<String, Object> dailyBonusMap = new HashMap<>();
                dailyBonusMap.put("claimed_date", "");
                user.put("daily_bonus", dailyBonusMap);

                user.put("fcmToken", "");

                // ⏰ TIMESTAMPS
                user.put("created_at", FieldValue.serverTimestamp());
                user.put("loginTime", FieldValue.serverTimestamp());

                // ✅ SAVE NEW USER
                ref.set(user);

                // ✅ COIN HISTORY
                Map<String, Object> coinDetail = new HashMap<>();
                coinDetail.put("amount", 100);
                coinDetail.put("type", "welcome_bonus");
                coinDetail.put("status", "Credit");
                coinDetail.put("istype", "coin");
                coinDetail.put("created_at", FieldValue.serverTimestamp());

                ref.collection("coinDetails").add(coinDetail);

                // ✅ TICKET HISTORY
                Map<String, Object> ticketDetail = new HashMap<>();
                ticketDetail.put("amount", 10);
                ticketDetail.put("type", "welcome_bonus");
                ticketDetail.put("status", "Credit");
                ticketDetail.put("istype", "token");
                ticketDetail.put("created_at", FieldValue.serverTimestamp());

                ref.collection("coinDetails").add(ticketDetail);

            } else {
                // CASE B — EXISTING USER: Targeted update for loginTime & profile info (PRESERVES COINS, TICKETS, STREAKS, CREATED_AT)
                Map<String, Object> updates = new HashMap<>();
                updates.put("loginTime", FieldValue.serverTimestamp());

                if (!name.isEmpty()) updates.put("name", name);
                if (!email.isEmpty()) updates.put("email", email);
                if (!picture.isEmpty()) updates.put("profile_pic", picture);

                ref.update(updates);
            }

            return ResponseEntity.ok("Success");

        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid Token");
        }
    }
}