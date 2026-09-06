package com.example.backend.controller;

import com.example.backend.model.ScratchResponse;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Transaction;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ScratchService {

    private static final int DAILY_LIMIT = 10;
    private static final String USERS_COLLECTION = "users";

    private final Firestore firestore;

    public ScratchService(Firestore firestore) {
        this.firestore = firestore;
    }

    /**
     * Play Scratch & Win
     *
     * Firestore:
     * /users/{userId}
     *
     * Fields used:
     * coins
     * dailyScratchCount
     * dailyScratchDate
     */
    public ScratchResponse playScratch(String userId) {

        if (userId == null || userId.trim().isEmpty()) {
            return new ScratchResponse(
                    false,
                    0,
                    0,
                    0,
                    "Invalid user ID"
            );
        }

        try {

            DocumentReference userRef =
                    firestore.collection(USERS_COLLECTION).document(userId);

            final int[] resultReward = {0};
            final int[] resultCoins = {0};
            final int[] resultCount = {0};
            final int[] resultRemaining = {0};
            final boolean[] resultAllowed = {false};
            final String[] resultMessage = {""};

            ApiFuture<Void> future = firestore.runTransaction(
                    transaction -> {

                        DocumentSnapshot document =
                                transaction.get(userRef).get();

                        if (!document.exists()) {

                            resultAllowed[0] = false;
                            resultReward[0] = 0;
                            resultCoins[0] = 0;
                            resultCount[0] = 0;
                            resultRemaining[0] = 0;
                            resultMessage[0] = "User not found";

                            return null;
                        }

                        /*
                         * ----------------------------------------
                         * Current date
                         * ----------------------------------------
                         */
                        String today = LocalDate.now().toString();

                        /*
                         * ----------------------------------------
                         * Read daily scratch information
                         * ----------------------------------------
                         */
                        String savedDate =
                                document.getString("dailyScratchDate");

                        Long savedCount =
                                document.getLong("dailyScratchCount");

                        int scratchCount =
                                savedCount != null
                                        ? savedCount.intValue()
                                        : 0;

                        /*
                         * ----------------------------------------
                         * Read existing coins
                         *
                         * Your current document has:
                         * coins = 9985
                         *
                         * We preserve that value.
                         * ----------------------------------------
                         */
                        Long savedCoins =
                                document.getLong("coins");

                        int coins =
                                savedCoins != null
                                        ? savedCoins.intValue()
                                        : 0;

                        /*
                         * ----------------------------------------
                         * New day
                         *
                         * If the stored date is different from today,
                         * reset only Scratch & Win counters.
                         *
                         * We DO NOT touch:
                         * dailySpinCount
                         * daily_ads_count
                         * daily_bonus
                         * tickets
                         * etc.
                         * ----------------------------------------
                         */
                        if (savedDate == null ||
                                !today.equals(savedDate)) {

                            scratchCount = 0;

                            transaction.update(
                                    userRef,
                                    "dailyScratchCount",
                                    0L,
                                    "dailyScratchDate",
                                    today
                            );
                        }

                        /*
                         * ----------------------------------------
                         * Check daily limit
                         * ----------------------------------------
                         */
                        if (scratchCount >= DAILY_LIMIT) {

                            resultAllowed[0] = false;
                            resultReward[0] = 0;
                            resultCoins[0] = coins;
                            resultCount[0] = scratchCount;
                            resultRemaining[0] = 0;
                            resultMessage[0] =
                                    "Daily scratch limit reached";

                            return null;
                        }

                        /*
                         * ----------------------------------------
                         * Generate server-side reward
                         * ----------------------------------------
                         */
                        int reward = generateReward();

                        /*
                         * ----------------------------------------
                         * Calculate new values
                         * ----------------------------------------
                         */
                        int newCount = scratchCount + 1;
                        int newCoins = coins + reward;
                        int remaining = DAILY_LIMIT - newCount;

                        /*
                         * ----------------------------------------
                         * Atomically update Firestore
                         * ----------------------------------------
                         */
                        Map<String, Object> updates =
                                new HashMap<>();

                        updates.put(
                                "coins",
                                (long) newCoins
                        );

                        updates.put(
                                "dailyScratchCount",
                                (long) newCount
                        );

                        updates.put(
                                "dailyScratchDate",
                                today
                        );

                        transaction.update(
                                userRef,
                                updates
                        );

                        /*
                         * ----------------------------------------
                         * Result
                         * ----------------------------------------
                         */
                        resultAllowed[0] = true;
                        resultReward[0] = reward;
                        resultCoins[0] = newCoins;
                        resultCount[0] = newCount;
                        resultRemaining[0] = remaining;
                        resultMessage[0] = "Scratch successful";

                        return null;
                    }
            );

            future.get();

            return new ScratchResponse(
                    resultAllowed[0],
                    resultReward[0],
                    resultCoins[0],
                    resultRemaining[0],
                    resultMessage[0]
            );

        } catch (Exception e) {

            e.printStackTrace();

            return new ScratchResponse(
                    false,
                    0,
                    0,
                    0,
                    "Unable to process scratch"
            );
        }
    }

    /**
     * Get Scratch & Win status
     *
     * Does NOT award coins.
     * Does NOT consume a scratch.
     *
     * Android calls:
     * GET /api/scratch/status/{userId}
     */
    public ScratchResponse getScratchStatus(String userId) {

        if (userId == null || userId.trim().isEmpty()) {

            return new ScratchResponse(
                    false,
                    0,
                    0,
                    0,
                    "Invalid user ID"
            );
        }

        try {

            DocumentReference userRef =
                    firestore.collection(USERS_COLLECTION)
                            .document(userId);

            final int[] resultCoins = {0};
            final int[] resultCount = {0};
            final int[] resultRemaining = {0};
            final boolean[] resultAllowed = {false};
            final String[] resultMessage = {""};

            ApiFuture<Void> future = firestore.runTransaction(
                    transaction -> {

                        DocumentSnapshot document =
                                transaction.get(userRef).get();

                        if (!document.exists()) {

                            resultAllowed[0] = false;
                            resultCoins[0] = 0;
                            resultCount[0] = 0;
                            resultRemaining[0] = 0;
                            resultMessage[0] = "User not found";

                            return null;
                        }

                        /*
                         * ----------------------------------------
                         * Current date
                         * ----------------------------------------
                         */
                        String today =
                                LocalDate.now().toString();

                        /*
                         * ----------------------------------------
                         * Existing coins
                         * ----------------------------------------
                         */
                        Long savedCoins =
                                document.getLong("coins");

                        int coins =
                                savedCoins != null
                                        ? savedCoins.intValue()
                                        : 0;

                        /*
                         * ----------------------------------------
                         * Existing scratch data
                         * ----------------------------------------
                         */
                        String savedDate =
                                document.getString(
                                        "dailyScratchDate"
                                );

                        Long savedCount =
                                document.getLong(
                                        "dailyScratchCount"
                                );

                        int scratchCount =
                                savedCount != null
                                        ? savedCount.intValue()
                                        : 0;

                        /*
                         * ----------------------------------------
                         * New day
                         *
                         * Reset Scratch only.
                         * ----------------------------------------
                         */
                        if (savedDate == null ||
                                !today.equals(savedDate)) {

                            scratchCount = 0;

                            transaction.update(
                                    userRef,
                                    "dailyScratchCount",
                                    0L,
                                    "dailyScratchDate",
                                    today
                            );
                        }

                        /*
                         * ----------------------------------------
                         * Calculate remaining scratches
                         * ----------------------------------------
                         */
                        int remaining =
                                Math.max(
                                        0,
                                        DAILY_LIMIT - scratchCount
                                );

                        boolean allowed =
                                remaining > 0;

                        /*
                         * ----------------------------------------
                         * Result
                         * ----------------------------------------
                         */
                        resultAllowed[0] = allowed;
                        resultCoins[0] = coins;
                        resultCount[0] = scratchCount;
                        resultRemaining[0] = remaining;

                        if (allowed) {
                            resultMessage[0] =
                                    "Scratch available";
                        } else {
                            resultMessage[0] =
                                    "Daily scratch limit reached";
                        }

                        return null;
                    }
            );

            future.get();

            return new ScratchResponse(
                    resultAllowed[0],
                    0,
                    resultCoins[0],
                    resultRemaining[0],
                    resultMessage[0]
            );

        } catch (Exception e) {

            e.printStackTrace();

            return new ScratchResponse(
                    false,
                    0,
                    0,
                    0,
                    "Unable to get scratch status"
            );
        }
    }

    /**
     * Server-side random reward.
     *
     * Possible rewards:
     * 5, 10, 10, 20, 20, 25, 50
     */
    private int generateReward() {

        int[] rewards = {
                5,
                10,
                10,
                20,
                20,
                25,
                50
        };

        int index =
                ThreadLocalRandom.current()
                        .nextInt(rewards.length);

        return rewards[index];
    }
}