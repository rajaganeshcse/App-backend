package com.example.backend.service;

import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DrawService {

    @Autowired
    private Firestore firestore;

    public void join(String drawId, String uid, int ticketCount) throws Exception {

        DocumentReference userRef =
                firestore.collection("users").document(uid);

        DocumentReference drawRef =
                firestore.collection("lucky_draws").document(drawId);

        CollectionReference ticketRef =
                firestore.collection("lucky_draw_tickets")
                        .document(drawId)
                        .collection("tickets");

        firestore.runTransaction(transaction -> {

            DocumentSnapshot userSnap = transaction.get(userRef).get();
            DocumentSnapshot drawSnap = transaction.get(drawRef).get();

            long userTickets = userSnap.getLong("tickets");
            long sold = drawSnap.getLong("soldTickets");
            long total = drawSnap.getLong("totalTickets");
            String status = drawSnap.getString("status");

            /* 🚫 Anti-cheat checks */

            if (!"OPEN".equals(status))
                throw new RuntimeException("Draw closed");

            if (userTickets < ticketCount)
                throw new RuntimeException("Not enough tickets");

            if (sold + ticketCount > total)
                throw new RuntimeException("Draw full");

            /* 🎟 Create tickets */

            for (int i = 0; i < ticketCount; i++) {

                String ticketId = UUID.randomUUID().toString();

                Map<String, Object> data = new HashMap<>();
                data.put("uid", uid);
                data.put("createdAt", FieldValue.serverTimestamp());

                transaction.set(ticketRef.document(ticketId), data);
            }

            /* 💸 Deduct tickets */

            transaction.update(userRef,
                    "tickets", FieldValue.increment(-ticketCount));

            long newSold = sold + ticketCount;

            transaction.update(drawRef,
                    "soldTickets", newSold);

            return newSold;

        }).get();

        /* 🎯 AUTO WINNER CHECK */

        checkAndSelectWinner(drawId);
    }

    /* ================= WINNER ================= */

    public void checkAndSelectWinner(String drawId) throws Exception {

        DocumentReference drawRef =
                firestore.collection("lucky_draws").document(drawId);

        DocumentSnapshot drawSnap = drawRef.get().get();

        long sold = drawSnap.getLong("soldTickets");
        long total = drawSnap.getLong("totalTickets");

        if (sold < total) return;

        Boolean done = drawSnap.getBoolean("isCompleted");
        if (done != null && done) return;

        int randomIndex = new Random().nextInt((int) sold);

        Query query = firestore.collection("lucky_draw_tickets")
                .document(drawId)
                .collection("tickets")
                .limit(1)
                .offset(randomIndex);

        List<QueryDocumentSnapshot> docs =
                query.get().get().getDocuments();

        if (docs.isEmpty()) return;

        DocumentSnapshot winnerTicket = docs.get(0);

        String winnerUid = winnerTicket.getString("uid");

        /* 🎯 Save winner */

        drawRef.update(
                "winnerUid", winnerUid,
                "status", "CLOSED",
                "isCompleted", true
        );

        /* 🎁 Reward */

        long reward = drawSnap.getLong("rewardCoins");

        firestore.collection("users")
                .document(winnerUid)
                .update("coins", FieldValue.increment(reward));
    }
}