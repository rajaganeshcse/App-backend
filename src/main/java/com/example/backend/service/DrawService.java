package com.example.backend.service;

import com.example.backend.util.TokenGenerator;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DrawService {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private Firestore db;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final Map<String, Object> drawLocks = new ConcurrentHashMap<>();

    /* ================= JOIN DRAW ================= */

    public Map<String, Object> join(String drawId, String uid, String type) throws Exception {
        return join(drawId, uid, type, 1);
    }

    public Map<String, Object> join(String drawId, String uid, String type, int count) throws Exception {

        final int ticketQty = ("TICKET".equalsIgnoreCase(type)) ? Math.max(count, 1) : 1;
        final String entryType = type == null ? "" : type.toUpperCase();

        DocumentReference userRef = db.collection("users").document(uid);
        DocumentReference drawRef = db.collection("lucky_draws").document(drawId);

        CollectionReference ticketRef = db.collection("lucky_draw_tickets")
                .document(drawId)
                .collection("tickets");

        /* ================= FREE ENTRY CHECK ================= */

        if ("AD".equals(entryType)) {
            Query q = ticketRef
                    .whereEqualTo("uid", uid)
                    .whereEqualTo("type", "AD")
                    .limit(1);

            if (!q.get().get().isEmpty()) {
                throw new RuntimeException("Free entry already used");
            }
        }

        List<Map<String, Object>> createdTickets = new ArrayList<>();
        List<String> createdTokens = new ArrayList<>();

        /* ================= TRANSACTION ================= */

        db.runTransaction(tx -> {

            DocumentSnapshot user = tx.get(userRef).get();
            DocumentSnapshot draw = tx.get(drawRef).get();

            if (!user.exists()) {
                throw new RuntimeException("User not found");
            }
            if (!draw.exists()) {
                throw new RuntimeException("Draw not found");
            }

            long userTickets = Optional.ofNullable(user.getLong("tickets")).orElse(0L);
            long filled = Optional.ofNullable(draw.getLong("filledSlots")).orElse(0L);
            long total = Optional.ofNullable(draw.getLong("totalSlots")).orElse(0L);

            String status = draw.getString("status");

            if (!"OPEN".equalsIgnoreCase(status)) {
                throw new RuntimeException("Draw closed");
            }

            /* ================= TICKET ENTRY ================= */

            if ("TICKET".equals(entryType)) {
                if (userTickets < ticketQty) {
                    throw new RuntimeException("Not enough tickets. Available: " + userTickets);
                }

                tx.update(userRef, "tickets", FieldValue.increment(-ticketQty));

                Map<String, Object> coinDetail = new HashMap<>();
                coinDetail.put("amount", ticketQty);
                coinDetail.put("type", "Lucky Draw");
                coinDetail.put("status", "Deducted");
                coinDetail.put("istype", "token");
                coinDetail.put("created_at", FieldValue.serverTimestamp());
                userRef.collection("coinDetails").add(coinDetail);
            } else if (!"AD".equals(entryType)) {
                throw new RuntimeException("Invalid entry type");
            }

            /* ================= CHECK LIMIT ================= */

            if (filled + ticketQty > total) {
                throw new RuntimeException("Not enough slots remaining in this draw (" + (total - filled) + " left)");
            }

            /* ================= FETCH EXISTING TOKENS FOR UNIQUENESS ================= */

            Set<String> existingTokens = new HashSet<>();
            try {
                QuerySnapshot existingTickets = ticketRef.get().get();
                for (DocumentSnapshot doc : existingTickets.getDocuments()) {
                    String t = doc.getString("token");
                    if (t != null) {
                        existingTokens.add(t);
                    }
                }
            } catch (Exception ignored) {}

            /* ================= GENERATE UNIQUE 10-CHAR TOKENS & CREATE ENTRIES ================= */

            for (int i = 0; i < ticketQty; i++) {
                long ticketNumber = filled + 1 + i;
                String ticketId = UUID.randomUUID().toString();

                String token;
                int attempts = 0;
                do {
                    token = TokenGenerator.generate10CharToken();
                    attempts++;
                    if (attempts > 100) {
                        token = TokenGenerator.generate10CharToken() + (i % 10);
                        token = token.substring(0, 10);
                    }
                } while (existingTokens.contains(token));

                existingTokens.add(token);
                createdTokens.add(token);

                Map<String, Object> data = new HashMap<>();
                data.put("ticketId", ticketId);
                data.put("uid", uid);
                data.put("userId", uid);
                data.put("drawId", drawId);
                data.put("type", entryType);
                data.put("token", token);
                data.put("status", "ACTIVE");
                data.put("winning", false);
                data.put("ticketNumber", ticketNumber);
                data.put("createdAt", FieldValue.serverTimestamp());

                tx.set(ticketRef.document(ticketId), data);
                tx.set(userRef.collection("myTickets").document(ticketId), data);

                createdTickets.add(data);
            }

            tx.update(drawRef, "filledSlots", FieldValue.increment(ticketQty));

            return null;

        }).get();

        /* ================= CHECK WINNER ================= */

        checkWinner(drawId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", ("AD".equalsIgnoreCase(type))
                ? "Joined with Free Ad entry 🎟️"
                : "Joined with " + count + " Ticket(s) 🎟️");
        response.put("drawId", drawId);
        response.put("ticketsEntered", ticketQty);
        response.put("tokens", createdTokens);
        return response;
    }

    /* ================= ATOMIC & SECURE RANDOM WINNER SELECTION ================= */

    public void checkWinner(String drawId) throws Exception {

        Object lock = drawLocks.computeIfAbsent(drawId, k -> new Object());

        synchronized (lock) {

            DocumentReference drawRef = db.collection("lucky_draws").document(drawId);
            DocumentSnapshot draw = drawRef.get().get();

            if (!draw.exists()) return;

            long filled = Optional.ofNullable(draw.getLong("filledSlots")).orElse(0L);
            long total = Optional.ofNullable(draw.getLong("totalSlots")).orElse(0L);

            if (filled < total) return;

            String status = draw.getString("status");
            Boolean isCompleted = draw.getBoolean("isCompleted");

            if ("COMPLETED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status) || Boolean.TRUE.equals(isCompleted)) {
                System.out.println("Draw " + drawId + " already completed/closed.");
                return;
            }

            /* Mark as CLOSING to prevent concurrent executions */
            drawRef.update("status", "CLOSING").get();

            CollectionReference ticketRef = db.collection("lucky_draw_tickets")
                    .document(drawId)
                    .collection("tickets");

            QuerySnapshot querySnapshot = ticketRef
                    .whereEqualTo("status", "ACTIVE")
                    .get()
                    .get();

            List<QueryDocumentSnapshot> eligibleTickets = querySnapshot.getDocuments();

            if (eligibleTickets.isEmpty()) {
                /* Fallback: fetch all tickets regardless of status */
                eligibleTickets = ticketRef.get().get().getDocuments();
            }

            if (eligibleTickets.isEmpty()) {
                drawRef.update("status", "OPEN").get();
                return;
            }

            /* SECURE RANDOM SELECTION */
            int winnerIndex = SECURE_RANDOM.nextInt(eligibleTickets.size());
            DocumentSnapshot winningTicket = eligibleTickets.get(winnerIndex);

            String winnerUid = winningTicket.getString("uid");
            if (winnerUid == null) winnerUid = winningTicket.getString("userId");
            String winningToken = winningTicket.getString("token");
            Long winnerTicketNumber = winningTicket.getLong("ticketNumber");
            String winningTicketId = winningTicket.getId();

            /* REWARD SELECTION */
            long rewardCoins = 0L;
            List<?> rewardsList = (List<?>) draw.get("rewards");
            if (rewardsList != null && !rewardsList.isEmpty()) {
                Object selectedRewardObj = rewardsList.get(SECURE_RANDOM.nextInt(rewardsList.size()));
                if (selectedRewardObj instanceof Map) {
                    Object amtObj = ((Map<?, ?>) selectedRewardObj).get("amount");
                    if (amtObj instanceof Number) {
                        rewardCoins = ((Number) amtObj).longValue();
                    }
                } else if (selectedRewardObj instanceof Number) {
                    rewardCoins = ((Number) selectedRewardObj).longValue();
                }
            } else {
                rewardCoins = Optional.ofNullable(draw.getLong("rewardCoins")).orElse(0L);
            }

            final String finalWinnerUid = winnerUid;
            final String finalWinningToken = (winningToken != null) ? winningToken : "TOKEN_N/A";
            final long finalRewardCoins = rewardCoins;
            final Long finalTicketNumber = winnerTicketNumber;
            final String finalTicketId = winningTicketId;

            /* ATOMIC TRANSACTION FOR WINNER CREDITING & HISTORY */
            db.runTransaction(tx -> {

                DocumentReference userRef = db.collection("users").document(finalWinnerUid);
                DocumentSnapshot userDoc = tx.get(userRef).get();

                /* 1. Update winning ticket */
                DocumentReference winTicketRef = ticketRef.document(finalTicketId);
                tx.update(winTicketRef,
                        "status", "WINNER",
                        "winning", true,
                        "rewardAmount", finalRewardCoins,
                        "wonAt", FieldValue.serverTimestamp()
                );

                try {
                    DocumentReference userTicketRef = userRef.collection("myTickets").document(finalTicketId);
                    tx.update(userTicketRef,
                            "status", "WINNER",
                            "winning", true,
                            "rewardAmount", finalRewardCoins,
                            "wonAt", FieldValue.serverTimestamp()
                    );
                } catch (Exception ignored) {}

                /* 2. Credit winner account */
                tx.update(userRef, "coins", FieldValue.increment(finalRewardCoins));

                /* 3. Add coinDetail transaction record */
                Map<String, Object> coinDetail = new HashMap<>();
                coinDetail.put("amount", finalRewardCoins);
                coinDetail.put("type", "LUCKY_DRAW_REWARD");
                coinDetail.put("drawId", drawId);
                coinDetail.put("winningToken", finalWinningToken);
                coinDetail.put("status", "Credit");
                coinDetail.put("istype", "coin");
                coinDetail.put("created_at", FieldValue.serverTimestamp());

                DocumentReference newCoinDetailRef = userRef.collection("coinDetails").document();
                tx.set(newCoinDetailRef, coinDetail);

                /* 4. Create draw history record in drawHistory/{drawId} */
                DocumentReference historyRef = db.collection("drawHistory").document(drawId);
                Map<String, Object> historyData = new HashMap<>();
                historyData.put("drawId", drawId);
                historyData.put("winningTicketId", finalTicketId);
                historyData.put("winningUserId", finalWinnerUid);
                historyData.put("winnerUid", finalWinnerUid);
                historyData.put("winningToken", finalWinningToken);
                historyData.put("winnerTicketNumber", finalTicketNumber);
                historyData.put("rewardType", "COINS");
                historyData.put("rewardAmount", finalRewardCoins);
                historyData.put("rewardCoins", finalRewardCoins);
                historyData.put("status", "COMPLETED");
                historyData.put("completedAt", FieldValue.serverTimestamp());

                tx.set(historyRef, historyData);

                /* 5. Update draw document */
                tx.update(drawRef,
                        "winnerUid", finalWinnerUid,
                        "winnerTicketNumber", finalTicketNumber,
                        "winningToken", finalWinningToken,
                        "rewardCoins", finalRewardCoins,
                        "status", "COMPLETED",
                        "isCompleted", true,
                        "completedAt", FieldValue.serverTimestamp()
                );

                return null;

            }).get();

            /* Mark non-winning tickets as EXPIRED asynchronously */
            for (DocumentSnapshot doc : eligibleTickets) {
                if (!doc.getId().equals(finalTicketId)) {
                    ticketRef.document(doc.getId()).update("status", "EXPIRED");
                }
            }

            /* Send FCM notification */
            try {
                DocumentSnapshot winnerDoc = db.collection("users").document(finalWinnerUid).get().get();
                if (winnerDoc.exists()) {
                    String fcmToken = winnerDoc.getString("fcmToken");
                    if (fcmToken != null && !fcmToken.isEmpty()) {
                        notificationService.send(
                                fcmToken,
                                "Congratulations! You are the Lucky Winner 🏆🎊",
                                "Your winning token is " + finalWinningToken + "! You won " + finalRewardCoins + " Coins.",
                                "" + finalRewardCoins + " 🪙"
                        );
                    }
                }
            } catch (Exception e) {
                System.out.println("Failed to send FCM notification: " + e.getMessage());
            }

            System.out.println("LuckyDraw " + drawId + " completed successfully! Winning Token: " + finalWinningToken + " Winner: " + finalWinnerUid);
        }
    }

    /* ================= STATUS & WINNER APIS ================= */

    public Map<String, Object> getDrawStatus(String drawId) throws Exception {
        DocumentSnapshot draw = db.collection("lucky_draws").document(drawId).get().get();
        if (!draw.exists()) {
            throw new RuntimeException("Draw not found");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("drawId", drawId);
        result.put("status", draw.getString("status"));
        result.put("filledSlots", Optional.ofNullable(draw.getLong("filledSlots")).orElse(0L));
        result.put("totalSlots", Optional.ofNullable(draw.getLong("totalSlots")).orElse(0L));
        result.put("rewardCoins", Optional.ofNullable(draw.getLong("rewardCoins")).orElse(0L));
        result.put("winningToken", draw.getString("winningToken"));
        result.put("winnerUid", draw.getString("winnerUid"));
        result.put("isCompleted", Optional.ofNullable(draw.getBoolean("isCompleted")).orElse(false));
        result.put("completedAt", draw.get("completedAt"));
        return result;
    }

    public Map<String, Object> getWinner(String drawId) throws Exception {
        DocumentSnapshot draw = db.collection("lucky_draws").document(drawId).get().get();
        if (!draw.exists()) {
            throw new RuntimeException("Draw not found");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("drawId", drawId);
        result.put("status", draw.getString("status"));
        result.put("winningToken", draw.getString("winningToken"));
        result.put("winnerTicketNumber", draw.get("winnerTicketNumber"));
        result.put("rewardCoins", Optional.ofNullable(draw.getLong("rewardCoins")).orElse(0L));
        result.put("completedAt", draw.get("completedAt"));
        return result;
    }

    public List<Map<String, Object>> getHistory() throws Exception {
        QuerySnapshot snap = db.collection("drawHistory")
                .orderBy("completedAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .get();

        List<Map<String, Object>> history = new ArrayList<>();
        for (DocumentSnapshot doc : snap.getDocuments()) {
            Map<String, Object> data = doc.getData();
            if (data != null) {
                history.add(data);
            }
        }
        return history;
    }
}