package com.example.backend.controller;

import com.example.backend.service.DrawService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/admin/draw")
@CrossOrigin
public class DrawAdminController {

    @Autowired
    private DrawService service;

    /* ================= GET ADMIN CONFIG ================= */

    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        try {
            Map<String, Object> currentConfig = service.getAdminConfigInternal();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "config", currentConfig,
                    "presets", DrawService.SUPPORTED_PRESETS
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /* ================= UPDATE ADMIN CONFIG ================= */

    @RequestMapping(value = "/config", method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<?> updateConfig(@RequestBody Map<String, Object> req) {
        try {
            Long participationLimit = ((Number) req.get("participationLimit")).longValue();
            Long rewardCoins = ((Number) req.get("rewardCoins")).longValue();
            Long ticketCost = ((Number) req.get("ticketCost")).longValue();
            String updatedBy = (String) req.getOrDefault("updatedBy", "ADMIN");

            if (participationLimit <= 0 || rewardCoins <= 0 || ticketCost <= 0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Participation limit, reward coins, and ticket cost must be positive integers."
                ));
            }

            Map<String, Object> updatedConfig = service.updateAdminConfig(participationLimit, rewardCoins, ticketCost, updatedBy);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Lucky draw configuration updated successfully",
                    "config", updatedConfig
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /* ================= GET LIVE ACTIVE DRAW ================= */

    @GetMapping("/active")
    public ResponseEntity<?> getActiveDraw() {
        try {
            Map<String, Object> activeDraw = service.getOrCreateActiveDraw();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "draw", activeDraw
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /* ================= GET DRAW HISTORY ================= */

    @GetMapping("/history")
    public ResponseEntity<?> getHistory() {
        try {
            List<Map<String, Object>> history = service.getHistory();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "history", history
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
