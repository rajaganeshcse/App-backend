package com.example.backend.controller;


import com.google.firebase.FirebaseApp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/live")
public class live {
    @Autowired
    private FirebaseApp firebaseApp;

    @GetMapping("/1")
    public String live() {
        return "live";
    }
    @GetMapping("/temp/reedem")
    public String reedem(){
        return "Ganesh";

    }
    @PostMapping("/hello")
    public String  hello(){
        return "Hello";

    }
}
