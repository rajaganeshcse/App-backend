package com.example.backend.controller;

import com.example.backend.model.ConversionModel;
import com.example.backend.model.OfferModel;
import com.example.backend.model.ShareEarnAuditLogModel;
import com.example.backend.model.TrackingClickModel;
import com.example.backend.service.ShareEarnAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class ShareEarnAdminController {

    @Autowired
    private ShareEarnAdminService adminService;

    @PostMapping("/offers")
    public ResponseEntity<?> createOffer(@RequestBody OfferModel offer) {
        try {
            if (offer.getTitle() == null || offer.getTitle().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(buildError("INVALID_TITLE", "Title is required"));
            }
            if (offer.getRewardCoins() <= 0) {
                return ResponseEntity.badRequest().body(buildError("INVALID_REWARD", "Reward coins must be greater than 0"));
            }
            Map<String, Object> result = adminService.createOffer(offer);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildError("CREATE_OFFER_FAILED", e.getMessage()));
        }
    }

    @PutMapping("/offers/{id}")
    public ResponseEntity<?> updateOffer(@PathVariable("id") String offerId, @RequestBody OfferModel offer) {
        try {
            Map<String, Object> result = adminService.updateOffer(offerId, offer);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(buildError("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildError("UPDATE_OFFER_FAILED", e.getMessage()));
        }
    }

    @PatchMapping("/offers/{id}/status")
    public ResponseEntity<?> updateOfferStatus(@PathVariable("id") String offerId, @RequestBody Map<String, Object> req) {
        try {
            String status = req.get("status") != null ? req.get("status").toString() : null;
            if (status == null || (!"ACTIVE".equalsIgnoreCase(status) && !"INACTIVE".equalsIgnoreCase(status))) {
                return ResponseEntity.badRequest().body(buildError("INVALID_STATUS", "Status must be ACTIVE or INACTIVE"));
            }
            Map<String, Object> result = adminService.updateOfferStatus(offerId, status);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildError("STATUS_UPDATE_FAILED", e.getMessage()));
        }
    }

    @GetMapping("/offers")
    public ResponseEntity<?> getAllOffers() {
        try {
            List<OfferModel> offers = adminService.getAllOffers();
            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("data", offers);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildError("FETCH_ADMIN_OFFERS_FAILED", e.getMessage()));
        }
    }

    @GetMapping("/clicks")
    public ResponseEntity<?> getTrackingClicks(@RequestParam(value = "limit", defaultValue = "200") int limit) {
        try {
            List<TrackingClickModel> clicks = adminService.getTrackingClicks(limit);
            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("data", clicks);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildError("FETCH_CLICKS_FAILED", e.getMessage()));
        }
    }

    @GetMapping("/conversions")
    public ResponseEntity<?> getConversions(@RequestParam(value = "status", required = false) String status) {
        try {
            List<ConversionModel> conversions = adminService.getConversions(status);
            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("data", conversions);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildError("FETCH_CONVERSIONS_FAILED", e.getMessage()));
        }
    }

    @PostMapping("/conversions/{id}/action")
    public ResponseEntity<?> handleConversionAction(
            @PathVariable("id") String conversionId,
            @RequestBody Map<String, Object> req
    ) {
        try {
            String action = req.get("action") != null ? req.get("action").toString() : null;
            String reason = req.get("reason") != null ? req.get("reason").toString() : null;

            if (action == null || action.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(buildError("INVALID_ACTION", "Action is required (APPROVE, REJECT, REVERSE)"));
            }

            Map<String, Object> result = adminService.handleConversionAction(conversionId, action, reason);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(buildError("ACTION_FAILED", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildError("CONVERSION_ACTION_FAILED", e.getMessage()));
        }
    }

    @GetMapping("/rewards")
    public ResponseEntity<?> getRewardsLog() {
        return getConversions("APPROVED");
    }

    @GetMapping("/reports")
    public ResponseEntity<?> getReports() {
        try {
            Map<String, Object> reports = adminService.getReports();
            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("data", reports);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildError("FETCH_REPORTS_FAILED", e.getMessage()));
        }
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<?> getAuditLogs() {
        try {
            List<ShareEarnAuditLogModel> logs = adminService.getAuditLogs();
            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("data", logs);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildError("FETCH_AUDIT_LOGS_FAILED", e.getMessage()));
        }
    }

    private Map<String, Object> buildError(String code, String message) {
        Map<String, Object> err = new HashMap<>();
        err.put("code", code);
        err.put("message", message);
        Map<String, Object> res = new HashMap<>();
        res.put("success", false);
        res.put("error", err);
        return res;
    }
}
