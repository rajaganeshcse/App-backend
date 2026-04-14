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
            @RequestBody RewardRequest req) throws Exception {

        FirebaseToken decoded =
                FirebaseAuth.getInstance().verifyIdToken(token);

        String uid = decoded.getUid();

        Firestore db = FirestoreClient.getFirestore();
        DocumentReference userRef =
                db.collection("users").document(uid);

        DocumentSnapshot doc = userRef.get().get();

        Long coins = doc.getLong("coins");
        Long tickets = doc.getLong("tickets");
        Long ads = doc.getLong("daily_ads_count");

        if (ads == null) ads = 0L;
        if (coins == null) coins = 0L;
        if (tickets == null) tickets = 0L;

        // 🚫 Daily limit
        if (ads >= 10) {
            return ResponseEntity.badRequest().body("Daily limit reached");
        }

        // 🚫 Duplicate request check
        Query query = db.collection("transactions")
                .whereEqualTo("requestId", req.requestId);

        if (!query.get().get().isEmpty()) {
            return ResponseEntity.badRequest().body("Duplicate request");
        }

        // 🎁 Reward values
        int coinReward = 10;
        int ticketReward = 1;

        // ✅ ATOMIC UPDATE (VERY IMPORTANT 🔐)
        userRef.update(
                "coins", FieldValue.increment(coinReward),
                "tickets", FieldValue.increment(ticketReward),
                "daily_ads_count", FieldValue.increment(1)
        );

        // ✅ COIN HISTORY
        Map<String, Object> coinDetail = new HashMap<>();
        coinDetail.put("amount", coinReward);
        coinDetail.put("type", "ads");
        coinDetail.put("created_at", FieldValue.serverTimestamp());

        userRef.collection("coinDetails").add(coinDetail);

        // ✅ TICKET HISTORY
        Map<String, Object> ticketDetail = new HashMap<>();
        ticketDetail.put("amount", ticketReward);
        ticketDetail.put("type", "ads");
        ticketDetail.put("created_at", FieldValue.serverTimestamp());

        userRef.collection("ticketDetails").add(ticketDetail);

        // ✅ GLOBAL TRANSACTION LOG
        Map<String, Object> txn = new HashMap<>();
        txn.put("uid", uid);
        txn.put("coins", coinReward);
        txn.put("tickets", ticketReward);
        txn.put("type", "ads");
        txn.put("requestId", req.requestId);
        txn.put("time", FieldValue.serverTimestamp());

        db.collection("transactions").add(txn);

        return ResponseEntity.ok("Reward added");
    }
}