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

    // =========================================================
    // CONFIG
    // =========================================================

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
                    || authorization.trim().isEmpty()) {

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

            String token = authorization.trim();

            if (token.startsWith("Bearer ")) {
                token = token.substring(7).trim();
            }

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
                    || uid.trim().isEmpty()) {

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
                requestId = request.get("requestId");
            }

            if (requestId == null
                    || requestId.trim().isEmpty()) {

                requestId =
                        UUID.randomUUID().toString();
            }


            // =====================================================
            // FIRESTORE
            // =====================================================

            Firestore db =
                    FirestoreClient.getFirestore();

            DocumentReference userRef =
                    db.collection("users")
                            .document(uid);


            // =====================================================
            // TODAY'S DATE
            // =====================================================

            LocalDate today =
                    LocalDate.now(APP_ZONE);

            String todayString =
                    today.toString();


            // =====================================================
            // RESULT HOLDER
            // =====================================================

            Map<String, Object> result =
                    db.runTransaction(transaction -> {

                        // =============================================
                        // GET USER INSIDE TRANSACTION
                        // =============================================

                        DocumentSnapshot userDoc =
                                transaction
                                        .get(userRef)
                                        .get();


                        // =============================================
                        // USER CHECK
                        // =============================================

                        if (!userDoc.exists()) {

                            Map<String, Object> error =
                                    new HashMap<>();

                            error.put(
                                    "error",
                                    "User not found"
                            );

                            return error;
                        }


                        // =============================================
                        // CHECK LAST CLAIM DATE
                        // =============================================

                        Object claimDateObject =
                                userDoc.get(
                                        "dailyBonusClaimDate"
                                );

                        String lastClaimDate = null;


                        // ---------------------------------------------
                        // NEW FORMAT: STRING
                        // ---------------------------------------------

                        if (claimDateObject instanceof String) {

                            lastClaimDate =
                                    (String) claimDateObject;
                        }


                        // ---------------------------------------------
                        // OLD FORMAT: FIRESTORE TIMESTAMP
                        // ---------------------------------------------
                        //
                        // No Timestamp import is required.
                        // This safely handles old documents that
                        // already contain a Timestamp.
                        //

                        else if (
                                claimDateObject
                                        instanceof
                                        com.google.cloud.Timestamp
                        ) {

                            com.google.cloud.Timestamp timestamp =
                                    (com.google.cloud.Timestamp)
                                            claimDateObject;

                            lastClaimDate =
                                    timestamp
                                            .toDate()
                                            .toInstant()
                                            .atZone(APP_ZONE)
                                            .toLocalDate()
                                            .toString();
                        }


                        // =============================================
                        // ALREADY CLAIMED TODAY
                        // =============================================

                        if (todayString.equals(lastClaimDate)) {

                            Map<String, Object> alreadyClaimed =
                                    new HashMap<>();

                            alreadyClaimed.put(
                                    "alreadyClaimed",
                                    true
                            );

                            alreadyClaimed.put(
                                    "claimDate",
                                    lastClaimDate
                            );

                            alreadyClaimed.put(
                                    "today",
                                    todayString
                            );

                            return alreadyClaimed;
                        }


                        // =============================================
                        // CURRENT COINS
                        // =============================================

                        Long currentCoins =
                                userDoc.getLong("coins");

                        if (currentCoins == null) {
                            currentCoins = 0L;
                        }


                        // =============================================
                        // GENERATE RANDOM REWARD
                        // =============================================

                        int reward =
                                ThreadLocalRandom
                                        .current()
                                        .nextInt(
                                                MIN_REWARD,
                                                MAX_REWARD + 1
                                        );


                        // =============================================
                        // NEW COINS
                        // =============================================

                        long newCoins =
                                currentCoins + reward;


                        // =============================================
                        // UPDATE USER
                        // =============================================

                        Map<String, Object> update =
                                new HashMap<>();

                        update.put(
                                "coins",
                                newCoins
                        );

                        // IMPORTANT:
                        // Store the claim date as STRING.
                        update.put(
                                "dailyBonusClaimDate",
                                todayString
                        );


                        // =============================================
                        // ATOMIC USER UPDATE
                        // =============================================

                        transaction.update(
                                userRef,
                                update
                        );


                        // =============================================
                        // SUCCESS RESULT
                        // =============================================

                        Map<String, Object> success =
                                new HashMap<>();

                        success.put(
                                "alreadyClaimed",
                                false
                        );

                        success.put(
                                "reward",
                                reward
                        );

                        success.put(
                                "coins",
                                newCoins
                        );

                        success.put(
                                "claimDate",
                                todayString
                        );

                        return success;

                    }).get();


            // =====================================================
            // USER NOT FOUND
            // =====================================================

            if ("User not found".equals(
                    result.get("error")
            )) {

                return ResponseEntity
                        .status(404)
                        .body(
                                errorResponse(
                                        "User not found"
                                )
                        );
            }


            // =====================================================
            // ALREADY CLAIMED
            // =====================================================

            Boolean alreadyClaimed =
                    (Boolean) result.get(
                            "alreadyClaimed"
                    );

            if (Boolean.TRUE.equals(alreadyClaimed)) {

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
                        result.get("claimDate")
                );

                response.put(
                        "today",
                        result.get("today")
                );

                return ResponseEntity
                        .status(409)
                        .body(response);
            }


            // =====================================================
            // GET SUCCESS VALUES
            // =====================================================

            int reward =
                    ((Number) result.get("reward"))
                            .intValue();

            long newCoins =
                    ((Number) result.get("coins"))
                            .longValue();


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


            // =====================================================
            // SAVE COIN HISTORY
            // =====================================================

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