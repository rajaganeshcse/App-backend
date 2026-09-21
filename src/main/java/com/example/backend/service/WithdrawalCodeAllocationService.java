package com.example.backend.service;

import com.example.backend.model.NotificationSendRequest;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class WithdrawalCodeAllocationService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalCodeAllocationService.class);

    @Autowired
    private NotificationService notificationService;

    public static String normalizeMethod(String method) {
        if (method == null) return "";
        String m = method.trim().toUpperCase();
        if (m.equals("GOOGLE") || m.equals("GOOGLE_PLAY") || m.equals("GOOGLEPLAY")) return "GOOGLE_PLAY";
        if (m.equals("AMAZON") || m.equals("AMAZON_PAY")) return "AMAZON";
        if (m.equals("PHONEPE") || m.equals("PHONE_PE")) return "PHONEPE";
        if (m.equals("UPI")) return "UPI";
        if (m.equals("BANK")) return "BANK";
        return m;
    }

    public static boolean isCodeBasedMethod(String method) {
        String m = normalizeMethod(method);
        return m.equals("GOOGLE_PLAY") || m.equals("AMAZON") || m.equals("PHONEPE");
    }

    /**
     * Atomically find and allocate an AVAILABLE code for a code-based withdrawal request.
     * Returns the allocated code string if available, or null if no code is currently available.
     */
    public String findAndAllocateCode(String method, long amount, String uid, String requestId) {
        String normMethod = normalizeMethod(method);
        if (!isCodeBasedMethod(normMethod)) {
            return null;
        }

        Firestore db = FirestoreClient.getFirestore();

        try {
            // Find an AVAILABLE code for matching method and amount
            Query query = db.collection("withdrawal_codes")
                    .whereEqualTo("method", normMethod)
                    .whereEqualTo("amount", amount)
                    .whereEqualTo("status", "AVAILABLE")
                    .limit(1);

            List<QueryDocumentSnapshot> codeDocs = query.get().get().getDocuments();

            if (codeDocs.isEmpty()) {
                log.info("No AVAILABLE code found for method={}, amount={}", normMethod, amount);
                return null;
            }

            QueryDocumentSnapshot codeDoc = codeDocs.get(0);
            String codeDocId = codeDoc.getId();
            String codeValue = codeDoc.getString("code");

            // Perform atomic allocation update
            DocumentReference codeRef = db.collection("withdrawal_codes").document(codeDocId);

            Boolean success = db.runTransaction(transaction -> {
                DocumentSnapshot snap = transaction.get(codeRef).get();
                if (snap.exists() && "AVAILABLE".equalsIgnoreCase(snap.getString("status"))) {
                    transaction.update(codeRef,
                            "status", "ALLOCATED",
                            "allocatedTo", uid,
                            "withdrawalRequestId", requestId,
                            "allocatedAt", FieldValue.serverTimestamp()
                    );
                    return true;
                } else {
                    throw new IllegalStateException("Code was already allocated by another request");
                }
            }).get();

            log.info("Successfully allocated code={} to requestId={} for user={}", codeValue, requestId, uid);
            return codeValue;

        } catch (Exception e) {
            log.warn("Code allocation attempt failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Bulk add codes for a method & amount, then automatically allocate them to pending requests FIFO.
     */
    public Map<String, Object> bulkAddCodes(String method, long amount, List<String> codesInput) {
        String normMethod = normalizeMethod(method);
        if (!isCodeBasedMethod(normMethod)) {
            return Map.of("status", false, "message", "Method must be PHONEPE, AMAZON, or GOOGLE_PLAY");
        }

        if (codesInput == null || codesInput.isEmpty()) {
            return Map.of("status", false, "message", "No codes provided");
        }

        // Clean & validate input codes
        Set<String> uniqueCodes = new LinkedHashSet<>();
        for (String c : codesInput) {
            if (c != null) {
                String clean = c.trim();
                if (!clean.isEmpty()) {
                    uniqueCodes.add(clean);
                }
            }
        }

        if (uniqueCodes.isEmpty()) {
            return Map.of("status", false, "message", "No valid non-empty codes provided");
        }

        Firestore db = FirestoreClient.getFirestore();
        int addedCount = 0;
        int allocatedCount = 0;

        for (String codeStr : uniqueCodes) {
            try {
                // Check if code already exists
                Query existingCheck = db.collection("withdrawal_codes")
                        .whereEqualTo("method", normMethod)
                        .whereEqualTo("amount", amount)
                        .whereEqualTo("code", codeStr)
                        .limit(1);

                if (!existingCheck.get().get().isEmpty()) {
                    log.info("Skipping duplicate code={}", codeStr);
                    continue;
                }

                // Insert code as AVAILABLE
                DocumentReference codeRef = db.collection("withdrawal_codes").document();
                Map<String, Object> codeData = new HashMap<>();
                codeData.put("method", normMethod);
                codeData.put("amount", amount);
                codeData.put("code", codeStr);
                codeData.put("status", "AVAILABLE");
                codeData.put("createdAt", FieldValue.serverTimestamp());
                codeData.put("allocatedAt", null);
                codeData.put("allocatedTo", null);
                codeData.put("withdrawalRequestId", null);

                codeRef.set(codeData).get();
                addedCount++;

                // Automatically attempt FIFO allocation to oldest pending request
                boolean allocated = autoAllocateToPendingRequest(db, normMethod, amount, codeRef.getId(), codeStr);
                if (allocated) {
                    allocatedCount++;
                }

            } catch (Exception e) {
                log.error("Error processing code {}: {}", codeStr, e.getMessage());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", true);
        response.put("message", "Processed " + addedCount + " codes (" + allocatedCount + " automatically allocated to pending requests)");
        response.put("addedCount", addedCount);
        response.put("allocatedCount", allocatedCount);
        response.put("availableCount", addedCount - allocatedCount);
        return response;
    }

    /**
     * Auto allocate a newly added AVAILABLE code to the oldest pending request matching method + amount (FIFO).
     */
    private boolean autoAllocateToPendingRequest(Firestore db, String normMethod, long amount, String codeDocId, String codeStr) {
        try {
            // Fetch requests matching amount
            Query query = db.collection("redeem_requests")
                    .whereEqualTo("amount", amount)
                    .limit(50);

            List<QueryDocumentSnapshot> rawRequests = query.get().get().getDocuments();

            // Filter pending requests for matching method (case-insensitive status check)
            List<QueryDocumentSnapshot> pendingRequests = new ArrayList<>();
            for (QueryDocumentSnapshot doc : rawRequests) {
                String reqStatus = doc.getString("status");
                if (reqStatus != null && (reqStatus.equalsIgnoreCase("PENDING") || reqStatus.equalsIgnoreCase("PENDING_CODE"))) {
                    String reqType = normalizeMethod(doc.getString("type"));
                    if (normMethod.equals(reqType)) {
                        pendingRequests.add(doc);
                    }
                }
            }

            if (pendingRequests.isEmpty()) {
                log.info("No matching pending request found for method={}, amount={}", normMethod, amount);
                return false;
            }

            // Sort FIFO by created_at timestamp
            pendingRequests.sort((a, b) -> {
                com.google.cloud.Timestamp tA = a.getTimestamp("created_at");
                com.google.cloud.Timestamp tB = b.getTimestamp("created_at");
                long sA = tA != null ? tA.getSeconds() : 0L;
                long sB = tB != null ? tB.getSeconds() : 0L;
                return Long.compare(sA, sB);
            });

            for (QueryDocumentSnapshot reqDoc : pendingRequests) {
                String requestId = reqDoc.getId();
                String uid = reqDoc.getString("uid");

                DocumentReference codeRef = db.collection("withdrawal_codes").document(codeDocId);
                DocumentReference reqRef = db.collection("redeem_requests").document(requestId);

                Boolean success = db.runTransaction(transaction -> {
                    DocumentSnapshot cSnap = transaction.get(codeRef).get();
                    DocumentSnapshot rSnap = transaction.get(reqRef).get();

                    if (cSnap.exists() && "AVAILABLE".equalsIgnoreCase(cSnap.getString("status"))) {
                        String rStatus = rSnap.exists() ? rSnap.getString("status") : null;
                        if (rStatus != null && (rStatus.equalsIgnoreCase("PENDING") || rStatus.equalsIgnoreCase("PENDING_CODE"))) {

                            // Mark code as ALLOCATED
                            transaction.update(codeRef,
                                    "status", "ALLOCATED",
                                    "allocatedTo", uid,
                                    "withdrawalRequestId", requestId,
                                    "allocatedAt", FieldValue.serverTimestamp()
                            );

                            // Mark request as SUCCESS
                            transaction.update(reqRef,
                                    "status", "SUCCESS",
                                    "voucher_code", codeStr,
                                    "voucher_added_at", FieldValue.serverTimestamp()
                            );
                            return true;
                        }
                    }
                    return false;
                }).get();

                if (Boolean.TRUE.equals(success)) {
                    log.info("Auto-allocated code={} to FIFO pending request={}", codeStr, requestId);

                    // Send high-priority system push notification to user
                    if (uid != null && !uid.isEmpty()) {
                        try {
                            NotificationSendRequest notifReq = new NotificationSendRequest();
                            notifReq.setAudience("SPECIFIC_USER");
                            notifReq.setTargetUserId(uid);
                            notifReq.setTitle("Withdrawal Approved 🎉");
                            notifReq.setMessage("Your " + normMethod.replace("_", " ") + " voucher code is ready: " + codeStr);
                            notifReq.setNotificationType("WITHDRAWAL_APPROVED");
                            notifReq.setScreen("TRANSACTION_HISTORY");
                            notificationService.sendNotification(notifReq);
                            log.info("Push approval notification sent successfully for user={}", uid);
                        } catch (Exception notifErr) {
                            log.error("Failed to send push notification: {}", notifErr.getMessage(), notifErr);
                        }
                    }

                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Auto allocation error: {}", e.getMessage(), e);
        }
        return false;
    }
}
