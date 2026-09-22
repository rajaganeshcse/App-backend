package com.example.backend.service;

import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
public class AttributionService {

    public Map<String, Object> recordInstall(String uid, String clickId, String referrer, String deviceId, String appVersion)
            throws ExecutionException, InterruptedException {
        return recordInstall(uid, clickId, referrer, deviceId, appVersion, null);
    }

    public Map<String, Object> recordInstall(String uid, String clickId, String referrer, String deviceId, String appVersion, String clientIp)
            throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        long now = System.currentTimeMillis();

        if ((clickId == null || clickId.trim().isEmpty()) && referrer != null && !referrer.trim().isEmpty()) {
            clickId = extractClickIdFromReferrer(referrer);
        }

        // Probabilistic / Fingerprint fallback: match recent click from same device or IP within 48h
        if (clickId == null || clickId.trim().isEmpty()) {
            clickId = findRecentClick(db, deviceId, clientIp);
        }

        String attributionId = "ATTR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        Map<String, Object> doc = new HashMap<>();
        doc.put("attributionId", attributionId);
        doc.put("type", "INSTALL");
        doc.put("uid", uid != null ? uid : "ANONYMOUS");
        doc.put("clickId", clickId != null ? clickId : "");
        doc.put("referrer", referrer != null ? referrer : "");
        doc.put("deviceId", deviceId != null ? deviceId : "");
        doc.put("appVersion", appVersion != null ? appVersion : "");
        doc.put("ipAddress", clientIp != null ? clientIp : "");
        doc.put("createdAt", now);
        doc.put("status", "RECORDED");
        db.collection("install_attributions").document(attributionId).set(doc).get();

        String matchedOfferId = null;
        if (clickId != null && !clickId.isEmpty()) {
            try {
                DocumentReference clickRef = db.collection("tracking_clicks").document(clickId);
                DocumentSnapshot snap = clickRef.get().get();
                if (snap.exists()) {
                    matchedOfferId = snap.getString("offerId");
                    Map<String, Object> upd = new HashMap<>();
                    upd.put("installedAt", now);
                    upd.put("installAttributionId", attributionId);
                    if (deviceId != null && !deviceId.isEmpty()) upd.put("deviceId", deviceId);
                    if (uid != null && !uid.isEmpty() && !uid.equals("ANONYMOUS")) {
                        String existingUid = snap.getString("userId");
                        if (existingUid == null || existingUid.isEmpty() || existingUid.equals("ANONYMOUS") || existingUid.equals("DIRECT_VISITOR")) {
                            upd.put("userId", uid);
                        }
                        upd.put("installedUid", uid);
                    }
                    String currentStatus = snap.getString("status");
                    if (!"CONVERTED".equalsIgnoreCase(currentStatus)) {
                        upd.put("status", "INSTALLED");
                    }
                    clickRef.update(upd).get();

                    // Increment totalInstalls counter on associated offer
                    if (matchedOfferId != null && !matchedOfferId.isEmpty()) {
                        try {
                            db.collection("offers").document(matchedOfferId).update("totalInstalls", FieldValue.increment(1)).get();
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("attributionId", attributionId);
        res.put("clickId", clickId != null ? clickId : "");
        res.put("offerId", matchedOfferId != null ? matchedOfferId : "");
        res.put("message", "Install attribution recorded successfully");
        return res;
    }

    public Map<String, Object> recordRegistration(String uid, String clickId)
            throws ExecutionException, InterruptedException {
        return recordRegistration(uid, clickId, null, null);
    }

    public Map<String, Object> recordRegistration(String uid, String clickId, String deviceId, String clientIp)
            throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        long now = System.currentTimeMillis();

        // 1. Resolve clickId if not directly supplied
        if ((clickId == null || clickId.trim().isEmpty())) {
            // First check install_attributions by uid
            if (uid != null && !uid.isEmpty()) {
                Query q = db.collection("install_attributions")
                        .whereEqualTo("uid", uid)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(1);
                List<QueryDocumentSnapshot> docs = q.get().get().getDocuments();
                if (!docs.isEmpty()) {
                    Object cid = docs.get(0).get("clickId");
                    if (cid != null && !cid.toString().isEmpty()) clickId = cid.toString();
                }
            }

            // Second check install_attributions by deviceId
            if ((clickId == null || clickId.isEmpty()) && deviceId != null && !deviceId.isEmpty()) {
                Query q = db.collection("install_attributions")
                        .whereEqualTo("deviceId", deviceId)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(1);
                List<QueryDocumentSnapshot> docs = q.get().get().getDocuments();
                if (!docs.isEmpty()) {
                    Object cid = docs.get(0).get("clickId");
                    if (cid != null && !cid.toString().isEmpty()) {
                        clickId = cid.toString();
                        // Link this anonymous install doc to the registered uid
                        try {
                            docs.get(0).getReference().update("registeredUid", uid, "registeredAt", now);
                        } catch (Exception ignored) {}
                    }
                }
            }

            // Third fallback: match recent click from same device or IP
            if (clickId == null || clickId.isEmpty()) {
                clickId = findRecentClick(db, deviceId, clientIp);
            }
        }

        String regAttrId = "RATTR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        Map<String, Object> regDoc = new HashMap<>();
        regDoc.put("attributionId", regAttrId);
        regDoc.put("type", "REGISTRATION");
        regDoc.put("uid", uid);
        regDoc.put("clickId", clickId != null ? clickId : "");
        regDoc.put("deviceId", deviceId != null ? deviceId : "");
        regDoc.put("ipAddress", clientIp != null ? clientIp : "");
        regDoc.put("createdAt", now);
        regDoc.put("status", "RECORDED");
        db.collection("install_attributions").document(regAttrId).set(regDoc).get();

        String matchedOfferId = null;
        if (clickId != null && !clickId.isEmpty()) {
            try {
                DocumentReference clickRef = db.collection("tracking_clicks").document(clickId);
                DocumentSnapshot snap = clickRef.get().get();
                if (snap.exists()) {
                    matchedOfferId = snap.getString("offerId");
                    Map<String, Object> upd = new HashMap<>();
                    upd.put("registeredAt", now);
                    upd.put("registeredUid", uid);
                    upd.put("registrationAttributionId", regAttrId);
                    String existingUid = snap.getString("userId");
                    if (existingUid == null || existingUid.isEmpty() || existingUid.equals("ANONYMOUS") || existingUid.equals("DIRECT_VISITOR")) {
                        upd.put("userId", uid);
                    }
                    String currentStatus = snap.getString("status");
                    if (!"CONVERTED".equalsIgnoreCase(currentStatus)) {
                        upd.put("status", "REGISTERED");
                    }
                    clickRef.update(upd).get();

                    // Increment totalRegistrations counter on associated offer
                    if (matchedOfferId != null && !matchedOfferId.isEmpty()) {
                        try {
                            db.collection("offers").document(matchedOfferId).update("totalRegistrations", FieldValue.increment(1)).get();
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("attributionId", regAttrId);
        res.put("clickId", clickId != null ? clickId : "");
        res.put("offerId", matchedOfferId != null ? matchedOfferId : "");
        res.put("uid", uid);
        res.put("message", "Registration attribution recorded successfully");
        return res;
    }

    private String findRecentClick(Firestore db, String deviceId, String clientIp) {
        long cutoff = System.currentTimeMillis() - (48L * 60 * 60 * 1000); // 48-hour attribution window
        try {
            if (deviceId != null && !deviceId.trim().isEmpty()) {
                Query q = db.collection("tracking_clicks")
                        .whereEqualTo("deviceInfo", deviceId)
                        .whereGreaterThanOrEqualTo("createdAt", cutoff)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(1);
                List<QueryDocumentSnapshot> docs = q.get().get().getDocuments();
                if (!docs.isEmpty()) {
                    return docs.get(0).getId();
                }
            }
            if (clientIp != null && !clientIp.trim().isEmpty() && !clientIp.equals("127.0.0.1")) {
                Query q = db.collection("tracking_clicks")
                        .whereEqualTo("ipAddress", clientIp)
                        .whereGreaterThanOrEqualTo("createdAt", cutoff)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(1);
                List<QueryDocumentSnapshot> docs = q.get().get().getDocuments();
                if (!docs.isEmpty()) {
                    return docs.get(0).getId();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractClickIdFromReferrer(String referrer) {
        if (referrer == null || referrer.isEmpty()) return null;
        try {
            String[] params = referrer.split("&");
            for (String param : params) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim();
                    String val = URLDecoder.decode(kv[1].trim(), StandardCharsets.UTF_8.name());
                    if ("click_id".equalsIgnoreCase(key) || "clickId".equalsIgnoreCase(key) || "sub_id".equalsIgnoreCase(key)) return val;
                }
            }
        } catch (UnsupportedEncodingException ignored) {}
        return null;
    }
}
