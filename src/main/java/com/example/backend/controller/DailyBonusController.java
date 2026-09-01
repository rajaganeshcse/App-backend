package com.example.backend.controller;


import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.cloud.FirestoreClient;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api")
public class DailyBonusController {

    // Change this to your application's timezone
    private static final ZoneId APP_ZONE =
            ZoneId.of("Asia/Kolkata");

    private static final int MIN_REWARD = 1;
    private static final int MAX_REWARD = 50;


    // =========================================================
    // DAILY BONUS
    // =========================================================

    @PostMapping("/daily-bonus")
    public ResponseEntity<?> claimDailyBonus(

            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization,

            @RequestBody(
                    required = false
            )
            Map<String, String> request
    ) {

        try {

            // =====================================================
            // CHECK AUTHORIZATION
            // =====================================================

            if (authorization == null
                    || authorization.isEmpty()) {

                return ResponseEntity
                        .status(401)
                        .body(
                                errorResponse(
                                        "Authorization token required"
                                )
                        );
            }


            // =====================================================
            // REMOVE BEARER
            // =====================================================

            String token = authorization;

            if (token.startsWith("Bearer ")) {

                token = token.substring(7);
            }

            token = token.trim();


            if (token.isEmpty()) {

                return ResponseEntity
                        .status(401)
                        .body(
                                errorResponse(
                                        "Invalid token"
                                )
                        );
            }


            // =====================================================
            // VERIFY FIREBASE TOKEN
            // =====================================================

            FirebaseToken decodedToken =
                    FirebaseAuth
                            .getInstance()
                            .verifyIdToken(token);


            // =====================================================
            // GET UID
            // =====================================================

            String uid =
                    decodedToken.getUid();


            if (uid == null
                    || uid.isEmpty()) {

                return ResponseEntity
                        .status(401)
                        .body(
                                errorResponse(
                                        "Invalid user"
                                )
                        );
            }


            // =====================================================
            // REQUEST ID
            // =====================================================

            String requestId = null;

            if (request != null) {

                requestId =
                        request.get("requestId");
            }

            if (requestId == null
                    || requestId.isEmpty()) {

                requestId =
                        UUID.randomUUID()
                                .toString();
            }


            // =====================================================
            // FIRESTORE
            // =====================================================

            Firestore db =
                    FirestoreClient.getFirestore();


            DocumentReference userRef =
                    db.collection("users")
                            .document(uid);


            DocumentSnapshot userDoc =
                    userRef
                            .get()
                            .get();


            // =====================================================
            // USER CHECK
            // =====================================================

            if (!userDoc.exists()) {

                return ResponseEntity
                        .status(404)
                        .body(
                                errorResponse(
                                        "User not found"
                                )
                        );
            }


            // =====================================================
            // TODAY'S DATE
            // =====================================================

            LocalDate today =
                    LocalDate.now(APP_ZONE);


            String todayString =
                    today.toString();


            // Example:
            //
            // 2026-09-02
            //
            // =====================================================


            // =====================================================
            // GET LAST CLAIM DATE
            // =====================================================

            String lastClaimDate =
                    userDoc.getString(
                            "dailyBonusClaimDate"
                    );


            // =====================================================
            // CHECK IF ALREADY CLAIMED TODAY
            // =====================================================

            if (todayString.equals(lastClaimDate)) {

                Map<String, Object> response =
                        new HashMap<>();

                response.put(
                        "success",
                        false
                );

                response.put(
                        "message",
                        "Daily bonus already claimed today"
                );

                response.put(
                        "claimDate",
                        lastClaimDate
                );

                response.put(
                        "today",
                        todayString
                );


                return ResponseEntity
                        .status(409)
                        .body(response);
            }


            // =====================================================
            // GENERATE RANDOM REWARD
            // =====================================================

            int reward =
                    ThreadLocalRandom
                            .current()
                            .nextInt(
                                    MIN_REWARD,
                                    MAX_REWARD + 1
                            );


            // =====================================================
            // CURRENT COINS
            // =====================================================

            Long currentCoins =
                    userDoc.getLong("coins");


            if (currentCoins == null) {

                currentCoins = 0L;
            }


            // =====================================================
            // NEW COINS
            // =====================================================

            long newCoins =
                    currentCoins + reward;


            // =====================================================
            // UPDATE USER
            // =====================================================

            Map<String, Object> update =
                    new HashMap<>();


            update.put(
                    "coins",
                    newCoins
            );

            // SAVE ONLY THE DATE
            update.put(
                    "dailyBonusClaimDate",
                    todayString
            );


            userRef
                    .update(update)
                    .get();


            // =====================================================
            // COIN HISTORY
            // =====================================================

            Map<String, Object> coinDetail =
                    new HashMap<>();


            coinDetail.put(
                    "amount",
                    reward
            );

            coinDetail.put(
                    "type",
                    "daily_bonus"
            );

            coinDetail.put(
                    "status",
                    "Credit"
            );

            coinDetail.put(
                    "istype",
                    "coin"
            );

            coinDetail.put(
                    "requestId",
                    requestId
            );

            coinDetail.put(
                    "claimDate",
                    todayString
            );

            coinDetail.put(
                    "created_at",
                    FieldValue.serverTimestamp()
            );


            userRef
                    .collection("coinDetails")
                    .add(coinDetail)
                    .get();


            // =====================================================
            // SUCCESS RESPONSE
            // =====================================================

            Map<String, Object> response =
                    new HashMap<>();


            response.put(
                    "success",
                    true
            );

            response.put(
                    "message",
                    "Daily bonus claimed successfully"
            );

            response.put(
                    "reward",
                    reward
            );

            response.put(
                    "coins",
                    newCoins
            );

            response.put(
                    "claimDate",
                    todayString
            );


            return ResponseEntity.ok(
                    response
            );


        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(500)
                    .body(
                            errorResponse(
                                    "Server error"
                            )
                    );
        }
    }


    // =========================================================
    // ERROR RESPONSE
    // =========================================================

    private Map<String, Object> errorResponse(
            String message
    ) {

        Map<String, Object> response =
                new HashMap<>();

        response.put(
                "success",
                false
        );

        response.put(
                "message",
                message
        );

        return response;
    }
}
