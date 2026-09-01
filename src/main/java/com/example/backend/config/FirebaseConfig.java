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
        // CHECK REQUIRED VALUES
        // =====================================================

        if (projectId == null || projectId.isBlank()) {

            throw new RuntimeException(
                    "FIREBASE_PROJECT_ID is missing"
            );
        }

        if (clientEmail == null || clientEmail.isBlank()) {

            throw new RuntimeException(
                    "FIREBASE_CLIENT_EMAIL is missing"
            );
        }

        if (privateKey == null || privateKey.isBlank()) {

            throw new RuntimeException(
                    "FIREBASE_PRIVATE_KEY is missing"
            );
        }


        // =====================================================
        // FIX PRIVATE KEY NEWLINES
        // =====================================================

        privateKey =
                privateKey.replace("\\n", "\n");


        // =====================================================
        // CREATE SERVICE ACCOUNT JSON
        // =====================================================

        String json =
                "{\n" +
                        "  \"type\": \"service_account\",\n" +
                        "  \"project_id\": \"" +
                        escapeJson(projectId) +
                        "\",\n" +

                        "  \"private_key_id\": \"" +
                        escapeJson(
                                privateKeyId == null
                                        ? ""
                                        : privateKeyId
                        ) +
                        "\",\n" +

                        "  \"private_key\": \"" +
                        escapeJson(privateKey) +
                        "\",\n" +

                        "  \"client_email\": \"" +
                        escapeJson(clientEmail) +
                        "\",\n" +

                        "  \"client_id\": \"" +
                        escapeJson(
                                clientId == null
                                        ? ""
                                        : clientId
                        ) +
                        "\"\n" +

                        "}";


        // =====================================================
        // CREATE INPUT STREAM
        // =====================================================

        InputStream serviceAccount =
                new ByteArrayInputStream(
                        json.getBytes(
                                StandardCharsets.UTF_8
                        )
                );


        // =====================================================
        // FIREBASE OPTIONS
        // =====================================================

        FirebaseOptions options =
                FirebaseOptions.builder()
                        .setCredentials(
                                GoogleCredentials.fromStream(
                                        serviceAccount
                                )
                        )
                        .setProjectId(projectId)
                        .build();


        // =====================================================
        // INITIALIZE FIREBASE
        // =====================================================

        FirebaseApp app =
                FirebaseApp.initializeApp(options);


        System.out.println(
                "🔥 Firebase Connected"
        );


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