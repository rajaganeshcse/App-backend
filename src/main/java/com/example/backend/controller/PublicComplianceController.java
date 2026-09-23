package com.example.backend.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@CrossOrigin(origins = "*")
public class PublicComplianceController {

    @GetMapping(value = "/app-ads.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getAppAdsTxt() {
        return ResponseEntity.ok("google.com, pub-6379296195597918, DIRECT, f08c47fec0942fa0\n");
    }

    @GetMapping(value = {"/privacy-policy", "/privacy", "/privacy-policy.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getPrivacyPolicy() throws IOException {
        Resource resource = new ClassPathResource("static/privacy-policy.html");
        String html = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok(html);
    }

    @GetMapping(value = {"/delete-account", "/account-deletion", "/delete-account.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getDeleteAccount() throws IOException {
        Resource resource = new ClassPathResource("static/delete-account.html");
        String html = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok(html);
    }
}
