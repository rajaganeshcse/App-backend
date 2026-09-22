package com.example.backend.service;

import com.example.backend.model.*;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
public class ShareEarnAdminService {

    @Autowired(required = false)
    private NotificationService notificationService;

    public Map<String, Object> createOffer(OfferModel offer) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        DocumentReference docRef = db.collection("offers").document();
        String offerId = "OFFER_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        offer.setOfferId(offerId);

        long now = System.currentTimeMillis();
        offer.setCreatedAt(now);
        offer.setUpdatedAt(now);
        if (offer.getStatus() == null) {
            offer.setStatus("ACTIVE");
        }

        db.collection("offers").document(offerId).set(offer).get();

        logAudit("ADMIN", "CREATE_OFFER", "OFFER", offerId, null, offer.getTitle() + " (" + offer.getRewardCoins() + " coins)");

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("offerId", offerId);
        res.put("message", "Offer created successfully");
        return res;
    }

    public Map<String, Object> updateOffer(String offerId, OfferModel offer) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        DocumentReference docRef = db.collection("offers").document(offerId);
        DocumentSnapshot snap = docRef.get().get();

        if (!snap.exists()) {
            throw new IllegalArgumentException("Offer not found: " + offerId);
        }

        offer.setOfferId(offerId);
        offer.setUpdatedAt(System.currentTimeMillis());

        docRef.set(offer, SetOptions.merge()).get();

        logAudit("ADMIN", "UPDATE_OFFER", "OFFER", offerId, null, offer.getTitle());

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("offerId", offerId);
        res.put("message", "Offer updated successfully");
        return res;
    }

    public Map<String, Object> updateOfferStatus(String offerId, String status) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        DocumentReference docRef = db.collection("offers").document(offerId);
        DocumentSnapshot snap = docRef.get().get();

        if (!snap.exists()) {
            throw new IllegalArgumentException("Offer not found: " + offerId);
        }

        String oldStatus = snap.getString("status");
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status.toUpperCase());
        updates.put("updatedAt", System.currentTimeMillis());

        docRef.update(updates).get();

        logAudit("ADMIN", "UPDATE_OFFER_STATUS", "OFFER", offerId, oldStatus, status.toUpperCase());

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("offerId", offerId);
        res.put("status", status.toUpperCase());
        res.put("message", "Offer status changed to " + status);
        return res;
    }

    public List<OfferModel> getAllOffers() throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        List<QueryDocumentSnapshot> docs = db.collection("offers").get().get().getDocuments();
        List<OfferModel> list = new ArrayList<>();

        for (QueryDocumentSnapshot doc : docs) {
            OfferModel offer = doc.toObject(OfferModel.class);
            if (offer.getOfferId() == null) offer.setOfferId(doc.getId());
            list.add(offer);
        }
        list.sort((a, b) -> Long.compare(b.getCreatedAt() != null ? b.getCreatedAt() : 0, a.getCreatedAt() != null ? a.getCreatedAt() : 0));
        return list;
    }

    public List<TrackingClickModel> getTrackingClicks(int limit) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        Query query = db.collection("tracking_clicks").orderBy("createdAt", Query.Direction.DESCENDING).limit(limit > 0 ? limit : 200);
        List<QueryDocumentSnapshot> docs = query.get().get().getDocuments();
        List<TrackingClickModel> clicks = new ArrayList<>();
        for (QueryDocumentSnapshot doc : docs) {
            clicks.add(doc.toObject(TrackingClickModel.class));
        }
        return clicks;
    }

    public List<ConversionModel> getConversions(String statusFilter) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        Query query = db.collection("conversions");
        if (statusFilter != null && !statusFilter.isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
            query = query.whereEqualTo("status", statusFilter.toUpperCase());
        }

        List<QueryDocumentSnapshot> docs = query.get().get().getDocuments();
        List<ConversionModel> list = new ArrayList<>();
        for (QueryDocumentSnapshot doc : docs) {
            list.add(doc.toObject(ConversionModel.class));
        }
        list.sort((a, b) -> Long.compare(b.getCreatedAt() != null ? b.getCreatedAt() : 0, a.getCreatedAt() != null ? a.getCreatedAt() : 0));
        return list;
    }

    public Map<String, Object> handleConversionAction(String conversionId, String action, String reason) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        DocumentReference convRef = db.collection("conversions").document(conversionId);
        DocumentSnapshot convSnap = convRef.get().get();

        if (!convSnap.exists()) {
            throw new IllegalArgumentException("Conversion not found: " + conversionId);
        }

        ConversionModel conv = convSnap.toObject(ConversionModel.class);
        if (conv == null) {
            throw new IllegalArgumentException("Invalid conversion document");
        }

        String currentStatus = conv.getStatus();
        String targetAction = action.toUpperCase();

        if ("APPROVE".equals(targetAction)) {
            if ("APPROVED".equalsIgnoreCase(currentStatus)) {
                throw new IllegalStateException("Conversion is already approved");
            }

            long rewardCoins = conv.getRewardCoins();
            String uid = conv.getUserId();
            DocumentReference userRef = db.collection("users").document(uid);

            return db.runTransaction(transaction -> {
                DocumentSnapshot userSnap = transaction.get(userRef).get();
                if (!userSnap.exists()) {
                    throw new IllegalStateException("User not found: " + uid);
                }

                Long coinsObj = userSnap.getLong("coins");
                long coins = coinsObj != null ? coinsObj : 0;
                long updatedCoins = coins + rewardCoins;

                transaction.update(userRef, "coins", updatedCoins);
                transaction.update(convRef, "status", "APPROVED");
                transaction.update(convRef, "approvedAt", System.currentTimeMillis());

                DocumentReference coinDetailRef = db.collection("users").document(uid).collection("coinDetails").document();
                Map<String, Object> coinDetail = new HashMap<>();
                coinDetail.put("amount", rewardCoins);
                coinDetail.put("type", "Share & Earn Reward Approved");
                coinDetail.put("status", "Credited");
                coinDetail.put("istype", "coin");
                coinDetail.put("source", "SHARE_EARN");
                coinDetail.put("sourceId", conversionId);
                coinDetail.put("offerId", conv.getOfferId());
                coinDetail.put("created_at", FieldValue.serverTimestamp());
                transaction.set(coinDetailRef, coinDetail);

                if (conv.getClickId() != null && !conv.getClickId().isEmpty()) {
                    DocumentReference clickRef = db.collection("tracking_clicks").document(conv.getClickId());
                    transaction.update(clickRef, "status", "CONVERTED");
                    transaction.update(clickRef, "conversionId", conversionId);
                }

                // FCM Push Notification
                String fcmToken = userSnap.getString("fcmToken");
                if (fcmToken != null && !fcmToken.isEmpty() && notificationService != null) {
                    try {
                        notificationService.send(
                                fcmToken,
                                "Reward Approved! 🎉",
                                "Your claim has been approved! " + rewardCoins + " coins added to your wallet.",
                                "+" + rewardCoins + " Coins"
                        );
                    } catch (Exception ignored) {}
                }

                logAudit("ADMIN", "APPROVE_CONVERSION", "CONVERSION", conversionId, currentStatus, "APPROVED");

                Map<String, Object> res = new HashMap<>();
                res.put("success", true);
                res.put("conversionId", conversionId);
                res.put("status", "APPROVED");
                res.put("rewardCoins", rewardCoins);
                res.put("updatedCoins", updatedCoins);
                return res;
            }).get();

        } else if ("REJECT".equals(targetAction)) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "REJECTED");
            updates.put("rejectedAt", System.currentTimeMillis());
            updates.put("rejectionReason", reason != null ? reason : "Rejected by Admin");
            convRef.update(updates).get();

            if (conv.getClickId() != null && !conv.getClickId().isEmpty()) {
                try {
                    db.collection("tracking_clicks").document(conv.getClickId()).update("status", "REJECTED");
                } catch (Exception ignored) {}
            }

            // Optional FCM notification for rejection
            try {
                DocumentSnapshot userSnap = db.collection("users").document(conv.getUserId()).get().get();
                if (userSnap.exists()) {
                    String fcmToken = userSnap.getString("fcmToken");
                    if (fcmToken != null && !fcmToken.isEmpty() && notificationService != null) {
                        notificationService.send(
                                fcmToken,
                                "Offer Claim Update",
                                "Your claim was not approved: " + (reason != null ? reason : "Verification incomplete"),
                                "Claim Rejected"
                        );
                    }
                }
            } catch (Exception ignored) {}

            logAudit("ADMIN", "REJECT_CONVERSION", "CONVERSION", conversionId, currentStatus, "REJECTED");

            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("conversionId", conversionId);
            res.put("status", "REJECTED");
            return res;

        } else if ("REVERSE".equals(targetAction)) {
            if (!"APPROVED".equalsIgnoreCase(currentStatus)) {
                throw new IllegalStateException("Only APPROVED conversions can be reversed");
            }

            long rewardCoins = conv.getRewardCoins();
            String uid = conv.getUserId();
            DocumentReference userRef = db.collection("users").document(uid);

            return db.runTransaction(transaction -> {
                DocumentSnapshot userSnap = transaction.get(userRef).get();
                if (!userSnap.exists()) {
                    throw new IllegalStateException("User not found: " + uid);
                }

                Long coinsObj = userSnap.getLong("coins");
                long coins = coinsObj != null ? coinsObj : 0;
                long updatedCoins = Math.max(0, coins - rewardCoins);

                // Deduct coins from wallet as compensating transaction
                transaction.update(userRef, "coins", updatedCoins);
                transaction.update(convRef, "status", "REVERSED");
                transaction.update(convRef, "reversedAt", System.currentTimeMillis());

                DocumentReference coinDetailRef = db.collection("users").document(uid).collection("coinDetails").document();
                Map<String, Object> coinDetail = new HashMap<>();
                coinDetail.put("amount", rewardCoins);
                coinDetail.put("type", "Share & Earn Reward Reversal");
                coinDetail.put("status", "Deducted");
                coinDetail.put("istype", "coin");
                coinDetail.put("source", "SHARE_EARN_REVERSAL");
                coinDetail.put("sourceId", conversionId);
                coinDetail.put("created_at", FieldValue.serverTimestamp());
                transaction.set(coinDetailRef, coinDetail);

                logAudit("ADMIN", "REVERSE_CONVERSION", "CONVERSION", conversionId, currentStatus, "REVERSED");

                Map<String, Object> res = new HashMap<>();
                res.put("success", true);
                res.put("conversionId", conversionId);
                res.put("status", "REVERSED");
                res.put("deductedCoins", rewardCoins);
                res.put("updatedCoins", updatedCoins);
                return res;
            }).get();
        } else {
            throw new IllegalArgumentException("Unknown action: " + action);
        }
    }

    public List<ShareEarnAuditLogModel> getAuditLogs() throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        Query query = db.collection("share_earn_audit_logs").orderBy("timestamp", Query.Direction.DESCENDING).limit(200);
        List<QueryDocumentSnapshot> docs = query.get().get().getDocuments();
        List<ShareEarnAuditLogModel> logs = new ArrayList<>();
        for (QueryDocumentSnapshot doc : docs) {
            logs.add(doc.toObject(ShareEarnAuditLogModel.class));
        }
        return logs;
    }

    public Map<String, Object> getReports() throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();

        List<QueryDocumentSnapshot> offerDocs = db.collection("offers").get().get().getDocuments();
        List<QueryDocumentSnapshot> clickDocs = db.collection("tracking_clicks").get().get().getDocuments();
        List<QueryDocumentSnapshot> convDocs = db.collection("conversions").get().get().getDocuments();

        int totalOffers = offerDocs.size();
        int totalClicks = clickDocs.size();
        int totalConversions = convDocs.size();
        int pendingConversions = 0;
        int approvedConversions = 0;
        int rejectedConversions = 0;
        int reversedConversions = 0;
        long totalRewardedCoins = 0;

        for (QueryDocumentSnapshot doc : convDocs) {
            ConversionModel conv = doc.toObject(ConversionModel.class);
            if ("APPROVED".equalsIgnoreCase(conv.getStatus())) {
                approvedConversions++;
                totalRewardedCoins += conv.getRewardCoins();
            } else if ("PENDING".equalsIgnoreCase(conv.getStatus())) {
                pendingConversions++;
            } else if ("REJECTED".equalsIgnoreCase(conv.getStatus())) {
                rejectedConversions++;
            } else if ("REVERSED".equalsIgnoreCase(conv.getStatus())) {
                reversedConversions++;
            }
        }

        double conversionRate = totalClicks > 0 ? ((double) approvedConversions / totalClicks) * 100.0 : 0.0;

        Map<String, Object> reports = new HashMap<>();
        reports.put("totalOffers", totalOffers);
        reports.put("totalClicks", totalClicks);
        reports.put("totalConversions", totalConversions);
        reports.put("approvedConversions", approvedConversions);
        reports.put("pendingConversions", pendingConversions);
        reports.put("rejectedConversions", rejectedConversions);
        reports.put("reversedConversions", reversedConversions);
        reports.put("totalRewardedCoins", totalRewardedCoins);
        reports.put("conversionRate", Math.round(conversionRate * 100.0) / 100.0);
        return reports;
    }

    private void logAudit(String role, String action, String entityType, String entityId, String oldValue, String newValue) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            DocumentReference ref = db.collection("share_earn_audit_logs").document();
            ShareEarnAuditLogModel log = new ShareEarnAuditLogModel();
            log.setAuditId(ref.getId());
            log.setActorId("ADMIN");
            log.setActorRole(role);
            log.setAction(action);
            log.setEntityType(entityType);
            log.setEntityId(entityId);
            log.setTimestamp(System.currentTimeMillis());
            log.setOldValue(oldValue);
            log.setNewValue(newValue);
            ref.set(log);
        } catch (Exception ignored) {}
    }
}
