package com.example.backend.service;

import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class withdrawService {
    @Autowired
    private NotificationService notificationService;

    @Autowired
    private WithdrawalCodeAllocationService codeAllocationService;

    public Map<String, Object> createWithdrawRequest(
            String uid,
            long amount,
            String type,
            String details,
            Long coinss
    ) throws Exception {

        Firestore db = FirestoreClient.getFirestore();

        DocumentSnapshot userSnapshot = db.collection("users")
                .document(uid)
                .get()
                .get();

        // ✅ Normalize type (GOOGLE_PLAY, AMAZON, PHONEPE, UPI, BANK)
        String normalizedType = WithdrawalCodeAllocationService.normalizeMethod(type);
        boolean isCodeBased = WithdrawalCodeAllocationService.isCodeBasedMethod(normalizedType);

        // ✅ Check if the requested withdraw type is enabled in Firestore settings/reward_config
        try {
            DocumentSnapshot configDoc = db.collection("settings").document("reward_config").get().get();
            if (configDoc.exists()) {
                String configKey;
                if ("GOOGLE_PLAY".equals(normalizedType)) {
                    configKey = "googleEnabled";
                } else if ("AMAZON".equals(normalizedType)) {
                    configKey = "amazonEnabled";
                } else if ("PHONEPE".equals(normalizedType)) {
                    configKey = "phonepeEnabled";
                } else if ("UPI".equals(normalizedType)) {
                    configKey = "upiEnabled";
                } else {
                    configKey = "bankEnabled";
                }

                Boolean isEnabled = configDoc.getBoolean(configKey);
                if (isEnabled != null && !isEnabled) {
                    return Map.of(
                            "status", false,
                            "message", normalizedType + " withdrawals are currently disabled by admin"
                    );
                }
            }
        } catch (Exception ignored) {}

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

            if (coinss == null || coinss > coins) {
                Map<String, Object> fail = new HashMap<>();
                fail.put("status", false);
                fail.put("message", "Insufficient balance");
                if (userSnapshot.exists()) {
                    String token = userSnapshot.getString("fcmToken");
                    if (token != null && !token.isEmpty()) {
                        notificationService.send(token, "Withdrawal Failed ❌", "Your withdrawal request failed due to insufficient coins balance.", "₹ " + amount);
                    }
                }
                return fail;
            }

            // ✅ Deduct coins
            long updatedCoins = coins - coinss;
            transaction.update(userRef, "coins", updatedCoins);

            // ✅ Create withdraw request
            DocumentReference reqRef = db.collection("redeem_requests").document();
            String requestId = reqRef.getId();

            // Check code allocation if code-based method
            String allocatedCode = null;
            if (isCodeBased) {
                // Query matching AVAILABLE code doc inside transaction/service
                allocatedCode = codeAllocationService.findAndAllocateCode(normalizedType, amount, uid, requestId);
            }

            String initialStatus = (allocatedCode != null) ? "SUCCESS" : "PENDING";

            Map<String, Object> request = new HashMap<>();
            request.put("uid", uid);
            request.put("amount", amount);
            request.put("coinused", coinss);
            request.put("type", normalizedType);
            request.put("details", details != null ? details : "");
            request.put("created_at", FieldValue.serverTimestamp());
            request.put("status", initialStatus);

            if (allocatedCode != null) {
                request.put("voucher_code", allocatedCode);
                request.put("voucher_added_at", FieldValue.serverTimestamp());
            }

            transaction.set(reqRef, request);

            Map<String, Object> coinDetail = new HashMap<>();
            coinDetail.put("amount", coinss);
            coinDetail.put("type", "Withdrawal (" + normalizedType + ")");
            coinDetail.put("status", "Deducted");
            coinDetail.put("istype", "coin");
            coinDetail.put("created_at", FieldValue.serverTimestamp());
            db.collection("users")
                    .document(uid).collection("coinDetails").add(coinDetail);

            // Send notification
            if (userSnapshot.exists()) {
                String token = userSnapshot.getString("fcmToken");
                if (token != null && !token.isEmpty()) {
                    if (allocatedCode != null) {
                        notificationService.send(token, "Withdrawal Approved 🎉", "Your " + normalizedType + " voucher code is ready: " + allocatedCode, "₹ " + amount);
                    } else {
                        notificationService.send(token, "Withdrawal Pending", "Your request for " + normalizedType + " ₹" + amount + " is submitted. It will be fulfilled automatically when a code is added.", "₹ " + amount);
                    }
                }
            }

            // ✅ Response
            Map<String, Object> res = new HashMap<>();
            res.put("status", true);
            res.put("updatedCoins", updatedCoins);
            res.put("requestId", requestId);
            res.put("codeStatus", initialStatus);

            if (allocatedCode != null) {
                res.put("code", allocatedCode);
                res.put("message", "Redeem successful! Voucher code is ready.");
            } else if (isCodeBased) {
                res.put("message", "Withdrawal Pending. No " + normalizedType + " code is currently available. Your request will be processed automatically when a matching code is added.");
            } else {
                res.put("message", "Withdraw request submitted");
            }

            return res;

        }).get();
    }
}