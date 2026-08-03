package io.github.skyblueheat.echoclaims.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCatalogSafetyTest {

    @Test
    void adversarialPlaceholderIsSanitized() {
        MessageCatalog catalog = new MessageCatalog(
                Map.of("test", "Hello {name}!"),
                Map.of()
        );

        String result = catalog.format("test", Map.of("name", "<red>EVIL</red><click:run_command:/stop>"));
        assertFalse(result.contains("<red>"));
        assertFalse(result.contains("<click"));
        assertTrue(result.contains("EVIL"));
    }

    @Test
    void backslashInPlaceholderIsRemoved() {
        MessageCatalog catalog = new MessageCatalog(
                Map.of("test", "Value: {v}"),
                Map.of()
        );

        String result = catalog.format("test", Map.of("v", "test\\test"));
        assertFalse(result.contains("\\"));
    }

    @Test
    void angleBracketsInPlaceholderAreRemoved() {
        MessageCatalog catalog = new MessageCatalog(
                Map.of("test", "Item: {item}"),
                Map.of()
        );

        String result = catalog.format("test", Map.of("item", "<bold>stolen</bold>"));
        assertFalse(result.contains("<bold>"));
        assertFalse(result.contains("</bold>"));
        assertTrue(result.contains("stolen"));
    }

    @Test
    void missingKeyReturnsDiagnosticMessage() {
        MessageCatalog catalog = new MessageCatalog(Map.of(), Map.of());

        String result = catalog.format("nonexistent");
        assertTrue(result.contains("Missing message"));
        assertTrue(result.contains("nonexistent"));
    }

    @Test
    void missingKeyFallsBackToEnglish() {
        MessageCatalog catalog = new MessageCatalog(
                Map.of("only-tr", "tr-value"),
                Map.of("only-en", "en-value")
        );

        String result = catalog.format("only-en");
        assertEquals("en-value", result);
    }

    @Test
    void nullPlaceholdersReturnsUnsubstituted() {
        MessageCatalog catalog = new MessageCatalog(
                Map.of("test", "Hello {name}!"),
                Map.of()
        );

        String result = catalog.format("test", null);
        assertEquals("Hello {name}!", result);
    }

    @Test
    void emptyPlaceholdersReturnsUnsubstituted() {
        MessageCatalog catalog = new MessageCatalog(
                Map.of("test", "Hello {name}!"),
                Map.of()
        );

        String result = catalog.format("test", Map.of());
        assertEquals("Hello {name}!", result);
    }

    @Test
    void multiplePlaceholdersAreAllSubstituted() {
        MessageCatalog catalog = new MessageCatalog(
                Map.of("test", "{a} and {b} and {c}"),
                Map.of()
        );

        String result = catalog.format("test", Map.of("a", "first", "b", "second", "c", "third"));
        assertEquals("first and second and third", result);
    }

    @Test
    void prefixIsPrependedToMessages() {
        MessageCatalog catalog = new MessageCatalog(
                Map.of("prefix", "[EC] ", "test", "Hello"),
                Map.of()
        );

        String result = catalog.format("test");
        assertEquals("[EC] Hello", result);
    }

    @Test
    void nullPlaceholderValueIsReplacedWithEmpty() {
        MessageCatalog catalog = new MessageCatalog(
                Map.of("test", "Value: {v}"),
                Map.of()
        );

        Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("v", null);
        String result = catalog.format("test", placeholders);
        assertEquals("Value: ", result);
    }

    @Test
    void injectionAttemptInKeyIsSanitized() {
        MessageCatalog catalog = new MessageCatalog(Map.of(), Map.of());

        String result = catalog.raw("<red>evil</red>");
        assertTrue(result.contains("Missing message"));
        assertFalse(result.contains("<red>evil"));
        assertFalse(result.contains("evil</red>"));
        assertTrue(result.contains("evil"));
    }
}
