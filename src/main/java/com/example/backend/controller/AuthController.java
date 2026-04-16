package com.example.backend.controller;

import com.example.backend.model.LoginRequest;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    @PostMapping("/auth")
    public ResponseEntity<?> auth(@RequestBody LoginRequest request) {

        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(request.token);

            String uid = decoded.getUid();
            String name = decoded.getName();
            String email = decoded.getEmail();

            Firestore db = FirestoreClient.getFirestore();
            DocumentReference ref = db.collection("users").document(uid);

            DocumentSnapshot doc = ref.get().get();

            if (!doc.exists()) {

                Map<String, Object> user = new HashMap<>();

                user.put("uid", uid);
                user.put("name", name);
                user.put("email", email);

                // 🔐 MAIN BALANCE
                user.put("coins", 100);
                user.put("tickets", 10);

                user.put("created_at", FieldValue.serverTimestamp());

                // ✅ SAVE USER
                ref.set(user);

                // ✅ COIN HISTORY
                Map<String, Object> coinDetail = new HashMap<>();
                coinDetail.put("amount", 100);
                coinDetail.put("type", "welcome_bonus");
                coinDetail.put("created_at", FieldValue.serverTimestamp());

                ref.collection("coinDetails").add(coinDetail);

                // ✅ TICKET HISTORY
                Map<String, Object> ticketDetail = new HashMap<>();
                ticketDetail.put("amount", 10);
                ticketDetail.put("type", "welcome_bonus");
                ticketDetail.put("created_at", FieldValue.serverTimestamp());

                ref.collection("ticketDetails").add(ticketDetail);
            }

            return ResponseEntity.ok("Success");

        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid Token");
        }
    }
}