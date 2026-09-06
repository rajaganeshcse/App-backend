package com.example.backend.controller;

import com.example.backend.model.ScratchResponse;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scratch")
@CrossOrigin
public class ScratchController {

    private final ScratchService scratchService;

    public ScratchController(ScratchService scratchService) {
        this.scratchService = scratchService;
    }

    @PostMapping("/play/{userId}")
    public ScratchResponse playScratch(
            @PathVariable String userId) {

        return scratchService.playScratch(userId);
    }

    @GetMapping("/status/{userId}")
    public ScratchResponse getScratchStatus(
            @PathVariable String userId) {

        return scratchService.getScratchStatus(userId);
    }
}
