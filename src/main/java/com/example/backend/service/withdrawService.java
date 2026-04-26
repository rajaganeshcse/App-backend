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
            String details,
            Long coinss
    ) throws Exception {

        // ✅ Normalize type (fix Android lowercase issue)
        String normalizedType = type.toUpperCase();

        // ✅ Allowed types (Amazon added)
        List<String> allowedTypes = List.of(
                "UPI",
                "BANK",
                "GOOGLE",
                "PHONEPE",
                "AMAZON"
        );

        if (!allowedTypes.contains(normalizedType)) {
            return Map.of(
                    "status", false,
                    "message", "Invalid withdraw type"
            );
        }

        Firestore db = FirestoreClient.getFirestore();
        DocumentReference userRef = db.collection("users").document(uid);

        return db.runTransaction(transaction -> {

            DocumentSnapshot userDoc = transaction.get(userRef).get();

            if (!userDoc.exists()) {
                Map<String, Object> fail = new HashMap<>();
                fail.put("status", false);
                fail.put("message", "User not found");
                return fail;
            }

            Long coinsObj = userDoc.getLong("coins");
            long coins = coinsObj != null ? coinsObj : 0;

            if (coinss > coins) {
                Map<String, Object> fail = new HashMap<>();
                fail.put("status", false);
                fail.put("message", "Insufficient balance");
                return fail;
            }

            // ✅ Deduct coins
            long updatedCoins = coins - coinss;
            transaction.update(userRef, "coins", updatedCoins);

            // ✅ Create withdraw request
            DocumentReference reqRef =
                    db.collection("reedem_requests").document();

            String requestId = reqRef.getId();

            Map<String, Object> request = new HashMap<>();
            request.put("uid", uid);
            request.put("amount", amount);
            request.put("coinused", coinss);
            request.put("type", normalizedType);
            request.put("details", details);
            request.put("status", "PENDING");

            // ✅ IMPORTANT: timestamp
            request.put("createdAt", FieldValue.serverTimestamp());

            transaction.set(reqRef, request);

            // ✅ Response
            Map<String, Object> res = new HashMap<>();
            res.put("status", true);
            res.put("message", "Withdraw request submitted");
            res.put("updatedCoins", updatedCoins);
            res.put("requestId", requestId);

            return res;

        }).get(); // ✅ Blocking return for Spring
    }
}