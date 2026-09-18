package com.example.backend.controller;

import com.example.backend.model.ScratchResponse;
import com.example.backend.util.TokenUtil;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/scratch")
@CrossOrigin
public class ScratchController {

    private final ScratchService scratchService;

    public ScratchController(ScratchService scratchService) {
        this.scratchService = scratchService;
    }

    @PostMapping("/play")
    public ResponseEntity<?> playScratch(@RequestHeader("Authorization") String token) {
        try {
            String userId = TokenUtil.verify(token);
            ScratchResponse response = scratchService.playScratch(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or missing authorization token"));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> getScratchStatus(@RequestHeader("Authorization") String token) {
        try {
            String userId = TokenUtil.verify(token);
            ScratchResponse response = scratchService.getScratchStatus(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or missing authorization token"));
        }
    }
}
