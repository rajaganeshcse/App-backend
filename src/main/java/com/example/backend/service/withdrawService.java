package com.example.backend.service;

import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class withdrawService {

    public Map<String, Object> createWithdrawRequest(
            String uid,
            long amount,
            String type,
            String details
    ) throws Exception {

        List<String> allowedTypes = List.of("UPI", "BANK", "GOOGLE", "PHONEPE");

        if (!allowedTypes.contains(type.toUpperCase())) {
            return Map.of("status", false, "message", "Invalid withdraw type");
        }

        Firestore db = FirestoreClient.getFirestore();
        DocumentReference userRef = db.collection("users").document(uid);

        return db.runTransaction(transaction -> {

            DocumentSnapshot userDoc = transaction.get(userRef).get();

            long coins = userDoc.getLong("coins") != null
                    ? userDoc.getLong("coins")
                    : 0;

            if (coins < amount) {
                Map<String, Object> fail = new HashMap<>();
                fail.put("status", false);
                fail.put("message", "Insufficient balance");
                return fail;
            }

            long updatedCoins = coins - amount;
            transaction.update(userRef, "coins", updatedCoins);

            DocumentReference reqRef =
                    db.collection("withdraw_requests").document();

            String requestId = reqRef.getId();

            Map<String, Object> request = new HashMap<>();
            request.put("uid", uid);
            request.put("amount", amount);
            request.put("type", type);
            request.put("details", details);
            request.put("status", "PENDING");

            transaction.set(reqRef, request);

            Map<String, Object> res = new HashMap<>();
            res.put("status", true);
            res.put("message", "Withdraw request submitted");
            res.put("updatedCoins", updatedCoins);
            res.put("requestId", requestId);

            return res;

        }).get();   // ✅ no error
    }
}