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

    public void join(String drawId, String uid, String type) throws Exception {

        final String entryType = type == null ? "" : type.toUpperCase();

        DocumentReference userRef = db.collection("users").document(uid);
        DocumentReference drawRef = db.collection("lucky_draws").document(drawId);

        CollectionReference ticketRef =
                db.collection("lucky_draw_tickets")
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

        /* ================= TRANSACTION ================= */

        db.runTransaction(tx -> {

            DocumentSnapshot user = tx.get(userRef).get();
            DocumentSnapshot draw = tx.get(drawRef).get();

            long userTickets = Optional.ofNullable(user.getLong("tickets")).orElse(0L);
            long filled = Optional.ofNullable(draw.getLong("filledSlots")).orElse(0L);
            long total = Optional.ofNullable(draw.getLong("totalSlots")).orElse(0L);

            String status = draw.getString("status");

            System.out.println("ENTRY TYPE = " + entryType);
            System.out.println("USER TICKETS BEFORE = " + userTickets);

            if (!"OPEN".equalsIgnoreCase(status))
                throw new RuntimeException("Draw closed");

            /* ================= TICKET ENTRY ================= */

            if ("TICKET".equals(entryType)) {

                if (userTickets < 1)
                    throw new RuntimeException("Not enough tickets");

                System.out.println("Deducting 1 ticket...");

                tx.update(userRef, "tickets",
                        FieldValue.increment(-1));
            }

            else if (!"AD".equals(entryType)) {
                throw new RuntimeException("Invalid type");
            }

            /* ================= CHECK LIMIT ================= */

            if (filled + 1 > total)
                throw new RuntimeException("Draw full");

            /* ================= CREATE ENTRY ================= */

            long ticketNumber = filled + 1;

            String ticketId = UUID.randomUUID().toString();

            Map<String, Object> data = new HashMap<>();
            data.put("ticketId", ticketId);
            data.put("uid", uid);
            data.put("drawId", drawId);
            data.put("type", entryType);
            data.put("ticketNumber", ticketNumber);
            data.put("createdAt", FieldValue.serverTimestamp());

            tx.set(ticketRef.document(ticketId), data);

            tx.set(userRef.collection("myTickets")
                    .document(ticketId), data);

            tx.update(drawRef, "filledSlots",
                    FieldValue.increment(1));

            return null;

        }).get();

        /* ================= CHECK WINNER ================= */

        checkWinner(drawId);
    }

    /* ================= WINNER ================= */

    public void checkWinner(String drawId) throws Exception {

        DocumentReference drawRef =
                db.collection("lucky_draws").document(drawId);

        DocumentSnapshot draw = drawRef.get().get();

        long filled = Optional.ofNullable(draw.getLong("filledSlots")).orElse(0L);
        long total = Optional.ofNullable(draw.getLong("totalSlots")).orElse(0L);

        if (filled < total) return;

        Boolean done = draw.getBoolean("isCompleted");
        if (done != null && done) return;

        int index = new Random().nextInt((int) filled);

        Query query = db.collection("lucky_draw_tickets")
                .document(drawId)
                .collection("tickets")
                .orderBy("ticketNumber")
                .offset(index)
                .limit(1);

        List<QueryDocumentSnapshot> docs =
                query.get().get().getDocuments();

        if (docs.isEmpty()) return;

        DocumentSnapshot win = docs.get(0);

        String winnerUid = win.getString("uid");
        Long ticketNumber = win.getLong("ticketNumber");

        drawRef.update(
                "winnerUid", winnerUid,
                "winnerTicketNumber", ticketNumber,
                "status", "CLOSED",
                "isCompleted", true
        );

        long reward = Optional.ofNullable(draw.getLong("rewardCoins")).orElse(0L);

        db.collection("users")
                .document(winnerUid)
                .update("coins", FieldValue.increment(reward));
    }
}