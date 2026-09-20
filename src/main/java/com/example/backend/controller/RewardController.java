package com.example.backend.controller;

import com.example.backend.model.RewardRequest;
import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
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

    private Map<String, String> getWeeklyPeriodInfo() {
        ZoneId zoneKolkata = ZoneId.of("Asia/Kolkata");
        ZonedDateTime now = ZonedDateTime.now(zoneKolkata);

        ZonedDateTime weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                    .truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime nextReset = weekStart.plusWeeks(1);

        DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        DateTimeFormatter keyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        Map<String, String> info = new HashMap<>();
        info.put("weekStart", weekStart.format(isoFormatter));
        info.put("nextReset", nextReset.format(isoFormatter));
        info.put("weekPeriodKey", weekStart.format(keyFormatter));
        return info;
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

    @GetMapping("/hitz-rewards/status")
    public ResponseEntity<?> getHitzRewardsStatus(
            @RequestHeader(value = "Authorization", required = false) String token) {

        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body("Authorization header missing");
            }
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token);
            String uid = decoded.getUid();

            Map<String, String> weekInfo = getWeeklyPeriodInfo();
            String weekStartStr = weekInfo.get("weekStart");
            String nextResetStr = weekInfo.get("nextReset");
            String weekPeriodKey = weekInfo.get("weekPeriodKey");

            Firestore db = FirestoreClient.getFirestore();
            String claimDocId = uid + "_" + weekPeriodKey;
            DocumentSnapshot claimDoc = db.collection("weekly_hitz_claims").document(claimDocId).get().get();

            Map<String, Object> response = new HashMap<>();
            response.put("weekStart", weekStartStr);
            response.put("nextReset", nextResetStr);

            if (claimDoc.exists() && Boolean.TRUE.equals(claimDoc.getBoolean("claimed"))) {
                response.put("eligible", false);
                response.put("claimed", true);
                response.put("message", "Weekly reward already claimed");
            } else {
                response.put("eligible", true);
                response.put("claimed", false);
                response.put("message", "Weekly reward available");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Server error: " + e.getMessage());
        }
    }

    @PostMapping("/hitz-rewards/claim")
    public ResponseEntity<?> claimHitzReward(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, Object> req) {

        try {
            if (token == null || token.isEmpty()) {
                return ResponseEntity.status(401).body("Authorization header missing");
            }
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

            Map<String, String> weekInfo = getWeeklyPeriodInfo();
            String weekStartStr = weekInfo.get("weekStart");
            String nextResetStr = weekInfo.get("nextReset");
            String weekPeriodKey = weekInfo.get("weekPeriodKey");

            Firestore db = FirestoreClient.getFirestore();

            // Duplicate requestId check
            Query dupQuery = db.collection("transactions").whereEqualTo("requestId", requestId);
            if (!dupQuery.get().get().isEmpty()) {
                Map<String, Object> dupRes = new HashMap<>();
                dupRes.put("eligible", false);
                dupRes.put("claimed", true);
                dupRes.put("message", "Duplicate request");
                dupRes.put("nextReset", nextResetStr);
                return ResponseEntity.badRequest().body(dupRes);
            }

            String claimDocId = uid + "_" + weekPeriodKey;
            DocumentReference claimRef = db.collection("weekly_hitz_claims").document(claimDocId);
            DocumentReference userRef = db.collection("users").document(uid);

            // ATOMIC FIRESTORE TRANSACTION FOR WEEKLY ELIGIBILITY & DUPLICATE CLAIM PREVENTION
            return db.runTransaction(transaction -> {
                DocumentSnapshot claimDoc = transaction.get(claimRef).get();
                if (claimDoc.exists() && Boolean.TRUE.equals(claimDoc.getBoolean("claimed"))) {
                    Map<String, Object> claimedRes = new HashMap<>();
                    claimedRes.put("eligible", false);
                    claimedRes.put("claimed", true);
                    claimedRes.put("message", "Weekly reward already claimed");
                    claimedRes.put("nextReset", nextResetStr);
                    return ResponseEntity.status(400).body(claimedRes);
                }

                DocumentSnapshot userDoc = transaction.get(userRef).get();
                if (!userDoc.exists()) {
                    return ResponseEntity.status(404).body("User not found");
                }

                // Fetch dynamic hitz payouts configuration
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

                // 1. Record weekly claim state in Firestore
                Map<String, Object> claimData = new HashMap<>();
                claimData.put("userId", uid);
                claimData.put("weekStart", weekStartStr);
                claimData.put("weekPeriodKey", weekPeriodKey);
                claimData.put("claimed", true);
                claimData.put("claimedAt", FieldValue.serverTimestamp());
                claimData.put("lastActivityAt", FieldValue.serverTimestamp());
                claimData.put("requestId", requestId);

                transaction.set(claimRef, claimData);

                // 2. Update user balance atomically
                transaction.update(userRef,
                        "coins", FieldValue.increment(coinReward),
                        "tickets", FieldValue.increment(ticketReward)
                );

                // 3. Record history details & transaction log
                Map<String, Object> coinDetail = new HashMap<>();
                coinDetail.put("amount", coinReward);
                coinDetail.put("type", "hitz_reward");
                coinDetail.put("status", "Credit");
                coinDetail.put("istype", "coin");
                coinDetail.put("created_at", FieldValue.serverTimestamp());
                db.collection("users").document(uid).collection("coinDetails").add(coinDetail);

                Map<String, Object> ticketDetail = new HashMap<>();
                ticketDetail.put("amount", ticketReward);
                ticketDetail.put("type", "hitz_reward");
                ticketDetail.put("status", "Credit");
                ticketDetail.put("istype", "token");
                ticketDetail.put("created_at", FieldValue.serverTimestamp());
                db.collection("users").document(uid).collection("coinDetails").add(ticketDetail);

                Map<String, Object> txn = new HashMap<>();
                txn.put("uid", uid);
                txn.put("coins", coinReward);
                txn.put("tickets", ticketReward);
                txn.put("type", "hitz_reward");
                txn.put("taskId", taskId);
                txn.put("requestId", requestId);
                txn.put("weekStart", weekStartStr);
                txn.put("time", FieldValue.serverTimestamp());
                db.collection("transactions").add(txn);

                Map<String, Object> resMap = new HashMap<>();
                resMap.put("eligible", false);
                resMap.put("claimed", true);
                resMap.put("message", "Weekly hit reward claimed successfully");
                resMap.put("coinReward", coinReward);
                resMap.put("ticketReward", ticketReward);
                resMap.put("totalCoins", updatedCoins);
                resMap.put("totalTickets", updatedTickets);
                resMap.put("weekStart", weekStartStr);
                resMap.put("nextReset", nextResetStr);

                return ResponseEntity.ok(resMap);
            }).get();

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Server error: " + e.getMessage());
        }
    }
}