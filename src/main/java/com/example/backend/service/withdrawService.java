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

        // ✅ Validate type
        List<String> allowedTypes = List.of("UPI", "BANK", "GPAY", "PHONEPE");

        if (!allowedTypes.contains(type.toUpperCase())) {
            return Map.of("status", "failed", "message", "Invalid withdraw type");
        }
        Firestore db = FirestoreClient.getFirestore();

        DocumentReference userRef = db.collection("users").document(uid);

        DocumentSnapshot userDoc = userRef.get().get();

        long coins = 0;

        if (userDoc.exists() && userDoc.getLong("coins") != null) {
            coins = userDoc.getLong("coins");
        }

        // ❌ Balance check
        if (coins < amount) {
            return Map.of("status", "failed", "message", "Insufficient balance");
        }

        // ❗ Optional: prevent multiple pending requests
        Query query = db.collection("withdraw_requests")
                .whereEqualTo("userId", uid)
                .whereEqualTo("status", "PENDING");
        QuerySnapshot qs = query.get().get();

        if (!qs.isEmpty()) {
            return Map.of("status", "failed", "message", "Pending request already exists");
        }

        // ✅ Create withdraw request (NO deduction here)
        Map<String, Object> request = new HashMap<>();
        request.put("userId", uid);
        request.put("amount", amount);
        request.put("type", type.toUpperCase());
        request.put("details", details);
        request.put("status", "PENDING");
        request.put("createdAt", System.currentTimeMillis());

        db.collection("withdraw_requests").add(request);

        return Map.of(
                "status", "success",
                "message", "Withdraw request submitted"
        );
    }
}