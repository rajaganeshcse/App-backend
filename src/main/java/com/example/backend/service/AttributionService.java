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
        Firestore db = FirestoreClient.getFirestore();
        long now = System.currentTimeMillis();

        if ((clickId == null || clickId.isEmpty()) && referrer != null && !referrer.isEmpty()) {
            clickId = extractClickIdFromReferrer(referrer);
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
        doc.put("createdAt", now);
        doc.put("status", "RECORDED");
        db.collection("install_attributions").document(attributionId).set(doc).get();

        if (clickId != null && !clickId.isEmpty()) {
            try {
                DocumentReference clickRef = db.collection("tracking_clicks").document(clickId);
                DocumentSnapshot snap = clickRef.get().get();
                if (snap.exists()) {
                    Map<String, Object> upd = new HashMap<>();
                    upd.put("installedAt", now);
                    upd.put("installAttributionId", attributionId);
                    if (uid != null && !uid.isEmpty()) upd.put("userId", uid);
                    clickRef.update(upd).get();
                }
            } catch (Exception ignored) {}
        }

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("attributionId", attributionId);
        res.put("clickId", clickId != null ? clickId : "");
        res.put("message", "Install attribution recorded");
        return res;
    }

    public Map<String, Object> recordRegistration(String uid, String clickId)
            throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        long now = System.currentTimeMillis();

        if ((clickId == null || clickId.isEmpty()) && uid != null) {
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

        String regAttrId = "RATTR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        Map<String, Object> regDoc = new HashMap<>();
        regDoc.put("attributionId", regAttrId);
        regDoc.put("type", "REGISTRATION");
        regDoc.put("uid", uid);
        regDoc.put("clickId", clickId != null ? clickId : "");
        regDoc.put("createdAt", now);
        regDoc.put("status", "RECORDED");
        db.collection("install_attributions").document(regAttrId).set(regDoc).get();

        if (clickId != null && !clickId.isEmpty()) {
            try {
                DocumentReference clickRef = db.collection("tracking_clicks").document(clickId);
                DocumentSnapshot snap = clickRef.get().get();
                if (snap.exists()) {
                    Map<String, Object> upd = new HashMap<>();
                    upd.put("registeredAt", now);
                    upd.put("registeredUid", uid);
                    upd.put("registrationAttributionId", regAttrId);
                    String existingUid = snap.getString("userId");
                    if (existingUid == null || existingUid.isEmpty() || existingUid.equals("ANONYMOUS")) {
                        upd.put("userId", uid);
                    }
                    clickRef.update(upd).get();
                }
            } catch (Exception ignored) {}
        }

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("attributionId", regAttrId);
        res.put("clickId", clickId != null ? clickId : "");
        res.put("uid", uid);
        res.put("message", "Registration attribution recorded");
        return res;
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
                    if ("click_id".equals(key) || "clickId".equals(key) || "sub_id".equals(key)) return val;
                }
            }
        } catch (UnsupportedEncodingException ignored) {}
        return null;
    }
}
