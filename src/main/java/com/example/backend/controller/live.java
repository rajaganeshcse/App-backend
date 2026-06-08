package com.example.backend.controller;


import com.google.firebase.FirebaseApp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.PublicKey;

@RestController
@RequestMapping("/live")
public class live {
    @Autowired
    private FirebaseApp firebaseApp;

    @GetMapping("/1")
    public String live() {
        return "live";
    }
    @GetMapping("temp/reedem")
    public String reedem(){
        return "Ganesh";
    }
}
