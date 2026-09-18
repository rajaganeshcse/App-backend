package com.example.backend.job;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * DeleteAccountJob
 *
 * Runs once daily at 02:00 AM IST.
 *
 * Logic:
 *  1. Query all docs in "account_delete_requests" where status == "pending"
 *  2. For each doc, check if (now - requestedAt) >= 7 days
 *  3. If YES:
 *      a. Delete all sub-collections under users/{uid}  (coinDetails, etc.)
 *      b. Delete users/{uid} document
 *      c. Delete Firebase Auth user
 *      d. Update status to "completed" in account_delete_requests/{uid}
 */
@Component
public class DeleteAccountJob {

    private static final long SEVEN_DAYS_MS = TimeUnit.DAYS.toMillis(7);

    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Kolkata")
    public void processDeleteRequests() {

        System.out.println("[DeleteAccountJob] Starting daily account deletion check...");

        try {
            Firestore db = FirestoreClient.getFirestore();

            // 1. Query pending delete requests
            List<QueryDocumentSnapshot> pendingDocs = db
                    .collection("account_delete_requests")
                    .whereEqualTo("status", "pending")
                    .get()
                    .get()
                    .getDocuments();

            System.out.println("[DeleteAccountJob] Found " + pendingDocs.size() + " pending requests.");

            Date now = new Date();

            for (QueryDocumentSnapshot doc : pendingDocs) {

                String uid = doc.getString("userId");
                Timestamp requestedAt = doc.getTimestamp("requestedAt");

                if (uid == null || requestedAt == null) {
                    System.err.println("[DeleteAccountJob] Skipping doc with missing fields: " + doc.getId());
                    continue;
                }

                long requestedAtMs = requestedAt.toDate().getTime();
                long elapsedMs = now.getTime() - requestedAtMs;

                System.out.println("[DeleteAccountJob] uid=" + uid
                        + " | elapsed=" + TimeUnit.MILLISECONDS.toDays(elapsedMs) + " day(s)");

                // 2. Check if 7 days have passed
                if (elapsedMs >= SEVEN_DAYS_MS) {

                    System.out.println("[DeleteAccountJob] Deleting account for uid: " + uid);

                    try {
                        // 3a. Delete sub-collection: coinDetails
                        deleteSubCollection(db, "users", uid, "coinDetails");

                        // 3b. Mark account as Deleted BEFORE removing (safety for edge-case race)
                        try {
                            db.collection("users").document(uid)
                                    .update("account", "Deleted").get();
                        } catch (Exception ignored) {}

                        // 3c. Delete main user document
                        db.collection("users").document(uid).delete().get();
                        System.out.println("[DeleteAccountJob] Firestore user doc deleted: " + uid);

                        // 3d. Delete Firebase Auth user
                        try {
                            FirebaseAuth.getInstance().deleteUser(uid);
                            System.out.println("[DeleteAccountJob] Firebase Auth user deleted: " + uid);
                        } catch (Exception authEx) {
                            System.err.println("[DeleteAccountJob] Auth delete failed for uid=" + uid
                                    + ": " + authEx.getMessage());
                        }

                        // 3e. Mark request as completed
                        db.collection("account_delete_requests")
                                .document(uid)
                                .update("status", "completed",
                                        "deletedAt", com.google.cloud.firestore.FieldValue.serverTimestamp())
                                .get();

                        System.out.println("[DeleteAccountJob] Account deletion complete for uid: " + uid);

                    } catch (Exception deleteEx) {
                        System.err.println("[DeleteAccountJob] Error deleting uid=" + uid
                                + ": " + deleteEx.getMessage());
                    }

                } else {
                    long daysLeft = 7 - TimeUnit.MILLISECONDS.toDays(elapsedMs);
                    System.out.println("[DeleteAccountJob] uid=" + uid
                            + " — deletion in " + daysLeft + " more day(s).");
                }
            }

            System.out.println("[DeleteAccountJob] Daily check complete.");

        } catch (Exception e) {
            System.err.println("[DeleteAccountJob] Fatal error: " + e.getMessage());
        }
    }

    // ===========================================================
    // Helper: Delete all documents in a sub-collection
    // ===========================================================

    private void deleteSubCollection(Firestore db,
                                     String parentCollection,
                                     String parentDocId,
                                     String subCollection) {
        try {
            List<QueryDocumentSnapshot> subDocs = db
                    .collection(parentCollection)
                    .document(parentDocId)
                    .collection(subCollection)
                    .get()
                    .get()
                    .getDocuments();

            for (QueryDocumentSnapshot subDoc : subDocs) {
                subDoc.getReference().delete();
            }

            System.out.println("[DeleteAccountJob] Deleted sub-collection '"
                    + subCollection + "' for uid=" + parentDocId
                    + " (" + subDocs.size() + " docs)");

        } catch (Exception e) {
            System.err.println("[DeleteAccountJob] Failed to delete sub-collection '"
                    + subCollection + "' for uid=" + parentDocId
                    + ": " + e.getMessage());
        }
    }
}
