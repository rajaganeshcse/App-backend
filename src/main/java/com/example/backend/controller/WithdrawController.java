package com.example.backend.controller;

import com.example.backend.service.withdrawService;
import com.example.backend.util.TokenUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class WithdrawController {

    private final withdrawService withdrawService;

    public WithdrawController(withdrawService withdrawService) {
        this.withdrawService = withdrawService;
    }

    @PostMapping("/withdraw/request")
    public ResponseEntity<?> requestWithdraw(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> req
    ) {
        try {
            String uid = TokenUtil.verify(token);

            Long amount = req.get("amount") != null
                    ? Long.parseLong(req.get("amount").toString())
                    : null;

            String type = (String) req.get("type");
            String details = (String) req.get("details");

            if (amount == null || type == null || details == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("status", false, "message", "Missing fields"));
            }

            return ResponseEntity.ok(
                    withdrawService.createWithdrawRequest(uid, amount, type, details)
            );

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", false, "message", e.getMessage()));
        }
    }
}