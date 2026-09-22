package com.example.backend.service;

import com.example.backend.model.*;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
public class ShareEarnService {

    @Autowired(required = false)
    private NotificationService notificationService;

    private static final String API_SECRET = System.getenv().getOrDefault("SHARE_EARN_API_SECRET", "rewards_planet_share_earn_secret_key_2026");

    public List<OfferModel> getActiveOffers(String category, String search) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        // Fetch all offers and filter active ones cleanly to handle any casing
        List<QueryDocumentSnapshot> docs = db.collection("offers").get().get().getDocuments();

        List<OfferModel> offers = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (QueryDocumentSnapshot doc : docs) {
            OfferModel offer = doc.toObject(OfferModel.class);
            if (offer.getOfferId() == null) {
                offer.setOfferId(doc.getId());
            }

            // Status check (Default to ACTIVE if null)
            String status = offer.getStatus();
            if (status != null && "INACTIVE".equalsIgnoreCase(status.trim())) {
                continue;
            }

            // Date validation
            if (offer.getStartDate() != null && offer.getStartDate() > now) {
                continue;
            }
            if (offer.getEndDate() != null && offer.getEndDate() < now) {
                continue;
            }

            // Category filter
            if (category != null && !category.trim().isEmpty() && !"All".equalsIgnoreCase(category.trim())) {
                if (offer.getCategory() == null || !offer.getCategory().equalsIgnoreCase(category.trim())) {
                    continue;
                }
            }

            // Search filter
            if (search != null && !search.trim().isEmpty()) {
                String q = search.trim().toLowerCase();
                boolean matchTitle = offer.getTitle() != null && offer.getTitle().toLowerCase().contains(q);
                boolean matchDesc = offer.getShortDescription() != null && offer.getShortDescription().toLowerCase().contains(q);
                boolean matchCat = offer.getCategory() != null && offer.getCategory().toLowerCase().contains(q);
                if (!matchTitle && !matchDesc && !matchCat) {
                    continue;
                }
            }

            offers.add(offer);
        }

        // Sort by priority descending
        offers.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        return offers;
    }

    public OfferModel getOfferById(String offerId) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        DocumentSnapshot doc = db.collection("offers").document(offerId).get().get();
        if (!doc.exists()) {
            return null;
        }
        OfferModel offer = doc.toObject(OfferModel.class);
        if (offer != null && offer.getOfferId() == null) {
            offer.setOfferId(doc.getId());
        }
        return offer;
    }

    public Map<String, Object> createClickTracking(String uid, String offerId, String baseUrl) throws Exception {
        return createClickTracking(uid, offerId, baseUrl, null, null);
    }

    public Map<String, Object> createClickTracking(String uid, String offerId, String baseUrl, String ipAddress, String userAgent) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        DocumentSnapshot offerDoc = db.collection("offers").document(offerId).get().get();

        if (!offerDoc.exists()) {
            throw new IllegalArgumentException("Offer not found");
        }

        OfferModel offer = offerDoc.toObject(OfferModel.class);
        String offerStatus = offerDoc.getString("status");
        if (offerStatus != null && "INACTIVE".equalsIgnoreCase(offerStatus.trim())) {
            throw new IllegalArgumentException("Offer is currently inactive");
        }

        long now = System.currentTimeMillis();
        Long sDate = offerDoc.getLong("startDate");
        Long eDate = offerDoc.getLong("endDate");
        if (sDate != null && sDate > now) {
            throw new IllegalArgumentException("Offer has not started yet");
        }
        if (eDate != null && eDate < now) {
            throw new IllegalArgumentException("Offer has expired");
        }

        // Query prior clicks by this user on this offer to determine if New User or Repeat Clicker
        Query priorClicksQuery = db.collection("tracking_clicks")
                .whereEqualTo("userId", uid)
                .whereEqualTo("offerId", offerId);
        List<QueryDocumentSnapshot> priorClicks = priorClicksQuery.get().get().getDocuments();

        boolean isNewUser = priorClicks.isEmpty();
        int clickCount = priorClicks.size() + 1;

        String clickId = "CLK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        long expiresAt = now + (7L * 24 * 60 * 60 * 1000); // 7 days expiration

        TrackingClickModel click = new TrackingClickModel();
        click.setClickId(clickId);
        click.setUserId(uid);
        click.setOfferId(offerId);
        click.setCreatedAt(now);
        click.setClickedAt(now);
        click.setLastClickedAt(now);
        click.setStatus("CLICKED");
        click.setExpiresAt(expiresAt);
        click.setNewUser(isNewUser);
        click.setClickCount(clickCount);
        click.setIpAddress(ipAddress);
        click.setDeviceInfo(userAgent != null ? userAgent : "AndroidApp");

        Map<String, String> meta = new HashMap<>();
        meta.put("createdFrom", "AndroidApp");
        if (offer.getReferralCode() != null && !offer.getReferralCode().isEmpty()) {
            meta.put("offerReferralCode", offer.getReferralCode());
        }
        if (offer.getOfferType() != null) {
            meta.put("offerType", offer.getOfferType());
        }
        click.setMetadata(meta);

        db.collection("tracking_clicks").document(clickId).set(click).get();

        String trackingDomain = System.getenv().getOrDefault("TRACKING_DOMAIN", baseUrl);
        if (trackingDomain.endsWith("/")) {
            trackingDomain = trackingDomain.substring(0, trackingDomain.length() - 1);
        }

        String trackingUrl = trackingDomain + "/r/" + clickId;

        Map<String, Object> res = new HashMap<>();
        res.put("clickId", clickId);
        res.put("trackingUrl", trackingUrl);
        res.put("offerId", offerId);
        res.put("expiresAt", expiresAt);
        res.put("isNewUser", isNewUser);
        res.put("clickCount", clickCount);
        res.put("referralCode", offer.getReferralCode());
        res.put("offerType", offer.getOfferType() != null ? offer.getOfferType() : "AFFILIATE");
        return res;
    }

    public String handleRedirect(String clickId) throws Exception {
        return handleRedirect(clickId, null, null);
    }

    public String handleRedirect(String trackingId, String ipAddress, String userAgent) throws Exception {
        if (trackingId == null || trackingId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tracking link identifier is required");
        }
        String cleanId = trackingId.trim();

        Firestore db = FirestoreClient.getFirestore();
        DocumentReference clickRef = db.collection("tracking_clicks").document(cleanId);
        DocumentSnapshot clickDoc = clickRef.get().get();

        String offerId;
        String clickId;

        if (clickDoc.exists()) {
            // Case 1: Standard tracking click generated by app or web
            TrackingClickModel click = clickDoc.toObject(TrackingClickModel.class);
            if (click == null) {
                throw new IllegalArgumentException("Tracking record corrupted");
            }

            if (click.getExpiresAt() != null && System.currentTimeMillis() > click.getExpiresAt()) {
                clickRef.update("status", "EXPIRED");
                throw new IllegalArgumentException("Tracking link has expired");
            }

            offerId = click.getOfferId();
            clickId = cleanId;

            // Record redirection timestamp & update client information
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "REDIRECTED");
            updates.put("redirectedAt", System.currentTimeMillis());
            updates.put("lastClickedAt", System.currentTimeMillis());
            if (ipAddress != null && (click.getIpAddress() == null || click.getIpAddress().isEmpty())) {
                updates.put("ipAddress", ipAddress);
            }
            if (userAgent != null && (click.getDeviceInfo() == null || "AndroidApp".equals(click.getDeviceInfo()))) {
                updates.put("deviceInfo", userAgent);
            }
            clickRef.update(updates);

        } else {
            // Case 2: Tracking identifier is an Offer ID (direct campaign link / admin test link)
            DocumentSnapshot offerSnap = db.collection("offers").document(cleanId).get().get();
            if (offerSnap.exists()) {
                offerId = cleanId;
                clickId = "CLK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
                long now = System.currentTimeMillis();
                long expiresAt = now + (7L * 24 * 60 * 60 * 1000);

                TrackingClickModel directClick = new TrackingClickModel();
                directClick.setClickId(clickId);
                directClick.setUserId("DIRECT_VISITOR");
                directClick.setOfferId(offerId);
                directClick.setCreatedAt(now);
                directClick.setClickedAt(now);
                directClick.setRedirectedAt(now);
                directClick.setLastClickedAt(now);
                directClick.setStatus("REDIRECTED");
                directClick.setExpiresAt(expiresAt);
                directClick.setNewUser(true);
                directClick.setClickCount(1);
                directClick.setIpAddress(ipAddress);
                directClick.setDeviceInfo(userAgent != null ? userAgent : "DirectWeb");

                Map<String, String> meta = new HashMap<>();
                meta.put("source", "DIRECT_OFFER_LINK");
                directClick.setMetadata(meta);

                db.collection("tracking_clicks").document(clickId).set(directClick).get();
            } else {
                throw new IllegalArgumentException("Invalid tracking link: " + cleanId);
            }
        }

        DocumentSnapshot offerDoc = db.collection("offers").document(offerId).get().get();
        if (!offerDoc.exists()) {
            throw new IllegalArgumentException("Associated offer no longer exists");
        }

        String destUrl = offerDoc.getString("destinationUrl");
        if (destUrl == null || destUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Destination URL not configured for this offer");
        }
        destUrl = destUrl.trim();

        // Enforce scheme (prevents browser from treating destination as relative URL and looping back)
        if (!destUrl.startsWith("http://") && !destUrl.startsWith("https://")) {
            destUrl = "https://" + destUrl;
        }

        // Guard against accidental Admin Dashboard URLs or recursive tracking links
        if (destUrl.contains("appdashboard") || destUrl.contains("vercel.app") || destUrl.contains("/admin") || destUrl.contains("/r/") || destUrl.contains("/track/")) {
            String title = offerDoc.getString("title");
            if (title != null && (title.toLowerCase().contains("gpay") || title.toLowerCase().contains("google pay"))) {
                destUrl = "https://play.google.com/store/apps/details?id=com.google.android.apps.nbu.paisa.user";
            } else if (title != null && title.toLowerCase().contains("phonepe")) {
                destUrl = "https://play.google.com/store/apps/details?id=com.phonepe.app";
            } else if (title != null && title.toLowerCase().contains("upstox")) {
                destUrl = "https://upstox.com/open-demat-account/";
            } else {
                destUrl = "https://play.google.com/store/apps/details?id=com.app.rewardsplanet";
            }
        }

        // Macro expansion for partner tracking URLs
        if (destUrl.contains("{click_id}") || destUrl.contains("{clickId}") || destUrl.contains("{sub_id}") || destUrl.contains("{subid}") || destUrl.contains("{click}")) {
            destUrl = destUrl.replace("{click_id}", clickId)
                             .replace("{clickId}", clickId)
                             .replace("{sub_id}", clickId)
                             .replace("{subid}", clickId)
                             .replace("{click}", clickId);
        } else if (!destUrl.contains("click_id=") && !destUrl.contains("clickId=")) {
            if (destUrl.contains("?")) {
                destUrl += "&click_id=" + clickId;
            } else {
                destUrl += "?click_id=" + clickId;
            }
        }

        // Google Play Store Referrer enrichment:
        // Google Play Store delivers the 'referrer' parameter to the app upon install via Play Install Referrer API.
        if (destUrl.contains("play.google.com")) {
            try {
                String playReferrerParam = "click_id=" + clickId;
                if (destUrl.contains("referrer=")) {
                    destUrl = destUrl.replace("referrer=", "referrer=" + URLEncoder.encode(playReferrerParam + "&", StandardCharsets.UTF_8.name()));
                } else {
                    destUrl += (destUrl.contains("?") ? "&" : "?") + "referrer=" + URLEncoder.encode(playReferrerParam, StandardCharsets.UTF_8.name());
                }
            } catch (Exception ignored) {}
        }

        return destUrl;
    }

    public Map<String, Object> submitOfferClaim(
            String uid,
            String offerId,
            String clickId,
            String proofText,
            String referralCodeUsed
    ) throws Exception {
        Firestore db = FirestoreClient.getFirestore();

        DocumentSnapshot offerDoc = db.collection("offers").document(offerId).get().get();
        if (!offerDoc.exists()) {
            throw new IllegalArgumentException("Offer not found: " + offerId);
        }

        OfferModel offer = offerDoc.toObject(OfferModel.class);
        if (offer == null || "INACTIVE".equalsIgnoreCase(offer.getStatus())) {
            throw new IllegalArgumentException("Offer is no longer active");
        }

        // Validate proof requirement
        if (offer.isProofRequired() && (proofText == null || proofText.trim().isEmpty())) {
            String label = offer.getProofLabel() != null && !offer.getProofLabel().isEmpty() 
                    ? offer.getProofLabel() 
                    : "proof details";
            throw new IllegalArgumentException("Proof is required: Please provide " + label);
        }

        // 1. Check if user already has an APPROVED conversion for this offer
        Query approvedQuery = db.collection("conversions")
                .whereEqualTo("userId", uid)
                .whereEqualTo("offerId", offerId)
                .whereEqualTo("status", "APPROVED");
        if (!approvedQuery.get().get().isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "ALREADY_COMPLETED");
            err.put("message", "You have already completed this offer and received your reward.");
            return err;
        }

        // 2. Check if user already has a PENDING claim for this offer
        Query pendingQuery = db.collection("conversions")
                .whereEqualTo("userId", uid)
                .whereEqualTo("offerId", offerId)
                .whereEqualTo("status", "PENDING");
        if (!pendingQuery.get().get().isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("code", "CLAIM_PENDING");
            err.put("message", "Your previous claim for this offer is currently under review by Admin.");
            return err;
        }

        // If clickId is missing or empty, find or create one
        String validClickId = clickId;
        if (validClickId == null || validClickId.trim().isEmpty()) {
            Query lastClickQuery = db.collection("tracking_clicks")
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("offerId", offerId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(1);
            List<QueryDocumentSnapshot> clickDocs = lastClickQuery.get().get().getDocuments();
            if (!clickDocs.isEmpty()) {
                validClickId = clickDocs.get(0).getId();
            } else {
                validClickId = "CLK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
                TrackingClickModel newClick = new TrackingClickModel();
                newClick.setClickId(validClickId);
                newClick.setUserId(uid);
                newClick.setOfferId(offerId);
                newClick.setCreatedAt(System.currentTimeMillis());
                newClick.setClickedAt(System.currentTimeMillis());
                newClick.setStatus("CLAIM_SUBMITTED");
                newClick.setNewUser(true);
                newClick.setClickCount(1);
                db.collection("tracking_clicks").document(validClickId).set(newClick).get();
            }
        }

        long now = System.currentTimeMillis();
        DocumentReference convRef = db.collection("conversions").document();
        String conversionId = "CLAIM_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();

        ConversionModel conv = new ConversionModel();
        conv.setConversionId(conversionId);
        conv.setClickId(validClickId);
        conv.setUserId(uid);
        conv.setOfferId(offerId);
        conv.setEvent("MANUAL_CLAIM");
        conv.setExternalReference(proofText != null && !proofText.trim().isEmpty() ? proofText.trim() : ("CLAIM_" + uid));
        conv.setStatus("PENDING");
        conv.setRewardCoins(offer.getRewardCoins());
        conv.setCreatedAt(now);
        conv.setProofText(proofText != null ? proofText.trim() : "");
        conv.setReferralCodeUsed(referralCodeUsed != null && !referralCodeUsed.trim().isEmpty() ? referralCodeUsed.trim() : offer.getReferralCode());

        Map<String, String> meta = new HashMap<>();
        meta.put("proofText", conv.getProofText());
        meta.put("referralCodeUsed", conv.getReferralCodeUsed() != null ? conv.getReferralCodeUsed() : "");
        meta.put("submissionType", "MANUAL_CLAIM");
        meta.put("offerTitle", offer.getTitle() != null ? offer.getTitle() : "");
        meta.put("offerType", offer.getOfferType() != null ? offer.getOfferType() : "REFERRAL_TASK");
        conv.setMetadata(meta);

        // Save conversion document
        db.collection("conversions").document(conversionId).set(conv).get();

        // Update tracking click document
        try {
            Map<String, Object> clickUpdates = new HashMap<>();
            clickUpdates.put("status", "PENDING_CLAIM");
            clickUpdates.put("conversionId", conversionId);
            clickUpdates.put("lastClickedAt", now);
            db.collection("tracking_clicks").document(validClickId).update(clickUpdates).get();
        } catch (Exception ignored) {}

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("conversionId", conversionId);
        res.put("status", "PENDING");
        res.put("rewardCoins", offer.getRewardCoins());
        res.put("message", "Claim submitted successfully! Coins will be credited once verified by Admin.");
        return res;
    }

    public Map<String, Object> processConversion(
            String clickId,
            String event,
            String externalReference,
            String signature,
            String apiKey
    ) throws Exception {
        Firestore db = FirestoreClient.getFirestore();

        // Signature / Auth Verification
        if (apiKey != null && !apiKey.isEmpty()) {
            if (!API_SECRET.equals(apiKey)) {
                throw new SecurityException("Invalid Conversion API Key");
            }
        } else if (signature != null && !signature.isEmpty()) {
            String payload = clickId + ":" + (event != null ? event : "") + ":" + (externalReference != null ? externalReference : "");
            String expectedSig = hmacSha256(payload, API_SECRET);
            if (!expectedSig.equalsIgnoreCase(signature)) {
                throw new SecurityException("Invalid Conversion Signature");
            }
        }

        if (clickId == null || clickId.trim().isEmpty()) {
            throw new IllegalArgumentException("clickId is required");
        }

        DocumentSnapshot clickDoc = db.collection("tracking_clicks").document(clickId).get().get();
        if (!clickDoc.exists()) {
            throw new IllegalArgumentException("Tracking click not found: " + clickId);
        }

        TrackingClickModel click = clickDoc.toObject(TrackingClickModel.class);
        if (click == null) {
            throw new IllegalArgumentException("Invalid click record");
        }

        // Duplicate external reference check
        if (externalReference != null && !externalReference.trim().isEmpty()) {
            Query extQuery = db.collection("conversions").whereEqualTo("externalReference", externalReference.trim());
            if (!extQuery.get().get().isEmpty()) {
                Map<String, Object> already = new HashMap<>();
                already.put("success", false);
                already.put("code", "DUPLICATE_EXTERNAL_REFERENCE");
                already.put("message", "Conversion with this externalReference has already been processed");
                return already;
            }
        }

        // Check if click was already converted
        if ("CONVERTED".equalsIgnoreCase(click.getStatus())) {
            Map<String, Object> already = new HashMap<>();
            already.put("success", false);
            already.put("code", "ALREADY_REWARDED");
            already.put("message", "This click has already been converted and rewarded");
            return already;
        }

        DocumentSnapshot offerDoc = db.collection("offers").document(click.getOfferId()).get().get();
        if (!offerDoc.exists()) {
            throw new IllegalArgumentException("Associated offer not found");
        }

        OfferModel offer = offerDoc.toObject(OfferModel.class);
        if (offer == null) {
            throw new IllegalArgumentException("Invalid offer record");
        }

        // Read admin-configured reward coins
        long rewardCoins = offer.getRewardCoins();
        if (rewardCoins <= 0) {
            throw new IllegalStateException("Offer rewardCoins is invalid or 0");
        }

        String uid = click.getUserId();
        DocumentReference userRef = db.collection("users").document(uid);

        return db.runTransaction(transaction -> {
            DocumentSnapshot userSnap = transaction.get(userRef).get();
            if (!userSnap.exists()) {
                Map<String, Object> fail = new HashMap<>();
                fail.put("success", false);
                fail.put("code", "USER_NOT_FOUND");
                fail.put("message", "User not found: " + uid);
                return fail;
            }

            Long currentCoinsObj = userSnap.getLong("coins");
            long currentCoins = currentCoinsObj != null ? currentCoinsObj : 0;
            long newCoins = currentCoins + rewardCoins;

            // 1. Credit existing user coins
            transaction.update(userRef, "coins", newCoins);

            // 2. Create conversion document
            DocumentReference convRef = db.collection("conversions").document();
            String conversionId = "CONV_" + convRef.getId().toUpperCase();

            ConversionModel conversion = new ConversionModel();
            conversion.setConversionId(conversionId);
            conversion.setClickId(clickId);
            conversion.setUserId(uid);
            conversion.setOfferId(click.getOfferId());
            conversion.setEvent(event != null ? event : offer.getConversionEvent());
            conversion.setExternalReference(externalReference != null ? externalReference : conversionId);
            conversion.setStatus("APPROVED");
            conversion.setRewardCoins(rewardCoins);
            conversion.setCreatedAt(System.currentTimeMillis());
            conversion.setApprovedAt(System.currentTimeMillis());

            transaction.set(db.collection("conversions").document(conversionId), conversion);

            // 3. Update click record
            DocumentReference clickRef = db.collection("tracking_clicks").document(clickId);
            transaction.update(clickRef, "status", "CONVERTED");
            transaction.update(clickRef, "conversionId", conversionId);

            // 4. Atomic transaction set for coin details transaction log
            DocumentReference coinDetailRef = db.collection("users").document(uid).collection("coinDetails").document();
            Map<String, Object> coinDetail = new HashMap<>();
            coinDetail.put("amount", rewardCoins);
            coinDetail.put("type", "Share & Earn (" + offer.getTitle() + ")");
            coinDetail.put("status", "Credited");
            coinDetail.put("istype", "coin");
            coinDetail.put("source", "SHARE_EARN");
            coinDetail.put("sourceId", conversionId);
            coinDetail.put("offerId", click.getOfferId());
            coinDetail.put("created_at", FieldValue.serverTimestamp());

            transaction.set(coinDetailRef, coinDetail);

            // FCM Notification if available
            String fcmToken = userSnap.getString("fcmToken");
            if (fcmToken != null && !fcmToken.isEmpty() && notificationService != null) {
                try {
                    notificationService.send(
                            fcmToken,
                            "Coins Credited! 🎉",
                            "You earned " + rewardCoins + " coins for completing " + offer.getTitle() + "!",
                            "+" + rewardCoins + " Coins"
                    );
                } catch (Exception ignored) {}
            }

            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("conversionId", conversionId);
            res.put("rewardCoins", rewardCoins);
            res.put("updatedCoins", newCoins);
            res.put("status", "APPROVED");
            res.put("message", "Conversion approved and " + rewardCoins + " coins credited successfully");
            return res;
        }).get();
    }

    public List<Map<String, Object>> getUserOfferHistory(String uid, String statusFilter) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        Query query = db.collection("tracking_clicks").whereEqualTo("userId", uid);

        List<QueryDocumentSnapshot> clickDocs = query.get().get().getDocuments();
        List<Map<String, Object>> historyList = new ArrayList<>();

        for (QueryDocumentSnapshot clickDoc : clickDocs) {
            TrackingClickModel click = clickDoc.toObject(TrackingClickModel.class);
            DocumentSnapshot offerDoc = db.collection("offers").document(click.getOfferId()).get().get();

            if (!offerDoc.exists()) continue;

            OfferModel offer = offerDoc.toObject(OfferModel.class);
            if (offer == null) continue;

            String status = "Pending";
            String conversionId = click.getConversionId();

            if (conversionId != null && !conversionId.isEmpty()) {
                DocumentSnapshot convDoc = db.collection("conversions").document(conversionId).get().get();
                if (convDoc.exists()) {
                    ConversionModel conv = convDoc.toObject(ConversionModel.class);
                    if (conv != null && conv.getStatus() != null) {
                        status = conv.getStatus();
                    }
                }
            } else if ("EXPIRED".equalsIgnoreCase(click.getStatus()) || "INVALID".equalsIgnoreCase(click.getStatus())) {
                status = "Rejected";
            }

            // Filter mapping
            if (statusFilter != null && !statusFilter.isEmpty() && !"All".equalsIgnoreCase(statusFilter)) {
                if (!status.equalsIgnoreCase(statusFilter)) {
                    continue;
                }
            }

            Map<String, Object> item = new HashMap<>();
            item.put("clickId", click.getClickId());
            item.put("offerId", offer.getOfferId());
            item.put("title", offer.getTitle());
            item.put("logoUrl", offer.getLogoUrl());
            item.put("category", offer.getCategory());
            item.put("rewardCoins", offer.getRewardCoins());
            item.put("status", status);
            item.put("createdAt", click.getCreatedAt());
            historyList.add(item);
        }

        historyList.sort((a, b) -> Long.compare((Long) b.get("createdAt"), (Long) a.get("createdAt")));
        return historyList;
    }

    public Map<String, Object> getUserEarningsSummary(String uid) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        Query clickQuery = db.collection("tracking_clicks").whereEqualTo("userId", uid);
        List<QueryDocumentSnapshot> clickDocs = clickQuery.get().get().getDocuments();

        int totalClicks = clickDocs.size();
        int totalConversions = 0;
        int pendingConversions = 0;
        long totalEarnedCoins = 0;

        Map<String, Map<String, Object>> offerPerfMap = new HashMap<>();

        for (QueryDocumentSnapshot clickDoc : clickDocs) {
            TrackingClickModel click = clickDoc.toObject(TrackingClickModel.class);
            String offerId = click.getOfferId();

            if (!offerPerfMap.containsKey(offerId)) {
                DocumentSnapshot offerDoc = db.collection("offers").document(offerId).get().get();
                if (offerDoc.exists()) {
                    OfferModel offer = offerDoc.toObject(OfferModel.class);
                    Map<String, Object> perf = new HashMap<>();
                    perf.put("offerId", offerId);
                    perf.put("title", offer != null ? offer.getTitle() : "Offer");
                    perf.put("logoUrl", offer != null ? offer.getLogoUrl() : "");
                    perf.put("clicks", 0);
                    perf.put("conversions", 0);
                    perf.put("earnedCoins", 0L);
                    offerPerfMap.put(offerId, perf);
                }
            }

            Map<String, Object> perf = offerPerfMap.get(offerId);
            if (perf != null) {
                perf.put("clicks", (int) perf.get("clicks") + 1);
            }

            if ("CONVERTED".equalsIgnoreCase(click.getStatus()) && click.getConversionId() != null) {
                DocumentSnapshot convDoc = db.collection("conversions").document(click.getConversionId()).get().get();
                if (convDoc.exists()) {
                    ConversionModel conv = convDoc.toObject(ConversionModel.class);
                    if (conv != null && "APPROVED".equalsIgnoreCase(conv.getStatus())) {
                        totalConversions++;
                        totalEarnedCoins += conv.getRewardCoins();
                        if (perf != null) {
                            perf.put("conversions", (int) perf.get("conversions") + 1);
                            perf.put("earnedCoins", (long) perf.get("earnedCoins") + conv.getRewardCoins());
                        }
                    }
                }
            } else {
                pendingConversions++;
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalEarnedCoins", totalEarnedCoins);
        summary.put("totalClicks", totalClicks);
        summary.put("totalConversions", totalConversions);
        summary.put("pendingConversions", pendingConversions);
        summary.put("offerPerformance", new ArrayList<>(offerPerfMap.values()));
        return summary;
    }

    private String hmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
