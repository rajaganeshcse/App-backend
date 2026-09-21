package com.example.backend.controller;

import com.example.backend.service.withdrawService;
import com.example.backend.service.WithdrawalCodeAllocationService;
import com.example.backend.util.TokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class WithdrawController {

    private final withdrawService withdrawService;

    @Autowired
    private WithdrawalCodeAllocationService codeAllocationService;

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

            Long coinss = req.get("coins") != null
                    ? Long.parseLong(req.get("coins").toString())
                    : null;

            Long amount = req.get("amount") != null
                    ? Long.parseLong(req.get("amount").toString())
                    : null;

            String type = req.get("type") != null
                    ? req.get("type").toString()
                    : null;

            String details = req.get("details") != null
                    ? req.get("details").toString()
                    : null;

            if (amount == null || amount <= 0 || type == null || details == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", false,
                        "message", "Invalid or missing fields"
                ));
            }

            Map<String, Object> response =
                    withdrawService.createWithdrawRequest(uid, amount, type, details, coinss);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of(
                    "status", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/admin/withdrawal-codes/bulk")
    public ResponseEntity<?> bulkAddWithdrawalCodes(
            @RequestBody Map<String, Object> req
    ) {
        try {
            String method = req.get("method") != null ? req.get("method").toString() : null;
            Long amount = req.get("amount") != null ? Long.parseLong(req.get("amount").toString()) : null;

            List<String> codes = null;
            if (req.get("codes") instanceof List) {
                codes = (List<String>) req.get("codes");
            }

            if (method == null || amount == null || amount <= 0 || codes == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", false,
                        "message", "Missing required fields: method, amount, codes"
                ));
            }

            Map<String, Object> response = codeAllocationService.bulkAddCodes(method, amount, codes);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", false,
                    "message", e.getMessage()
            ));
        }
    }
}