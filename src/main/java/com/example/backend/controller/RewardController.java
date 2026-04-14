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
        Long ads = doc.getLong("daily_ads_count");

        if (ads == null) ads = 0L;

        // 🚫 Daily limit
        if (ads >= 10) {
            return ResponseEntity.badRequest().body("Daily limit reached");
        }

        // 🚫 Duplicate check
        Query query = db.collection("transactions")
                .whereEqualTo("requestId", req.requestId);

        if (!query.get().get().isEmpty()) {
            return ResponseEntity.badRequest().body("Duplicate request");
        }

        // ✅ Update coins
        userRef.update(
                "coins", coins + 10,
                "daily_ads_count", ads + 1
        );

        // ✅ Save transaction
        Map<String, Object> txn = new HashMap<>();
        txn.put("uid", uid);
        txn.put("amount", 10);
        txn.put("type", "ad");
        txn.put("requestId", req.requestId);
        txn.put("time", FieldValue.serverTimestamp());

        db.collection("transactions").add(txn);

        return ResponseEntity.ok("Reward added");
    }
}