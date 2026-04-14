package com.example.backend.job;


import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
    public class DailyReset {

        @Scheduled(cron = "0 0 0 * * ?")
        public void reset() throws Exception {

            Firestore db = FirestoreClient.getFirestore();

            Iterable<DocumentReference> users =
                    db.collection("users").listDocuments();

            for (DocumentReference ref : users) {
                ref.update("daily_ads_count", 0);
            }

            System.out.println("Daily reset done");
        }
    }

