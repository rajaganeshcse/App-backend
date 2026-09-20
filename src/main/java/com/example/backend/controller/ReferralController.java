package com.example.backend.controller;

import com.example.backend.service.ReferralUtil;
import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/referral")
@CrossOrigin
public class ReferralController {

    private static final int REFERRAL_COIN_REWARD = 250;
    private static final int REFERRAL_TICKET_REWARD = 10;

    @GetMapping("/code")
    public ResponseEntity<?> getReferralCode(@RequestHeader("Authorization") String token) {
        try {
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token);
            String uid = decoded.getUid();

            Firestore db = FirestoreClient.getFirestore();
            DocumentReference userRef = db.collection("users").document(uid);
            DocumentSnapshot doc = userRef.get().get();

            if (!doc.exists()) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "User not found");
                return ResponseEntity.badRequest().body(err);
            }

            String code = doc.getString("referralCode");

            if (code == null || code.trim().isEmpty()) {
                code = ReferralUtil.generateCode();
                userRef.update("referralCode", code);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("referralCode", code);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Authentication failed: " + e.getMessage());
            return ResponseEntity.status(401).body(error);
        }
    }

    @PostMapping("/apply")
    public ResponseEntity<?> applyReferralCode(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> body) {
        try {
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token);
            String uid = decoded.getUid();

            if (body == null || !body.containsKey("referralCode")) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "Please enter a referral code");
                return ResponseEntity.badRequest().body(err);
            }

            String code = body.get("referralCode");
            if (code == null || code.trim().isEmpty()) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "Please enter a referral code");
                return ResponseEntity.badRequest().body(err);
            }

            code = code.trim().toUpperCase();

            Firestore db = FirestoreClient.getFirestore();

            // 1. Search for referralCode in users collection
            Query query = db.collection("users").whereEqualTo("referralCode", code).limit(1);
            QuerySnapshot querySnapshot = query.get().get();
            List<QueryDocumentSnapshot> docs = querySnapshot.getDocuments();

            if (docs.isEmpty()) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "Invalid referral code");
                return ResponseEntity.badRequest().body(err);
            }

            DocumentSnapshot referrerDoc = docs.get(0);
            String refUid = referrerDoc.getId();

            // 2. Cannot use own code
            if (refUid.equals(uid)) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "You cannot use your own referral code");
                return ResponseEntity.badRequest().body(err);
            }

            // 3. Check current user doc
            DocumentReference userRef = db.collection("users").document(uid);
            DocumentSnapshot userDoc = userRef.get().get();

            if (!userDoc.exists()) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "User not found");
                return ResponseEntity.badRequest().body(err);
            }

            Boolean referralUsed = userDoc.getBoolean("referralUsed");
            if (Boolean.TRUE.equals(referralUsed)) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "Referral code already used");
                return ResponseEntity.badRequest().body(err);
            }

            DocumentReference referrerRef = db.collection("users").document(refUid);

            WriteBatch batch = db.batch();

            String referrerName = referrerDoc.getString("name");
            if (referrerName == null || referrerName.trim().isEmpty()) {
                referrerName = "User (" + code + ")";
            }

            // Update Current User (User A)
            batch.update(userRef,
                    "referredBy", refUid,
                    "referredByName", referrerName,
                    "referralUsed", true,
                    "coins", FieldValue.increment(REFERRAL_COIN_REWARD),
                    "tickets", FieldValue.increment(REFERRAL_TICKET_REWARD)
            );

            // Update Referrer User (User B)
            Map<String, Object> referralUserData = new HashMap<>();
            referralUserData.put("userId", uid);
            String userName = userDoc.getString("name");
            referralUserData.put("name", userName != null ? userName : "User");
            referralUserData.put("joinedAt", System.currentTimeMillis());

            batch.update(referrerRef,
                    "totalReferralCoins", FieldValue.increment(REFERRAL_COIN_REWARD),
                    "totalReferralTickets", FieldValue.increment(REFERRAL_TICKET_REWARD),
                    "coins", FieldValue.increment(REFERRAL_COIN_REWARD),
                    "tickets", FieldValue.increment(REFERRAL_TICKET_REWARD),
                    "referralUsers." + uid, referralUserData
            );

            // Add Coin & Ticket Details subcollection for User A
            Map<String, Object> userCoinDetail = new HashMap<>();
            userCoinDetail.put("amount", REFERRAL_COIN_REWARD);
            userCoinDetail.put("type", "referral_bonus");
            userCoinDetail.put("status", "Credit");
            userCoinDetail.put("istype", "coin");
            userCoinDetail.put("created_at", FieldValue.serverTimestamp());
            DocumentReference userCoinRef = userRef.collection("coinDetails").document();
            batch.set(userCoinRef, userCoinDetail);

            Map<String, Object> userTicketDetail = new HashMap<>();
            userTicketDetail.put("amount", REFERRAL_TICKET_REWARD);
            userTicketDetail.put("type", "referral_bonus");
            userTicketDetail.put("status", "Credit");
            userTicketDetail.put("istype", "token");
            userTicketDetail.put("created_at", FieldValue.serverTimestamp());
            DocumentReference userTicketRef = userRef.collection("coinDetails").document();
            batch.set(userTicketRef, userTicketDetail);

            // Add Coin & Ticket Details subcollection for Referrer (User B)
            Map<String, Object> referrerCoinDetail = new HashMap<>();
            referrerCoinDetail.put("amount", REFERRAL_COIN_REWARD);
            referrerCoinDetail.put("type", "referral_reward");
            referrerCoinDetail.put("status", "Credit");
            referrerCoinDetail.put("istype", "coin");
            referrerCoinDetail.put("created_at", FieldValue.serverTimestamp());
            DocumentReference referrerCoinRef = referrerRef.collection("coinDetails").document();
            batch.set(referrerCoinRef, referrerCoinDetail);

            Map<String, Object> referrerTicketDetail = new HashMap<>();
            referrerTicketDetail.put("amount", REFERRAL_TICKET_REWARD);
            referrerTicketDetail.put("type", "referral_reward");
            referrerTicketDetail.put("status", "Credit");
            referrerTicketDetail.put("istype", "token");
            referrerTicketDetail.put("created_at", FieldValue.serverTimestamp());
            DocumentReference referrerTicketRef = referrerRef.collection("coinDetails").document();
            batch.set(referrerTicketRef, referrerTicketDetail);

            batch.commit().get();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Referral applied successfully! 🎉 +250 Coins & +10 Tickets credited.");
            response.put("coinsEarned", REFERRAL_COIN_REWARD);
            response.put("ticketsEarned", REFERRAL_TICKET_REWARD);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Server error: " + e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }
}
