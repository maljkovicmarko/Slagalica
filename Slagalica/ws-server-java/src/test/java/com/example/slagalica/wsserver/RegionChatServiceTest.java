package com.example.slagalica.wsserver;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class RegionChatServiceTest {
    @Test
    public void normalizesLegacyRegionNamesToCanonicalIds() {
        assertEquals("sumadija_zapadna_srbija",
                RegionChatService.normalizeRegionId("Šumadija i Zapadna Srbija"));
        assertEquals("juzna_istocna_srbija",
                RegionChatService.normalizeRegionId("Južna i Istočna Srbija"));
        assertEquals("kosovo_metohija",
                RegionChatService.normalizeRegionId("Kosovo i Metohija"));
    }

    @Test
    public void trimsValidMessages() {
        assertEquals("Zdravo svima!", RegionChatService.validateMessageText("  Zdravo svima!  "));
    }

    @Test
    public void rejectsBlankAndOversizedMessages() {
        assertThrows(IllegalArgumentException.class,
                () -> RegionChatService.validateMessageText("   "));
        assertThrows(IllegalArgumentException.class,
                () -> RegionChatService.validateMessageText("x".repeat(501)));
    }
}
