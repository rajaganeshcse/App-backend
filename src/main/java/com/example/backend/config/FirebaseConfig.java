package com.example.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.firestore.Firestore;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    // =========================================================
    // FIREBASE APP
    // =========================================================

    @Bean
    public FirebaseApp firebaseApp() throws Exception {

        // If Firebase is already initialized,
        // return the existing FirebaseApp.
        if (!FirebaseApp.getApps().isEmpty()) {

            System.out.println("🔥 Firebase already initialized");

            return FirebaseApp.getInstance();
        }


        // =====================================================
        // GET RENDER ENVIRONMENT VARIABLES
        // =====================================================

        String projectId =
                System.getenv("FIREBASE_PROJECT_ID");

        String clientEmail =
                System.getenv("FIREBASE_CLIENT_EMAIL");

        String privateKey =
                System.getenv("FIREBASE_PRIVATE_KEY");

        String clientId =
                System.getenv("FIREBASE_CLIENT_ID");

        String privateKeyId =
                System.getenv("FIREBASE_PRIVATE_KEY_ID");


        // =====================================================
        // CHECK REQUIRED VALUES & BUILD CREDENTIALS
        // =====================================================

        GoogleCredentials credentials;

        if (clientEmail != null && !clientEmail.isBlank() && privateKey != null && !privateKey.isBlank()) {
            privateKey = privateKey.replace("\\n", "\n");
            String json =
                    "{\n" +
                            "  \"type\": \"service_account\",\n" +
                            "  \"project_id\": \"" + escapeJson(projectId != null ? projectId : "r-gamer-35915") + "\",\n" +
                            "  \"private_key_id\": \"" + escapeJson(privateKeyId == null ? "" : privateKeyId) + "\",\n" +
                            "  \"private_key\": \"" + escapeJson(privateKey) + "\",\n" +
                            "  \"client_email\": \"" + escapeJson(clientEmail) + "\",\n" +
                            "  \"client_id\": \"" + escapeJson(clientId == null ? "" : clientId) + "\"\n" +
                            "}";
            credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        } else {
            System.out.println("⚠️ FIREBASE_CLIENT_EMAIL / FIREBASE_PRIVATE_KEY missing in env. Attempting default credentials for project: " + (projectId != null ? projectId : "r-gamer-35915"));
            try {
                credentials = GoogleCredentials.getApplicationDefault();
            } catch (Exception e) {
                System.err.println("⚠️ Default GoogleCredentials not found: " + e.getMessage());
                credentials = GoogleCredentials.fromStream(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
            }
        }

        // =====================================================
        // FIREBASE OPTIONS
        // =====================================================

        FirebaseOptions options =
                FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .setProjectId(projectId != null && !projectId.isBlank() ? projectId : "r-gamer-35915")
                        .build();

        // =====================================================
        // INITIALIZE FIREBASE
        // =====================================================

        FirebaseApp app = FirebaseApp.initializeApp(options);

        System.out.println("🔥 Firebase Connected");


        // IMPORTANT:
        // Returning this makes FirebaseApp
        // a Spring Bean.
        return app;
    }


    // =========================================================
    // FIRESTORE BEAN
    // =========================================================

    @Bean
    public Firestore firestore(
            FirebaseApp firebaseApp
    ) {

        return FirestoreClient.getFirestore(
                firebaseApp
        );
    }


    // =========================================================
    // JSON ESCAPE
    // =========================================================

    private String escapeJson(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}