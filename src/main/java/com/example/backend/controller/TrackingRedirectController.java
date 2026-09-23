package com.example.backend.controller;

import com.example.backend.service.ShareEarnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;

@Controller
@CrossOrigin(origins = "*")
public class TrackingRedirectController {

    @Autowired
    private ShareEarnService shareEarnService;

    /** Generic tracking redirect — canonical route: /r/{trackingId} */
    @GetMapping("/r/{trackingId}")
    public ResponseEntity<?> handleGenericRedirect(
            @PathVariable("trackingId") String trackingId,
            HttpServletRequest request) {
        return processRedirect(trackingId, request);
    }

    /** Legacy route kept for backward compatibility: /track/{clickId} */
    @GetMapping("/track/{clickId}")
    public ResponseEntity<?> handleTrackingRedirect(
            @PathVariable("clickId") String clickId,
            HttpServletRequest request) {
        return processRedirect(clickId, request);
    }

    private ResponseEntity<?> processRedirect(String clickId, HttpServletRequest request) {
        try {
            String ipAddress = extractClientIp(request);
            String userAgent = request.getHeader("User-Agent");
            String trustedDestinationUrl = shareEarnService.handleRedirect(clickId, ipAddress, userAgent);

            try {
                String sanitizedUrl = trustedDestinationUrl.trim().replace(" ", "%20");
                URI uri = URI.create(sanitizedUrl);
                HttpHeaders headers = new HttpHeaders();
                headers.setLocation(uri);
                return new ResponseEntity<>(headers, HttpStatus.FOUND);
            } catch (Exception uriEx) {
                // Robust Fallback: HTML + JavaScript Instant Redirect
                String html = "<!DOCTYPE html><html><head>"
                        + "<meta http-equiv=\"refresh\" content=\"0;url=" + trustedDestinationUrl + "\">"
                        + "<title>Redirecting...</title></head>"
                        + "<body style=\"font-family:system-ui,-apple-system,sans-serif;text-align:center;padding:60px 20px;background:#f8fafc;color:#1e293b;\">"
                        + "<div style=\"max-width:480px;margin:0 auto;background:#fff;padding:32px;border-radius:16px;box-shadow:0 4px 12px rgba(0,0,0,0.06);\">"
                        + "<h2 style=\"margin-top:0;color:#4f46e5;\">Redirecting to Offer...</h2>"
                        + "<p style=\"color:#64748b;\">If you are not redirected automatically within 3 seconds, please tap the button below:</p>"
                        + "<a href=\"" + trustedDestinationUrl + "\" style=\"display:inline-block;padding:12px 24px;background:#4f46e5;color:#fff;text-decoration:none;border-radius:8px;font-weight:600;margin-top:12px;\">Continue to Offer ➔</a>"
                        + "</div>"
                        + "<script>window.location.replace(\"" + trustedDestinationUrl.replace("\"", "\\\"") + "\");</script>"
                        + "</body></html>";
                HttpHeaders htmlHeaders = new HttpHeaders();
                htmlHeaders.set("Content-Type", "text/html; charset=UTF-8");
                return new ResponseEntity<>(html, htmlHeaders, HttpStatus.OK);
            }

        } catch (IllegalArgumentException e) {
            String errHtml = "<!DOCTYPE html><html><head><title>Link Error</title></head>"
                    + "<body style=\"font-family:system-ui,-apple-system,sans-serif;text-align:center;padding:60px 20px;background:#0f172a;color:#f8fafc;\">"
                    + "<div style=\"max-width:440px;margin:0 auto;background:#1e293b;padding:32px;border-radius:16px;border:1px solid #334155;\">"
                    + "<div style=\"font-size:40px;\">⚠️</div>"
                    + "<h2 style=\"color:#f87171;margin:12px 0;\">Link Notice</h2>"
                    + "<p style=\"color:#94a3b8;line-height:1.5;\">" + e.getMessage() + "</p>"
                    + "<p style=\"font-size:12px;color:#64748b;margin-top:20px;\">DailyKash Attribution System</p>"
                    + "</div></body></html>";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "text/html; charset=UTF-8");
            return new ResponseEntity<>(errHtml, headers, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            String errHtml = "<!DOCTYPE html><html><head><title>Server Notice</title></head>"
                    + "<body style=\"font-family:system-ui,-apple-system,sans-serif;text-align:center;padding:60px 20px;background:#0f172a;color:#f8fafc;\">"
                    + "<div style=\"max-width:440px;margin:0 auto;background:#1e293b;padding:32px;border-radius:16px;border:1px solid #334155;\">"
                    + "<div style=\"font-size:40px;\">⏳</div>"
                    + "<h2 style=\"color:#38bdf8;margin:12px 0;\">Service Notice</h2>"
                    + "<p style=\"color:#94a3b8;line-height:1.5;\">Unable to process redirect at this moment. Please try again shortly.</p>"
                    + "</div></body></html>";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "text/html; charset=UTF-8");
            return new ResponseEntity<>(errHtml, headers, HttpStatus.INTERNAL_SERVER_ERROR);
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
}
