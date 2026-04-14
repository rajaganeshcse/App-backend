package com.example.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void init() throws Exception {

        String projectId = System.getenv("FIREBASE_PROJECT_ID");
        String clientEmail = System.getenv("FIREBASE_CLIENT_EMAIL");
        String privateKey = System.getenv("FIREBASE_PRIVATE_KEY");
        String clientId = System.getenv("FIREBASE_CLIENT_ID");
        String privateKeyId = System.getenv("FIREBASE_PRIVATE_KEY_ID");

        String json = "{\n" +
                "  \"type\": \"service_account\",\n" +
                "  \"project_id\": \"" + projectId + "\",\n" +
                "  \"private_key_id\": \"" + privateKeyId + "\",\n" +
                "  \"private_key\": \"" + privateKey + "\",\n" +
                "  \"client_email\": \"" + clientEmail + "\",\n" +
                "  \"client_id\": \"" + clientId + "\"\n" +
                "}";

        InputStream serviceAccount = new ByteArrayInputStream(json.getBytes());

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }

        System.out.println("🔥 Firebase Connected");
    }
}