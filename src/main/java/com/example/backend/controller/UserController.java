package com.example.backend.controller;

import com.example.backend.util.TokenUtil;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class UserController {

    @GetMapping("/user")
    public ResponseEntity<?> getUser(
            @RequestHeader(value = "Authorization", required = false) String token) {

        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.status(401).body("Missing Authorization header");
        }

        try {
            String uid = TokenUtil.verify(token);

            Firestore db = FirestoreClient.getFirestore();
            DocumentSnapshot doc = db.collection("users").document(uid).get().get();

            if (!doc.exists()) {
                return ResponseEntity.status(404).body("User document not found");
            }

            String accountStatus = doc.getString("account");
            if ("Deleted".equals(accountStatus)) {
                return ResponseEntity.status(403).body("ACCOUNT_DELETED");
            }
            if ("Pending".equals(accountStatus)) {
                return ResponseEntity.status(403).body("ACCOUNT_PENDING");
            }

            return ResponseEntity.ok(doc.getData());

        } catch (IllegalArgumentException iae) {
            return ResponseEntity.status(400).body(iae.getMessage());
        } catch (Exception e) {
            System.err.println("❌ UserController /api/user error: " + e.getMessage());
            return ResponseEntity.status(401).body("Invalid or expired token");
        }
    }
}