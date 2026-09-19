package com.example.backend.service;

import com.example.backend.util.TokenGenerator;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DrawService {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private Firestore db;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final Map<String, Object> drawLocks = new ConcurrentHashMap<>();

    // ── DEFAULT CONFIG PRESETS ─────────────────────────────────────────────
    public static final List<Map<String, Long>> SUPPORTED_PRESETS = List.of(
            Map.of("participationLimit", 5L,   "rewardCoins", 25L,   "ticketCost", 1L),
            Map.of("participationLimit", 10L,  "rewardCoins", 100L,  "ticketCost", 1L),
            Map.of("participationLimit", 20L,  "rewardCoins", 200L,  "ticketCost", 1L),
            Map.of("participationLimit", 25L,  "rewardCoins", 250L,  "ticketCost", 1L),
            Map.of("participationLimit", 50L,  "rewardCoins", 500L,  "ticketCost", 1L),
            Map.of("participationLimit", 100L, "rewardCoins", 1000L, "ticketCost", 1L)
    );

    /* ================= STARTUP INITIALIZATION ================= */

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            System.out.println("🚀 [DrawService] Ensuring active OPEN draws exist for all preset categories on startup...");
            ensureAllPresetDrawsExist();
        } catch (Exception e) {
            System.err.println("❌ [DrawService] Failed to initialize active preset draws on startup: " + e.getMessage());
        }
    }

    /* ================= GET OR CREATE ACTIVE DRAWS FOR ALL PRESETS ================= */

    public synchronized List<Map<String, Object>> ensureAllPresetDrawsExist() throws Exception {
        List<Map<String, Object>> activeDraws = new ArrayList<>();

        for (Map<String, Long> preset : SUPPORTED_PRESETS) {
            long limit = preset.get("participationLimit");
            long reward = preset.get("rewardCoins");
            long cost = preset.get("ticketCost");

            QuerySnapshot openForPreset = db.collection("lucky_draws")
                    .whereEqualTo("status", "OPEN")
                    .whereEqualTo("participationLimit", limit)
                    .limit(1)
                    .get()
                    .get();

            if (!openForPreset.isEmpty()) {
                DocumentSnapshot doc = openForPreset.getDocuments().get(0);
                Map<String, Object> data = doc.getData();
                if (data == null) data = new HashMap<>();
                data.put("id", doc.getId());
                activeDraws.add(data);
            } else {
                Map<String, Object> newDraw = createDrawForPreset(limit, reward, cost);
                activeDraws.add(newDraw);
            }
        }
        return activeDraws;
    }

    public Map<String, Object> getOrCreateActiveDraw() throws Exception {
        List<Map<String, Object>> activeList = ensureAllPresetDrawsExist();
        return activeList.isEmpty() ? new HashMap<>() : activeList.get(0);
    }

    public List<Map<String, Object>> getAllActiveDraws() throws Exception {
        return ensureAllPresetDrawsExist();
    }

    public synchronized Map<String, Object> createDrawForPreset(long participationLimit, long rewardCoins, long ticketCost) throws Exception {
        // Double check open draws for this preset to prevent duplicate OPEN draws for the same preset
        QuerySnapshot openCheck = db.collection("lucky_draws")
                .whereEqualTo("status", "OPEN")
                .whereEqualTo("participationLimit", participationLimit)
                .limit(1)
                .get()
                .get();

        if (!openCheck.isEmpty()) {
            DocumentSnapshot doc = openCheck.getDocuments().get(0);
            Map<String, Object> data = doc.getData();
            if (data == null) data = new HashMap<>();
            data.put("id", doc.getId());
            return data;
        }

        // Get next sequential draw number atomically
        DocumentReference counterRef = db.collection("counters").document("lucky_draw");

        long nextDrawNumber = db.runTransaction(tx -> {
            DocumentSnapshot counterDoc = tx.get(counterRef).get();
            long currentNum = 0L;
            if (counterDoc.exists() && counterDoc.getLong("lastDrawNumber") != null) {
                currentNum = counterDoc.getLong("lastDrawNumber");
            }
            long nextNum = currentNum + 1L;
            Map<String, Object> counterData = new HashMap<>();
            counterData.put("lastDrawNumber", nextNum);
            counterData.put("updatedAt", FieldValue.serverTimestamp());
            tx.set(counterRef, counterData, SetOptions.merge());
            return nextNum;
        }).get();

        String drawId = "DRAW" + nextDrawNumber;

        DocumentReference drawRef = db.collection("lucky_draws").document(drawId);

        Map<String, Object> drawData = new HashMap<>();
        drawData.put("drawId", drawId);
        drawData.put("drawNumber", nextDrawNumber);
        drawData.put("status", "OPEN");

        drawData.put("participationLimit", participationLimit);
        drawData.put("rewardCoins", rewardCoins);
        drawData.put("ticketCost", ticketCost);

        drawData.put("currentParticipation", 0L);
        drawData.put("filledSlots", 0L);
        drawData.put("totalSlots", participationLimit);
        drawData.put("remainingSlots", participationLimit);
        drawData.put("isCompleted", false);

        drawData.put("createdAt", FieldValue.serverTimestamp());

        drawRef.set(drawData).get();

        System.out.println("✅ Automatically created active draw " + drawId
                + " [Preset Category: Limit=" + participationLimit + ", Reward=" + rewardCoins + ", Cost=" + ticketCost + "]");

        drawData.put("id", drawId);
        return drawData;
    }

    /* ================= ADMIN CONFIG ================= */

    public Map<String, Object> getAdminConfigInternal() {
        try {
            DocumentSnapshot doc = db.collection("lucky_draw_config").document("current").get().get();
            if (doc.exists() && doc.getData() != null) {
                return doc.getData();
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to read lucky_draw_config: " + e.getMessage());
        }

        // Default configuration preset
        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("participationLimit", 10L);
        defaultConfig.put("rewardCoins", 100L);
        defaultConfig.put("ticketCost", 1L);
        return defaultConfig;
    }

    public Map<String, Object> updateAdminConfig(long participationLimit, long rewardCoins, long ticketCost, String updatedBy) throws Exception {
        if (participationLimit <= 0 || rewardCoins <= 0 || ticketCost <= 0) {
            throw new IllegalArgumentException("Participation, reward, and ticket cost must be positive numbers.");
        }

        Map<String, Object> config = new HashMap<>();
        config.put("participationLimit", participationLimit);
        config.put("rewardCoins", rewardCoins);
        config.put("ticketCost", ticketCost);
        config.put("updatedAt", FieldValue.serverTimestamp());
        config.put("updatedBy", updatedBy != null ? updatedBy : "ADMIN");

        // 1. Update current config
        db.collection("lucky_draw_config").document("current").set(config).get();

        // 2. Save history snapshot
        db.collection("lucky_draw_config_history").document().set(config);

        System.out.println("⚙️ Admin configuration updated [Participation=" + participationLimit
                + ", Reward=" + rewardCoins + ", Cost=" + ticketCost + "]");

        return config;
    }

    /* ================= JOIN DRAW ================= */

    public Map<String, Object> join(String inputDrawId, String uid, String type, int count) throws Exception {

        final int ticketQty = ("TICKET".equalsIgnoreCase(type)) ? Math.max(count, 1) : 1;
        final String entryType = type == null ? "" : type.toUpperCase();

        // If drawId not provided, locate currently active draw
        String drawId = inputDrawId;
        if (drawId == null || drawId.isEmpty()) {
            Map<String, Object> active = getOrCreateActiveDraw();
            drawId = (String) active.get("drawId");
        }

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

        final String targetDrawId = drawId;

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
            long filled = Optional.ofNullable(draw.getLong("filledSlots"))
                    .orElseGet(() -> Optional.ofNullable(draw.getLong("currentParticipation")).orElse(0L));
            long total = Optional.ofNullable(draw.getLong("totalSlots"))
                    .orElseGet(() -> Optional.ofNullable(draw.getLong("participationLimit")).orElse(10L));
            long ticketCost = Optional.ofNullable(draw.getLong("ticketCost")).orElse(1L);

            String status = draw.getString("status");

            if (!"OPEN".equalsIgnoreCase(status)) {
                throw new RuntimeException("Draw closed");
            }

            /* ================= TICKET DEDUCTION ================= */

            if ("TICKET".equals(entryType)) {
                long requiredTickets = ticketQty * ticketCost;
                if (userTickets < requiredTickets) {
                    throw new RuntimeException("Not enough tickets. Required: " + requiredTickets + ", Available: " + userTickets);
                }

                tx.update(userRef, "tickets", FieldValue.increment(-requiredTickets));

                Map<String, Object> coinDetail = new HashMap<>();
                coinDetail.put("amount", requiredTickets);
                coinDetail.put("type", "Lucky Draw Entry");
                coinDetail.put("drawId", targetDrawId);
                coinDetail.put("status", "Deducted");
                coinDetail.put("istype", "token");
                coinDetail.put("created_at", FieldValue.serverTimestamp());
                userRef.collection("coinDetails").add(coinDetail);
            } else if (!"AD".equals(entryType)) {
                throw new RuntimeException("Invalid entry type");
            }

            /* ================= CAPACITY CHECK ================= */

            if (filled + ticketQty > total) {
                throw new RuntimeException("Not enough slots remaining in this draw (" + (total - filled) + " left)");
            }

            /* ================= FETCH EXISTING TOKENS ================= */

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

            /* ================= GENERATE 10-CHAR TOKENS ================= */

            for (int i = 0; i < ticketQty; i++) {
                long ticketNumber = filled + 1 + i;
                String ticketId = UUID.randomUUID().toString();

                String token;
                int attempts = 0;
                do {
                    token = TokenGenerator.generate10CharToken();
                    attempts++;
                    if (attempts > 100) {
                        token = TokenGenerator.generate10CharToken();
                    }
                } while (existingTokens.contains(token));

                existingTokens.add(token);
                createdTokens.add(token);

                Map<String, Object> data = new HashMap<>();
                data.put("ticketId", ticketId);
                data.put("uid", uid);
                data.put("userId", uid);
                data.put("drawId", targetDrawId);
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

            long newFilled = filled + ticketQty;
            long newRemaining = Math.max(0, total - newFilled);

            tx.update(drawRef,
                    "filledSlots", newFilled,
                    "currentParticipation", newFilled,
                    "remainingSlots", newRemaining
            );

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

            long filled = Optional.ofNullable(draw.getLong("filledSlots"))
                    .orElseGet(() -> Optional.ofNullable(draw.getLong("currentParticipation")).orElse(0L));
            long total = Optional.ofNullable(draw.getLong("totalSlots"))
                    .orElseGet(() -> Optional.ofNullable(draw.getLong("participationLimit")).orElse(10L));

            if (filled < total) return;

            String status = draw.getString("status");
            Boolean isCompleted = draw.getBoolean("isCompleted");

            if ("COMPLETED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status) || Boolean.TRUE.equals(isCompleted)) {
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

            /* REWARD SELECTION FROM DRAW'S FROZEN CONFIGURATION */
            long rewardCoins = Optional.ofNullable(draw.getLong("rewardCoins")).orElse(100L);
            long participationLimit = Optional.ofNullable(draw.getLong("participationLimit")).orElse(total);
            long ticketCost = Optional.ofNullable(draw.getLong("ticketCost")).orElse(1L);
            long drawNumber = Optional.ofNullable(draw.getLong("drawNumber")).orElse(0L);

            final String finalWinnerUid = winnerUid;
            final String finalWinningToken = (winningToken != null) ? winningToken : "TOKEN_N/A";
            final long finalRewardCoins = rewardCoins;
            final Long finalTicketNumber = winnerTicketNumber;
            final String finalTicketId = winningTicketId;

            /* ATOMIC TRANSACTION FOR WINNER CREDITING & HISTORY */
            db.runTransaction(tx -> {

                DocumentReference userRef = db.collection("users").document(finalWinnerUid);

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
                historyData.put("drawNumber", drawNumber);
                historyData.put("participationLimit", participationLimit);
                historyData.put("rewardCoins", finalRewardCoins);
                historyData.put("ticketCost", ticketCost);
                historyData.put("winningTicketId", finalTicketId);
                historyData.put("winningUserId", finalWinnerUid);
                historyData.put("winnerUid", finalWinnerUid);
                historyData.put("winningToken", finalWinningToken);
                historyData.put("winnerTicketNumber", finalTicketNumber);
                historyData.put("rewardType", "COINS");
                historyData.put("rewardAmount", finalRewardCoins);
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

            System.out.println("🎉 LuckyDraw " + drawId + " COMPLETED! Winner: " + finalWinnerUid + " Token: " + finalWinningToken);

            /* ================= AUTOMATICALLY CREATE NEXT DRAW FOR THIS PRESET ================= */
            try {
                createDrawForPreset(participationLimit, rewardCoins, ticketCost);
            } catch (Exception e) {
                System.err.println("❌ Failed to create next draw for preset (" + participationLimit + ") automatically: " + e.getMessage());
            }
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
        result.put("drawNumber", draw.get("drawNumber"));
        result.put("status", draw.getString("status"));
        result.put("participationLimit", Optional.ofNullable(draw.getLong("participationLimit")).orElse(10L));
        result.put("rewardCoins", Optional.ofNullable(draw.getLong("rewardCoins")).orElse(100L));
        result.put("ticketCost", Optional.ofNullable(draw.getLong("ticketCost")).orElse(1L));
        result.put("currentParticipation", Optional.ofNullable(draw.getLong("currentParticipation"))
                .orElseGet(() -> Optional.ofNullable(draw.getLong("filledSlots")).orElse(0L)));
        result.put("filledSlots", Optional.ofNullable(draw.getLong("filledSlots")).orElse(0L));
        result.put("totalSlots", Optional.ofNullable(draw.getLong("totalSlots")).orElse(10L));
        result.put("remainingSlots", Optional.ofNullable(draw.getLong("remainingSlots")).orElse(10L));
        result.put("winningToken", draw.getString("winningToken"));
        result.put("winnerUid", draw.getString("winnerUid"));
        result.put("isCompleted", Optional.ofNullable(draw.getBoolean("isCompleted")).orElse(false));
        result.put("createdAt", draw.get("createdAt"));
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
        result.put("winnerUid", draw.getString("winnerUid"));
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