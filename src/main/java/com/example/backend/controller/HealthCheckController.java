package com.example.backend.controller;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HealthCheckController
 *
 * Provides lightweight health-check and app-connectivity-check endpoints.
 *
 * Endpoints:
 *   GET /live/health          — liveness probe (backend up + Firebase initialized check)
 *   GET /api/app-check        — full connectivity check (backend + Firestore ping)
 */
@RestController
@CrossOrigin(origins = "*")
public class HealthCheckController {

    @Autowired(required = false)
    private Firestore firestore;

    @Autowired(required = false)
    private FirebaseApp firebaseApp;

    // =========================================================
    // LIVENESS PROBE  —  GET /live/health
    // =========================================================
    // Fast check: Is the backend running AND is Firebase SDK initialized?
    // Used by Render's health-check pings to keep the instance warm.

    @GetMapping("/live/health")
    public ResponseEntity<Map<String, Object>> liveness() {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", Instant.now().toString());
        result.put("status", "UP");
        result.put("service", "DailyKash Backend");

        // Firebase SDK init check (no network call)
        boolean firebaseReady = (firebaseApp != null) && !FirebaseApp.getApps().isEmpty();
        result.put("firebase_sdk", firebaseReady ? "INITIALIZED" : "NOT_INITIALIZED");

        if (!firebaseReady) {
            result.put("status", "DEGRADED");
            return ResponseEntity.status(503).body(result);
        }

        return ResponseEntity.ok(result);
    }

    // =========================================================
    // FULL APP CHECK  —  GET /api/app-check
    // =========================================================
    // Called by the Android app on startup / before critical operations.
    // Performs a real Firestore read-ping to verify end-to-end connectivity.
    // Returns JSON with overall status and individual component statuses.

    @GetMapping("/api/app-check")
    public ResponseEntity<Map<String, Object>> appCheck() {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", Instant.now().toString());
        result.put("service", "DailyKash Backend");

        // --- 1. Backend liveness ---
        result.put("backend", "OK");

        // --- 2. Firebase SDK init ---
        boolean firebaseReady = (firebaseApp != null) && !FirebaseApp.getApps().isEmpty();
        result.put("firebase_sdk", firebaseReady ? "OK" : "NOT_INITIALIZED");

        // --- 3. Firestore connectivity ping ---
        String firestoreStatus;
        if (firestore == null) {
            firestoreStatus = "NOT_CONFIGURED";
        } else {
            try {
                // Lightweight ping: fetch a known-small document with a tight timeout.
                // Uses the "config/app" document (create it in Firestore with any field,
                // or it simply returns an empty doc — both count as a successful connection).
                firestore.collection("config")
                         .document("app")
                         .get()
                         .get(5, TimeUnit.SECONDS);   // 5-second deadline
                firestoreStatus = "OK";
            } catch (java.util.concurrent.TimeoutException te) {
                firestoreStatus = "TIMEOUT";
            } catch (Exception e) {
                firestoreStatus = "ERROR: " + sanitize(e.getMessage());
            }
        }
        result.put("firestore", firestoreStatus);

        // --- 4. Overall status ---
        boolean allOk = firebaseReady
                && "OK".equals(firestoreStatus)
                && "OK".equals(result.get("backend"));

        result.put("status", allOk ? "OK" : "DEGRADED");

        int httpStatus = allOk ? 200 : 503;
        return ResponseEntity.status(httpStatus).body(result);
    }

    // =========================================================
    // HELPERS
    // =========================================================

    /** Truncate and strip newlines from error messages to keep JSON clean. */
    private String sanitize(String msg) {
        if (msg == null) return "unknown";
        String clean = msg.replace("\n", " ").replace("\r", "");
        return clean.length() > 120 ? clean.substring(0, 120) + "…" : clean;
    }
}
