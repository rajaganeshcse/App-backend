package com.example.backend.controller;

import com.example.backend.service.DrawService;
import com.example.backend.util.TokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/draw")
public class DrawController {

    @Autowired
    private DrawService service;

    /* ================= ENTER DRAW ================= */

    @PostMapping("/join")
    public ResponseEntity<?> join(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> req) {

        try {
            String uid = TokenUtil.verify(token);

            String drawId = (String) req.get("drawId");
            String type = (String) req.get("type"); // AD / TICKET

            if (drawId == null || type == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "errorCode", "INVALID_INPUT", "message", "Invalid drawId or type"));
            }

            Integer countObj = null;
            if (req.get("count") != null) {
                countObj = ((Number) req.get("count")).intValue();
            } else if (req.get("ticketCount") != null) {
                countObj = ((Number) req.get("ticketCount")).intValue();
            } else if (req.get("quantity") != null) {
                countObj = ((Number) req.get("quantity")).intValue();
            }
            int count = (countObj != null && countObj > 0) ? countObj : 1;

            Map<String, Object> result = service.join(drawId, uid, type, count);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Join failed";
            String code = "JOIN_FAILED";

            if (msg.contains("Not enough tickets")) {
                code = "INSUFFICIENT_TICKETS";
            } else if (msg.contains("not remaining") || msg.contains("full")) {
                code = "DRAW_FULL";
            } else if (msg.contains("Draw closed")) {
                code = "DRAW_CLOSED";
            } else if (msg.contains("Free entry")) {
                code = "FREE_ENTRY_USED";
            }

            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "errorCode", code, "message", msg));
        }
    }

    /* ================= DRAW STATUS ================= */

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam("drawId") String drawId) {

        try {
            Map<String, Object> status = service.getDrawStatus(drawId);
            status.put("success", true);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "errorCode", "DRAW_NOT_FOUND", "message", e.getMessage()));
        }
    }

    /* ================= DRAW WINNER ================= */

    @GetMapping("/winner")
    public ResponseEntity<?> getWinner(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam("drawId") String drawId) {

        try {
            Map<String, Object> winner = service.getWinner(drawId);
            winner.put("success", true);
            return ResponseEntity.ok(winner);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "errorCode", "WINNER_NOT_FOUND", "message", e.getMessage()));
        }
    }

    /* ================= DRAW HISTORY ================= */

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(
            @RequestHeader(value = "Authorization", required = false) String token) {

        try {
            List<Map<String, Object>> history = service.getHistory();
            return ResponseEntity.ok(Map.of("success", true, "history", history));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "errorCode", "HISTORY_FAILED", "message", e.getMessage()));
        }
    }
}