package com.example.backend.controller;

import com.example.backend.service.DrawService;
import com.example.backend.util.TokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/draw")
public class DrawController {

    @Autowired
    private DrawService service;

    @PostMapping("/join")
    public ResponseEntity<?> join(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> req) {

        try {

            String uid = TokenUtil.verify(token);

            String drawId = (String) req.get("drawId");
            int ticketCount = (int) req.get("ticketCount");

            service.join(drawId, uid, ticketCount);

            return ResponseEntity.ok(Map.of("message", "Joined"));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}