package com.example.backend.controller;

import com.example.backend.model.ScratchResponse;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ScratchService {

    /*
     * ============================================================
     * DAILY LIMIT
     * ============================================================
     */

    private static final int DAILY_LIMIT = 10;

    private static final String USERS_COLLECTION =
            "users";

    /*
     * ============================================================
     * PLAY SCRATCH
     * ============================================================
     *
     * Firestore document:
     *
     * /users/{userId}
     *
     * Example:
     *
     * /users/5erjhgeWVWfmP1eyo1sbGEDbTFO2
     *
     * Fields:
     *
     * coins
     * dailyScratchCount
     * dailyScratchDate
     *
     * ============================================================
     */

    public ScratchResponse playScratch(
            String userId
    ) {

        try {

            /*
             * ========================================================
             * FIRESTORE
             * ========================================================
             */

            Firestore firestore =
                    FirestoreClient.getFirestore();

            /*
             * ========================================================
             * USER DOCUMENT
             * ========================================================
             */

            DocumentReference userRef =
                    firestore
                            .collection(
                                    USERS_COLLECTION
                            )
                            .document(
                                    userId
                            );

            /*
             * ========================================================
             * RESULT VALUES
             * ========================================================
             *
             * These values are changed inside the transaction and
             * returned after the transaction completes.
             */

            final int[] resultReward =
                    {0};

            final int[] resultCoins =
                    {0};

            final int[] resultRemaining =
                    {0};

            final boolean[] resultAllowed =
                    {false};

            /*
             * ========================================================
             * TODAY
             * ========================================================
             */

            final String today =
                    LocalDate.now()
                            .toString();

            /*
             * ========================================================
             * FIRESTORE TRANSACTION
             * ========================================================
             */

            ApiFuture<Void> transaction =
                    firestore.runTransaction(
                            transaction1 -> {

                                /*
                                 * ==================================================
                                 * READ USER
                                 * ==================================================
                                 */

                                DocumentSnapshot snapshot =
                                        transaction1.get(
                                                userRef
                                        ).get();

                                /*
                                 * ==================================================
                                 * USER DOES NOT EXIST
                                 * ==================================================
                                 */

                                if (!snapshot.exists()) {

                                    throw new RuntimeException(
                                            "User not found"
                                    );
                                }

                                /*
                                 * ==================================================
                                 * READ DAILY DATE
                                 * ==================================================
                                 */

                                String savedDate =
                                        snapshot.getString(
                                                "dailyScratchDate"
                                        );

                                /*
                                 * ==================================================
                                 * READ DAILY COUNT
                                 * ==================================================
                                 */

                                Long savedCount =
                                        snapshot.getLong(
                                                "dailyScratchCount"
                                        );

                                /*
                                 * ==================================================
                                 * READ COINS
                                 * ==================================================
                                 */

                                Long savedCoins =
                                        snapshot.getLong(
                                                "coins"
                                        );

                                /*
                                 * ==================================================
                                 * DEFAULT DAILY COUNT
                                 * ==================================================
                                 */

                                int scratchCount =
                                        savedCount == null
                                                ? 0
                                                : savedCount.intValue();

                                /*
                                 * ==================================================
                                 * DEFAULT COINS
                                 * ==================================================
                                 *
                                 * If coins does not exist, start with 144.
                                 */

                                int coins =
                                        savedCoins == null
                                                ? 144
                                                : savedCoins.intValue();

                                /*
                                 * ==================================================
                                 * NEW DAY
                                 * ==================================================
                                 */

                                if (
                                        savedDate == null
                                                ||
                                                !today.equals(
                                                        savedDate
                                                )
                                ) {

                                    /*
                                     * Reset today's scratch count.
                                     */

                                    scratchCount = 0;

                                    /*
                                     * Data to update.
                                     */

                                    Map<String, Object>
                                            resetData =
                                            new HashMap<>();

                                    resetData.put(
                                            "dailyScratchCount",
                                            0
                                    );

                                    resetData.put(
                                            "dailyScratchDate",
                                            today
                                    );

                                    /*
                                     * IMPORTANT:
                                     *
                                     * Use transaction1 here.
                                     */

                                    transaction1.update(
                                            userRef,
                                            resetData
                                    );
                                }

                                /*
                                 * ==================================================
                                 * CHECK DAILY LIMIT
                                 * ==================================================
                                 */

                                if (
                                        scratchCount
                                                >= DAILY_LIMIT
                                ) {

                                    /*
                                     * No scratch available.
                                     */

                                    resultAllowed[0] =
                                            false;

                                    resultReward[0] =
                                            0;

                                    resultCoins[0] =
                                            coins;

                                    resultRemaining[0] =
                                            0;

                                    return null;
                                }

                                /*
                                 * ==================================================
                                 * GENERATE RANDOM REWARD
                                 * ==================================================
                                 *
                                 * IMPORTANT:
                                 *
                                 * Android does NOT generate this.
                                 *
                                 * Backend generates the reward.
                                 */

                                int reward =
                                        generateReward();

                                /*
                                 * ==================================================
                                 * NEW SCRATCH COUNT
                                 * ==================================================
                                 */

                                int newCount =
                                        scratchCount + 1;

                                /*
                                 * ==================================================
                                 * NEW COIN BALANCE
                                 * ==================================================
                                 */

                                int newCoins =
                                        coins + reward;

                                /*
                                 * ==================================================
                                 * REMAINING SCRATCHES
                                 * ==================================================
                                 */

                                int remaining =
                                        DAILY_LIMIT
                                                - newCount;

                                /*
                                 * ==================================================
                                 * FIRESTORE UPDATE
                                 * ==================================================
                                 */

                                Map<String, Object>
                                        updateData =
                                        new HashMap<>();

                                /*
                                 * Daily scratch count
                                 */

                                updateData.put(
                                        "dailyScratchCount",
                                        newCount
                                );

                                /*
                                 * Today's date
                                 */

                                updateData.put(
                                        "dailyScratchDate",
                                        today
                                );

                                /*
                                 * New coin balance
                                 */

                                updateData.put(
                                        "coins",
                                        newCoins
                                );

                                /*
                                 * ==================================================
                                 * ATOMIC UPDATE
                                 * ==================================================
                                 *
                                 * IMPORTANT:
                                 *
                                 * transaction1.update()
                                 *
                                 * NOT:
                                 *
                                 * transaction.update()
                                 */

                                transaction1.update(
                                        userRef,
                                        updateData
                                );

                                /*
                                 * ==================================================
                                 * RESPONSE DATA
                                 * ==================================================
                                 */

                                resultAllowed[0] =
                                        true;

                                resultReward[0] =
                                        reward;

                                resultCoins[0] =
                                        newCoins;

                                resultRemaining[0] =
                                        remaining;

                                return null;
                            }
                    );

            /*
             * ========================================================
             * WAIT FOR TRANSACTION
             * ========================================================
             */

            transaction.get();

            /*
             * ========================================================
             * LIMIT REACHED
             * ========================================================
             */

            if (!resultAllowed[0]) {

                return new ScratchResponse(
                        false,
                        0,
                        resultCoins[0],
                        0,
                        "Today's scratch limit reached"
                );
            }

            /*
             * ========================================================
             * SUCCESS
             * ========================================================
             */

            return new ScratchResponse(
                    true,
                    resultReward[0],
                    resultCoins[0],
                    resultRemaining[0],
                    "Scratch successful"
            );

        } catch (Exception e) {

            /*
             * ========================================================
             * ERROR
             * ========================================================
             */

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

    /*
     * ============================================================
     * RANDOM REWARD
     * ============================================================
     *
     * The reward is generated ONLY on the backend.
     *
     * Possible rewards:
     *
     * 5
     * 10
     * 10
     * 20
     * 20
     * 25
     * 50
     *
     * ============================================================
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
                        .nextInt(
                                rewards.length
                        );

        return rewards[index];
    }
}

