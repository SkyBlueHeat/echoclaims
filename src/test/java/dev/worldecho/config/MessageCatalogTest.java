package dev.worldecho.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCatalogTest {

    private static final Map<String, String> ENGLISH = Map.of(
            "prefix", "[WorldEcho] ",
            "recent-empty", "No memories yet.",
            "status-line", "{key}: {value}"
    );

    @Test
    void usesTheRequestedLocaleFirst() {
        MessageCatalog catalog = new MessageCatalog(
                Map.of("recent-empty", "Henüz hatıra yok."), ENGLISH);

        assertTrue(catalog.format("recent-empty").endsWith("Henüz hatıra yok."));
    }

    @Test
    void fallsBackToEnglishForUntranslatedKeys() {
        MessageCatalog catalog = new MessageCatalog(Map.of("prefix", ""), ENGLISH);

        assertEquals("No memories yet.", catalog.format("recent-empty"));
    }

    @Test
    void missingKeysAreVisibleInsteadOfSilent() {
        MessageCatalog catalog = new MessageCatalog(Map.of(), Map.of());

        assertTrue(catalog.format("nope").contains("nope"));
        assertFalse(catalog.has("nope"));
    }

    @Test
    void placeholdersAreReplaced() {
        MessageCatalog catalog = new MessageCatalog(Map.of("prefix", ""), ENGLISH);

        assertEquals("events: 7",
                catalog.format("status-line", Map.of("key", "events", "value", "7")));
    }

    @Test
    void placeholderValuesCannotInjectFormatting() {
        MessageCatalog catalog = new MessageCatalog(Map.of("prefix", ""), ENGLISH);

        String rendered = catalog.format(
                "status-line", Map.of("key", "item", "value", "<red>Hacked</red>"));

        assertEquals("item: redHacked/red", rendered);
    }
}
