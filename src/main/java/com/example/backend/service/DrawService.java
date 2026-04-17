package com.example.backend.service;

import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DrawService {

    @Autowired
    private Firestore db;

    public void join(String drawId, String uid, String type) throws Exception {

        type = type.toUpperCase();

        DocumentReference userRef = db.collection("users").document(uid);
        DocumentReference drawRef = db.collection("lucky_draws").document(drawId);

        CollectionReference ticketRef =
                db.collection("lucky_draw_tickets")
                        .document(drawId)
                        .collection("tickets");

        /* ===== CHECK AD OUTSIDE TRANSACTION ===== */

        if ("AD".equals(type)) {

            Query q = ticketRef
                    .whereEqualTo("uid", uid)
                    .whereEqualTo("type", "AD")
                    .limit(1);

            if (!q.get().get().isEmpty()) {
                throw new RuntimeException("Free entry already used");
            }
        }

        db.runTransaction(tx -> {

            DocumentSnapshot user = tx.get(userRef).get();
            DocumentSnapshot draw = tx.get(drawRef).get();

            long userTickets = Optional.ofNullable(user.getLong("tickets")).orElse(0L);
            long sold = Optional.ofNullable(draw.getLong("soldTickets")).orElse(0L);
            long total = Optional.ofNullable(draw.getLong("totalTickets")).orElse(0L);

            String status = draw.getString("status");

            if (!"OPEN".equalsIgnoreCase(status))
                throw new RuntimeException("Draw closed");

            if ("TICKET".equals(type)) {

                if (userTickets < 1)
                    throw new RuntimeException("Not enough tickets");

                tx.update(userRef, "tickets",
                        FieldValue.increment(-1));
            }

            if (sold + 1 > total)
                throw new RuntimeException("Draw full");

            /* ===== SEQUENTIAL TICKET ===== */

            long ticketNumber = sold + 1;

            String ticketId = UUID.randomUUID().toString();

            Map<String, Object> data = new HashMap<>();
            data.put("ticketId", ticketId);
            data.put("uid", uid);
            data.put("drawId", drawId);
            data.put("type", type);
            data.put("ticketNumber", ticketNumber);
            data.put("createdAt", FieldValue.serverTimestamp());

            tx.set(ticketRef.document(ticketId), data);

            tx.set(userRef.collection("myTickets")
                    .document(ticketId), data);

            tx.update(drawRef, "soldTickets",
                    FieldValue.increment(1));

            return null;

        }).get();

        checkWinner(drawId);
    }

    public void checkWinner(String drawId) throws Exception {

        DocumentReference drawRef =
                db.collection("lucky_draws").document(drawId);

        DocumentSnapshot draw = drawRef.get().get();

        long sold = Optional.ofNullable(draw.getLong("soldTickets")).orElse(0L);
        long total = Optional.ofNullable(draw.getLong("totalTickets")).orElse(0L);

        if (sold < total) return;

        Boolean done = draw.getBoolean("isCompleted");
        if (done != null && done) return;

        int randomNumber = new Random().nextInt(1_000_000);

        Query query = db.collection("lucky_draw_tickets")
                .document(drawId)
                .collection("tickets")
                .orderBy("ticketNumber")
                .startAt(randomNumber)
                .limit(1);

        List<QueryDocumentSnapshot> docs =
                query.get().get().getDocuments();

        if (docs.isEmpty()) {
            docs = db.collection("lucky_draw_tickets")
                    .document(drawId)
                    .collection("tickets")
                    .orderBy("ticketNumber")
                    .limit(1)
                    .get().get().getDocuments();
        }

        if (docs.isEmpty()) return;

        DocumentSnapshot win = docs.get(0);

        String winnerUid = win.getString("uid");
        Long winNumber = win.getLong("ticketNumber");

        drawRef.update(
                "winnerUid", winnerUid,
                "winnerTicketNumber", winNumber,
                "status", "CLOSED",
                "isCompleted", true
        );

        long reward = Optional.ofNullable(draw.getLong("rewardCoins")).orElse(0L);

        db.collection("users")
                .document(winnerUid)
                .update("coins", FieldValue.increment(reward));
    }
}