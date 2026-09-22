package com.example.backend.controller;

import com.example.backend.model.OfferModel;
import com.example.backend.service.ShareEarnService;
import com.example.backend.util.TokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ShareEarnController {

    @Autowired
    private ShareEarnService shareEarnService;

    @GetMapping("/offers")
    public ResponseEntity<?> getOffers(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "search", required = false) String search
    ) {
        try {
            List<OfferModel> offers = shareEarnService.getActiveOffers(category, search);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", offers);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildError("FETCH_OFFERS_FAILED", e.getMessage()));
        }
    }

    @GetMapping("/offers/{offerId}")
    public ResponseEntity<?> getOfferDetails(@PathVariable("offerId") String offerId) {
        try {
            OfferModel offer = shareEarnService.getOfferById(offerId);
            if (offer == null) {
                return ResponseEntity.status(404).body(buildError("OFFER_NOT_FOUND", "Offer not found: " + offerId));
            }
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", offer);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildError("FETCH_OFFER_FAILED", e.getMessage()));
        }
    }

    @PostMapping("/tracking/click")
    public ResponseEntity<?> createTrackingClick(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> req,
            HttpServletRequest httpRequest
    ) {
        try {
            String uid = TokenUtil.verify(token);
            String offerId = req.get("offerId") != null ? req.get("offerId").toString() : null;

            if (offerId == null || offerId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(buildError("INVALID_OFFER_ID", "offerId is required"));
            }

            String scheme = httpRequest.getScheme();
            String serverName = httpRequest.getServerName();
            int serverPort = httpRequest.getServerPort();

            String baseUrl;
            if ((scheme.equals("http") && serverPort == 80) || (scheme.equals("https") && serverPort == 443)) {
                baseUrl = scheme + "://" + serverName;
            } else {
                baseUrl = scheme + "://" + serverName + ":" + serverPort;
            }

            String ipAddress = extractClientIp(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");

            Map<String, Object> trackingResult = shareEarnService.createClickTracking(uid, offerId, baseUrl, ipAddress, userAgent);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", trackingResult);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(buildError("UNAUTHORIZED", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(buildError("BAD_REQUEST", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildError("TRACKING_FAILED", e.getMessage()));
        }
    }

    @PostMapping("/offers/{offerId}/claim")
    public ResponseEntity<?> submitOfferClaim(
            @RequestHeader("Authorization") String token,
            @PathVariable("offerId") String offerId,
            @RequestBody Map<String, Object> req
    ) {
        try {
            String uid = TokenUtil.verify(token);
            String clickId = req.get("clickId") != null ? req.get("clickId").toString() : null;
            String proofText = req.get("proofText") != null ? req.get("proofText").toString() : null;
            String referralCodeUsed = req.get("referralCodeUsed") != null ? req.get("referralCodeUsed").toString() : null;

            Map<String, Object> result = shareEarnService.submitOfferClaim(uid, offerId, clickId, proofText, referralCodeUsed);
            Boolean success = (Boolean) result.get("success");
            if (Boolean.FALSE.equals(success)) {
                return ResponseEntity.badRequest().body(result);
            }
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(buildError("UNAUTHORIZED", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(buildError("CLAIM_ERROR", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(buildError("CLAIM_FAILED", e.getMessage()));
        }
    }

    @GetMapping("/me/offers")
    public ResponseEntity<?> getMyOffers(
            @RequestHeader("Authorization") String token,
            @RequestParam(value = "status", required = false) String status
    ) {
        try {
            String uid = TokenUtil.verify(token);
            List<Map<String, Object>> history = shareEarnService.getUserOfferHistory(uid, status);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", history);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(buildError("UNAUTHORIZED", e.getMessage()));
        }
    }

    @GetMapping("/me/offer-history")
    public ResponseEntity<?> getMyOfferHistory(
            @RequestHeader("Authorization") String token,
            @RequestParam(value = "status", required = false) String status
    ) {
        return getMyOffers(token, status);
    }

    @GetMapping("/me/earnings")
    public ResponseEntity<?> getMyEarnings(@RequestHeader("Authorization") String token) {
        try {
            String uid = TokenUtil.verify(token);
            Map<String, Object> earnings = shareEarnService.getUserEarningsSummary(uid);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", earnings);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(buildError("UNAUTHORIZED", e.getMessage()));
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.trim().isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
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
