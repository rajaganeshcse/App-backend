package com.example.backend.controller;

import com.google.firebase.FirebaseApp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Basic liveness endpoints.
 * Full health + Firebase connectivity check → see HealthCheckController
 *   GET /live/health     — backend + Firebase SDK init check
 *   GET /api/app-check   — full Firestore ping
 */
@RestController
@RequestMapping("/live")
public class live {

    @Autowired
    private FirebaseApp firebaseApp;

    /** Simple string ping — used by internal keep-alive scripts. */
    @GetMapping("/1")
    public String live() {
        return "live";
    }

    /** Quick JSON status ping — returns 200 + tiny payload when backend is running. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> status() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "firebase", (firebaseApp != null) ? "INITIALIZED" : "NOT_INITIALIZED"
        ));
    }

    @GetMapping("/temp/reedem")
    public String reedem() {
        return "Ganesh";
    }

    @PostMapping("/hello")
    public String hello() {
        return "Hello";
    }
}

