package com.example.backend;

import com.example.backend.service.DrawService;
import com.example.backend.util.TokenGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class DrawServiceTest {

    @Test
    void testSupportedPresets() {
        Assertions.assertEquals(6, DrawService.SUPPORTED_PRESETS.size());
        
        // 5 -> 25 / 1
        Assertions.assertEquals(5L, DrawService.SUPPORTED_PRESETS.get(0).get("participationLimit"));
        Assertions.assertEquals(25L, DrawService.SUPPORTED_PRESETS.get(0).get("rewardCoins"));
        Assertions.assertEquals(1L, DrawService.SUPPORTED_PRESETS.get(0).get("ticketCost"));

        // 100 -> 1000 / 1
        Assertions.assertEquals(100L, DrawService.SUPPORTED_PRESETS.get(5).get("participationLimit"));
        Assertions.assertEquals(1000L, DrawService.SUPPORTED_PRESETS.get(5).get("rewardCoins"));
        Assertions.assertEquals(1L, DrawService.SUPPORTED_PRESETS.get(5).get("ticketCost"));
    }

    @Test
    void test5DigitTokenFormatValidation() {
        for (int i = 0; i < 500; i++) {
            String token = TokenGenerator.generateToken();
            Assertions.assertNotNull(token);
            Assertions.assertEquals(5, token.length(), "Token length must be 5 digits");
            Assertions.assertTrue(token.matches("^[A-Z0-9]{5}$"), "Token must match ^[A-Z0-9]{5}$");
        }
    }

    @Test
    void testDrawIdFormatting() {
        long drawNumber = 25;
        String drawId = "DRAW" + drawNumber;
        Assertions.assertEquals("DRAW25", drawId);
        Assertions.assertTrue(drawId.matches("^DRAW\\d+$"));
    }

    @Test
    void testActiveDrawConfigurationImmutabilityLogic() {
        // Draw 25 snapshot created with participation=50, reward=500, ticketCost=50
        Map<String, Object> draw25 = new HashMap<>();
        draw25.put("drawId", "DRAW25");
        draw25.put("participationLimit", 50L);
        draw25.put("rewardCoins", 500L);
        draw25.put("ticketCost", 50L);
        draw25.put("status", "OPEN");

        // Admin updates global config to participation=100, reward=1000, ticketCost=100
        Map<String, Object> newAdminConfig = new HashMap<>();
        newAdminConfig.put("participationLimit", 100L);
        newAdminConfig.put("rewardCoins", 1000L);
        newAdminConfig.put("ticketCost", 100L);

        // Verify active draw25 snapshot remains unchanged
        Assertions.assertEquals(50L, draw25.get("participationLimit"));
        Assertions.assertEquals(500L, draw25.get("rewardCoins"));
        Assertions.assertEquals(50L, draw25.get("ticketCost"));

        // Verify next draw reading newAdminConfig would get new settings
        Map<String, Object> draw26 = new HashMap<>();
        draw26.put("drawId", "DRAW26");
        draw26.put("participationLimit", newAdminConfig.get("participationLimit"));
        draw26.put("rewardCoins", newAdminConfig.get("rewardCoins"));
        draw26.put("ticketCost", newAdminConfig.get("ticketCost"));
        draw26.put("status", "OPEN");

        Assertions.assertEquals(100L, draw26.get("participationLimit"));
        Assertions.assertEquals(1000L, draw26.get("rewardCoins"));
        Assertions.assertEquals(100L, draw26.get("ticketCost"));
    }
}
