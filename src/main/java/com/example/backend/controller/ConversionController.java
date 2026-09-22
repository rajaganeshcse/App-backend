package com.example.backend.controller;

import com.example.backend.service.ShareEarnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ConversionController {

    @Autowired
    private ShareEarnService shareEarnService;

    @PostMapping("/conversions")
    public ResponseEntity<?> postConversion(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader,
            @RequestHeader(value = "X-Signature", required = false) String sigHeader,
            @RequestBody Map<String, Object> req
    ) {
        try {
            String clickId = req.get("clickId") != null ? req.get("clickId").toString() : null;
            String event = req.get("event") != null ? req.get("event").toString() : null;
            String externalReference = req.get("externalReference") != null ? req.get("externalReference").toString() : null;
            String apiKey = req.get("apiKey") != null ? req.get("apiKey").toString() : apiKeyHeader;
            String signature = req.get("signature") != null ? req.get("signature").toString() : sigHeader;

            Map<String, Object> result = shareEarnService.processConversion(
                    clickId,
                    event,
                    externalReference,
                    signature,
                    apiKey
            );

            Boolean success = (Boolean) result.get("success");
            if (Boolean.TRUE.equals(success)) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }

        } catch (SecurityException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", Map.of("code", "UNAUTHORIZED_CONVERSION", "message", e.getMessage()));
            return ResponseEntity.status(401).body(err);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", Map.of("code", "BAD_REQUEST", "message", e.getMessage()));
            return ResponseEntity.badRequest().body(err);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", Map.of("code", "CONVERSION_FAILED", "message", e.getMessage()));
            return ResponseEntity.status(500).body(err);
        }
    }
}
