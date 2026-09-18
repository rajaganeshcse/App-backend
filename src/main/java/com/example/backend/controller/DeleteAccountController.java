package com.example.backend.controller;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
@CrossOrigin
public class DeleteAccountController {

    // ===========================================================
    // POST /api/account/delete-request
    // Body: { "token": "<firebase-id-token>" }
    //
    // Stores a pending delete request in Firestore:
    // Collection: account_delete_requests
    // Document:   {userId}
    // Fields:     userId, status="pending", requestedAt (server timestamp)
    // ===========================================================

    @PostMapping("/delete-request")
    public ResponseEntity<?> requestDelete(@RequestBody Map<String, String> body) {

        String idToken = body.get("token");

        if (idToken == null || idToken.isEmpty()) {
            return ResponseEntity.status(400).body("Missing token");
        }

        try {
            // 1. Verify the Firebase ID token
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
            String uid = decoded.getUid();

            Firestore db = FirestoreClient.getFirestore();

            // 2. Check if a pending request already exists
            DocumentSnapshot existing = db.collection("account_delete_requests")
                    .document(uid)
                    .get()
                    .get();

            if (existing.exists()) {
                String status = existing.getString("status");
                if ("pending".equals(status)) {
                    return ResponseEntity.ok("Already pending");
                }
            }

            // 3. Write the delete request document
            Map<String, Object> request = new HashMap<>();
            request.put("userId", uid);
            request.put("status", "pending");
            request.put("requestedAt", FieldValue.serverTimestamp());

            db.collection("account_delete_requests")
                    .document(uid)
                    .set(request);

            System.out.println("[DeleteAccountController] Delete request submitted for uid: " + uid);

            return ResponseEntity.ok("Delete request submitted");

        } catch (Exception e) {
            System.err.println("[DeleteAccountController] Error: " + e.getMessage());
            return ResponseEntity.status(401).body("Unauthorized: " + e.getMessage());
        }
    }

    // ===========================================================
    // POST /api/account/cancel-delete
    // Body: { "token": "<firebase-id-token>" }
    //
    // Cancels a pending delete request (status -> "cancelled")
    // ===========================================================

    @PostMapping("/cancel-delete")
    public ResponseEntity<?> cancelDelete(@RequestBody Map<String, String> body) {

        String idToken = body.get("token");

        if (idToken == null || idToken.isEmpty()) {
            return ResponseEntity.status(400).body("Missing token");
        }

        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
            String uid = decoded.getUid();

            Firestore db = FirestoreClient.getFirestore();

            DocumentSnapshot existing = db.collection("account_delete_requests")
                    .document(uid)
                    .get()
                    .get();

            if (!existing.exists()) {
                return ResponseEntity.status(404).body("No delete request found");
            }

            Map<String, Object> update = new HashMap<>();
            update.put("status", "cancelled");
            update.put("cancelledAt", FieldValue.serverTimestamp());

            db.collection("account_delete_requests")
                    .document(uid)
                    .update(update);

            System.out.println("[DeleteAccountController] Delete request cancelled for uid: " + uid);

            return ResponseEntity.ok("Delete request cancelled");

        } catch (Exception e) {
            return ResponseEntity.status(401).body("Unauthorized: " + e.getMessage());
        }
    }
}
