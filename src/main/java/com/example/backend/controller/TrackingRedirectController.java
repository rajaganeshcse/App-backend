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

import java.net.URI;

@Controller
@CrossOrigin(origins = "*")
public class TrackingRedirectController {

    @Autowired
    private ShareEarnService shareEarnService;

    @GetMapping("/track/{clickId}")
    public ResponseEntity<?> handleTrackingRedirect(@PathVariable("clickId") String clickId) {
        try {
            String trustedDestinationUrl = shareEarnService.handleRedirect(clickId);

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(trustedDestinationUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND); // 302 Redirect

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("<html><body><h2>Link Error</h2><p>" + e.getMessage() + "</p></body></html>");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("<html><body><h2>Server Error</h2><p>Unable to process tracking redirect.</p></body></html>");
        }
    }
}
