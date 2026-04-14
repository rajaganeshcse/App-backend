package com.example.backend.job;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DailyReset {

    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Kolkata")
    public void reset() throws Exception {

        Firestore db = FirestoreClient.getFirestore();

        Iterable<DocumentReference> users =
                db.collection("users").listDocuments();

        for (DocumentReference ref : users) {

            ref.set(
                    Map.of("daily_ads_count", 0),
                    SetOptions.merge()
            );
        }

        System.out.println("Daily reset done");
    }
}