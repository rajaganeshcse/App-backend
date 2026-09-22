package com.example.backend.controller;

import com.example.backend.service.AttributionService;
import com.example.backend.util.TokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/attribution")
@CrossOrigin(origins = "*")
public class AttributionController {

    @Autowired
    private AttributionService attributionService;

    @PostMapping("/install")
    public ResponseEntity<?> recordInstall(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, Object> req
    ) {
        try {
            String uid = null;
            if (token != null && !token.trim().isEmpty()) {
                try { uid = TokenUtil.verify(token); } catch (Exception ignored) {}
            }
            String clickId = req.get("clickId") != null ? req.get("clickId").toString().trim() : null;
            String referrer = req.get("referrer") != null ? req.get("referrer").toString() : null;
            String deviceId = req.get("deviceId") != null ? req.get("deviceId").toString() : null;
            String appVersion = req.get("appVersion") != null ? req.get("appVersion").toString() : null;
            return ResponseEntity.ok(attributionService.recordInstall(uid, clickId, referrer, deviceId, appVersion));
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", Map.of("code", "INSTALL_ATTRIBUTION_FAILED", "message", e.getMessage()));
            return ResponseEntity.status(500).body(err);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> recordRegistration(
            @RequestHeader("Authorization") String token,
            @RequestBody(required = false) Map<String, Object> req
    ) {
        try {
            String uid = TokenUtil.verify(token);
            String clickId = (req != null && req.get("clickId") != null) ? req.get("clickId").toString().trim() : null;
            return ResponseEntity.ok(attributionService.recordRegistration(uid, clickId));
        } catch (SecurityException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", Map.of("code", "UNAUTHORIZED", "message", e.getMessage()));
            return ResponseEntity.status(401).body(err);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", Map.of("code", "REGISTER_ATTRIBUTION_FAILED", "message", e.getMessage()));
            return ResponseEntity.status(500).body(err);
        }
    }
}
