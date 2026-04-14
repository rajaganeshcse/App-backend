package com.example.backend.controller;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class UserController {

    @GetMapping("/user")
    public ResponseEntity<?> getUser(
            @RequestHeader("Authorization") String token) throws Exception {

        FirebaseToken decoded =
                FirebaseAuth.getInstance().verifyIdToken(token);

        String uid = decoded.getUid();

        Firestore db = FirestoreClient.getFirestore();

        DocumentSnapshot doc =
                db.collection("users").document(uid).get().get();

        return ResponseEntity.ok(doc.getData());
    }
}