package com.example.backend.service;

import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DrawService {

    @Autowired
    private Firestore db;

    /* ================= JOIN DRAW ================= */

    public void join(String drawId, String uid, int ticketCount) throws Exception {

        DocumentReference userRef = db.collection("users").document(uid);
        DocumentReference drawRef = db.collection("lucky_draws").document(drawId);

        CollectionReference ticketRef =
                db.collection("lucky_draw_tickets")
                        .document(drawId)
                        .collection("tickets");

        db.runTransaction(tx -> {

            DocumentSnapshot user = tx.get(userRef);
            DocumentSnapshot draw = tx.get(drawRef);

            Long ticketsObj = user.getLong("tickets");
            Long soldObj = draw.getLong("soldTickets");
            Long totalObj = draw.getLong("totalTickets");

            long tickets = ticketsObj != null ? ticketsObj : 0;
            long sold = soldObj != null ? soldObj : 0;
            long total = totalObj != null ? totalObj : 0;

            String status = draw.getString("status");

            /* 🚫 Anti-cheat */

            if (!"OPEN".equals(status))
                throw new RuntimeException("Draw closed");

            if (tickets < ticketCount)
                throw new RuntimeException("Not enough tickets");

            if (sold + ticketCount > total)
                throw new RuntimeException("Draw full");

            /* 🎟 CREATE TICKETS */

            for (int i = 0; i < ticketCount; i++) {

                String ticketId = UUID.randomUUID().toString();

                Map<String, Object> data = new HashMap<>();
                data.put("ticketId", ticketId);
                data.put("uid", uid);
                data.put("drawId", drawId);
                data.put("createdAt", FieldValue.serverTimestamp());

                tx.set(ticketRef.document(ticketId), data);

                // Optional user history
                tx.set(userRef.collection("myTickets").document(ticketId), data);
            }

            /* 💸 Deduct tickets */
            tx.update(userRef, "tickets", FieldValue.increment(-ticketCount));

            long newSold = sold + ticketCount;

            /* 📊 Update draw */
            tx.update(drawRef, "soldTickets", newSold);

            return newSold;

        }).get();

        /* 🎯 AUTO WINNER */
        checkWinner(drawId);
    }

    /* ================= WINNER ================= */

    public void checkWinner(String drawId) throws Exception {

        DocumentReference drawRef =
                db.collection("lucky_draws").document(drawId);

        DocumentSnapshot draw = drawRef.get().get();

        Long soldObj = draw.getLong("soldTickets");
        Long totalObj = draw.getLong("totalTickets");

        long sold = soldObj != null ? soldObj : 0;
        long total = totalObj != null ? totalObj : 0;

        if (sold < total) return;

        Boolean done = draw.getBoolean("isCompleted");
        if (done != null && done) return;

        int index = new Random().nextInt((int) sold);

        Query query = db.collection("lucky_draw_tickets")
                .document(drawId)
                .collection("tickets")
                .limit(1)
                .offset(index);

        List<QueryDocumentSnapshot> docs =
                query.get().get().getDocuments();

        if (docs.isEmpty()) return;

        DocumentSnapshot win = docs.get(0);

        String winnerUid = win.getString("uid");
        String ticketId = win.getString("ticketId");

        drawRef.update(
                "winnerUid", winnerUid,
                "winnerTicketId", ticketId,
                "status", "CLOSED",
                "isCompleted", true
        );

        Long rewardObj = draw.getLong("rewardCoins");
        long reward = rewardObj != null ? rewardObj : 0;

        db.collection("users")
                .document(winnerUid)
                .update("coins", FieldValue.increment(reward));
    }
}