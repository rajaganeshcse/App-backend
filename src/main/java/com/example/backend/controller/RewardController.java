package com.example.backend.controller;

import com.example.backend.model.RewardRequest;
import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RewardController {

    @PostMapping("/reward-ad")
    public ResponseEntity<?> reward(
            @RequestHeader("Authorization") String token,
            @RequestBody RewardRequest req) {

        try {

            // 🔥 FIX TOKEN
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            FirebaseToken decoded =
                    FirebaseAuth.getInstance().verifyIdToken(token);

            String uid = decoded.getUid();

            Firestore db = FirestoreClient.getFirestore();
            DocumentReference userRef =
                    db.collection("users").document(uid);

            DocumentSnapshot doc = userRef.get().get();

            if (!doc.exists()) {
                return ResponseEntity.badRequest().body("User not found");
            }

            if (req.requestId == null || req.requestId.isEmpty()) {
                return ResponseEntity.badRequest().body("Invalid requestId");
            }

            Long coins = doc.getLong("coins");
            Long tickets = doc.getLong("tickets");
            Long ads = doc.getLong("daily_ads_count");

            if (coins == null) coins = 0L;
            if (tickets == null) tickets = 0L;
            if (ads == null) ads = 0L;

            // 🚫 LIMIT
            if (ads >= 10) {
                return ResponseEntity.badRequest().body("Daily limit reached");
            }

            // 🚫 DUPLICATE
            Query query = db.collection("transactions")
                    .whereEqualTo("requestId", req.requestId);

            if (!query.get().get().isEmpty()) {
                return ResponseEntity.badRequest().body("Duplicate request");
            }

            long currentAdIndex = ads + 1;
            int coinReward;
            int ticketReward;

            if (currentAdIndex <= 3) {
                coinReward = 10;
                ticketReward = 1;
            } else if (currentAdIndex <= 5) {
                coinReward = 15;
                ticketReward = 1;
            } else if (currentAdIndex <= 7) {
                coinReward = 20;
                ticketReward = 1;
            } else if (currentAdIndex <= 9) {
                coinReward = 25;
                ticketReward = 1;
            } else {
                coinReward = 50;
                ticketReward = 2;
            }

            // ✅ UPDATE
            userRef.update(
                    "coins", FieldValue.increment(coinReward),
                    "tickets", FieldValue.increment(ticketReward),
                    "daily_ads_count", FieldValue.increment(1)
            );

            // ✅ HISTORY
            Map<String, Object> coinDetail = new HashMap<>();
            coinDetail.put("amount", coinReward);
            coinDetail.put("type", "ads");
            coinDetail.put("status", "Credit");
            coinDetail.put("istype","coin");
            coinDetail.put("created_at", FieldValue.serverTimestamp());
            userRef.collection("coinDetails").add(coinDetail);

            Map<String, Object> ticketDetail = new HashMap<>();
            ticketDetail.put("amount", ticketReward);
            ticketDetail.put("type", "ads");
            ticketDetail.put("status", "Credit");
            ticketDetail.put("istype","token");
            ticketDetail.put("created_at", FieldValue.serverTimestamp());
            userRef.collection("coinDetails").add(ticketDetail);

            // ✅ TRANSACTION
            Map<String, Object> txn = new HashMap<>();
            txn.put("uid", uid);
            txn.put("coins", coinReward);
            txn.put("tickets", ticketReward);
            txn.put("type", "ads");
            txn.put("requestId", req.requestId);
            txn.put("time", FieldValue.serverTimestamp());

            db.collection("transactions").add(txn);

            Map<String, Object> resMap = new HashMap<>();
            resMap.put("success", true);
            resMap.put("message", "Reward added successfully!");
            resMap.put("coinReward", coinReward);
            resMap.put("ticketReward", ticketReward);
            resMap.put("totalCoins", coins + coinReward);
            resMap.put("totalTickets", tickets + ticketReward);

            return ResponseEntity.ok(resMap);

        } catch (Exception e) {
            e.printStackTrace(); // 🔥 VERY IMPORTANT
            return ResponseEntity.status(500).body("Server error: " + e.getMessage());
        }
    }

    @GetMapping("/hitz-rewards/config")
    public ResponseEntity<?> getHitzRewardsConfig() {
        try {
            Firestore db = FirestoreClient.getFirestore();
            DocumentSnapshot doc = db.collection("settings").document("hitz_rewards").get().get();

            if (doc.exists() && doc.get("payouts") != null) {
                return ResponseEntity.ok(doc.getData());
            }

            Map<String, Object> config = new HashMap<>();
            config.put("payouts", java.util.Arrays.asList(10, 25, 25, 25, 50));
            config.put("titles", java.util.Arrays.asList(
                    "Mega Hitz Offer #1",
                    "Super Video Task #2",
                    "Ultra Hitz Offer #3",
                    "Premium Ad Task #4",
                    "Jackpot Hitz Task #5"
            ));
            config.put("duration", "30s Long Ad");

            // Seed Firestore document if missing
            db.collection("settings").document("hitz_rewards").set(config);

            return ResponseEntity.ok(config);
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("payouts", java.util.Arrays.asList(10, 25, 25, 25, 50));
            fallback.put("titles", java.util.Arrays.asList(
                    "Mega Hitz Offer #1",
                    "Super Video Task #2",
                    "Ultra Hitz Offer #3",
                    "Premium Ad Task #4",
                    "Jackpot Hitz Task #5"
            ));
            fallback.put("duration", "30s Long Ad");
            return ResponseEntity.ok(fallback);
        }
    }

    @PostMapping("/hitz-rewards/claim")
    public ResponseEntity<?> claimHitzReward(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> req) {

        try {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token);
            String uid = decoded.getUid();

            String requestId = (String) req.get("requestId");
            Number taskIdNum = (Number) req.get("taskId");
            int taskId = taskIdNum != null ? taskIdNum.intValue() : 1;

            if (requestId == null || requestId.isEmpty()) {
                return ResponseEntity.badRequest().body("Invalid requestId");
            }

            Firestore db = FirestoreClient.getFirestore();
            DocumentReference userRef = db.collection("users").document(uid);
            DocumentSnapshot userDoc = userRef.get().get();

            if (!userDoc.exists()) {
                return ResponseEntity.badRequest().body("User not found");
            }

            // Check duplicate request to prevent replay attacks
            Query query = db.collection("transactions").whereEqualTo("requestId", requestId);
            if (!query.get().get().isEmpty()) {
                return ResponseEntity.badRequest().body("Duplicate request");
            }

            // Fetch dynamic hitz rewards configuration from Firestore settings/hitz_rewards
            DocumentSnapshot configDoc = db.collection("settings").document("hitz_rewards").get().get();
            List<Long> payouts = null;
            if (configDoc.exists() && configDoc.get("payouts") != null) {
                payouts = (List<Long>) configDoc.get("payouts");
            }

            int[] defaultPayouts = {10, 25, 25, 25, 50};
            int coinReward;
            if (payouts != null && taskId >= 1 && taskId <= payouts.size()) {
                coinReward = payouts.get(taskId - 1).intValue();
            } else if (taskId >= 1 && taskId <= defaultPayouts.length) {
                coinReward = defaultPayouts[taskId - 1];
            } else {
                coinReward = 10;
            }

            int ticketReward = 5;

            Long currentCoins = userDoc.getLong("coins");
            Long currentTickets = userDoc.getLong("tickets");
            if (currentCoins == null) currentCoins = 0L;
            if (currentTickets == null) currentTickets = 0L;

            long updatedCoins = currentCoins + coinReward;
            long updatedTickets = currentTickets + ticketReward;

            // Atomically update user balance in Firestore
            userRef.update(
                    "coins", FieldValue.increment(coinReward),
                    "tickets", FieldValue.increment(ticketReward)
            );

            // Record coin history
            Map<String, Object> coinDetail = new HashMap<>();
            coinDetail.put("amount", coinReward);
            coinDetail.put("type", "hitz_reward");
            coinDetail.put("status", "Credit");
            coinDetail.put("istype", "coin");
            coinDetail.put("created_at", FieldValue.serverTimestamp());
            userRef.collection("coinDetails").add(coinDetail);

            // Record ticket history
            Map<String, Object> ticketDetail = new HashMap<>();
            ticketDetail.put("amount", ticketReward);
            ticketDetail.put("type", "hitz_reward");
            ticketDetail.put("status", "Credit");
            ticketDetail.put("istype", "token");
            ticketDetail.put("created_at", FieldValue.serverTimestamp());
            userRef.collection("coinDetails").add(ticketDetail);

            // Record transaction log
            Map<String, Object> txn = new HashMap<>();
            txn.put("uid", uid);
            txn.put("coins", coinReward);
            txn.put("tickets", ticketReward);
            txn.put("type", "hitz_reward");
            txn.put("taskId", taskId);
            txn.put("requestId", requestId);
            txn.put("time", FieldValue.serverTimestamp());
            db.collection("transactions").add(txn);

            Map<String, Object> resMap = new HashMap<>();
            resMap.put("success", true);
            resMap.put("message", "Hitz reward claimed successfully!");
            resMap.put("coinReward", coinReward);
            resMap.put("ticketReward", ticketReward);
            resMap.put("totalCoins", updatedCoins);
            resMap.put("totalTickets", updatedTickets);

            return ResponseEntity.ok(resMap);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Server error: " + e.getMessage());
        }
    }
}