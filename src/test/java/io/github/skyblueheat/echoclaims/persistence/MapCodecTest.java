package io.github.skyblueheat.echoclaims.persistence;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapCodecTest {

    @Test
    void emptyMapEncodesToEmptyString() {
        assertEquals("", MapCodec.encodeIntMap(Map.of()));
        assertEquals("", MapCodec.encodeStringMap(Map.of()));
    }

    @Test
    void nullMapEncodesToEmptyString() {
        assertEquals("", MapCodec.encodeIntMap(null));
        assertEquals("", MapCodec.encodeStringMap(null));
    }

    @Test
    void intMapRoundTrip() {
        Map<String, Integer> input = new LinkedHashMap<>();
        input.put("minecraft:sharpness", 5);
        input.put("minecraft:unbreaking", 3);

        String encoded = MapCodec.encodeIntMap(input);
        Map<String, Integer> decoded = MapCodec.decodeIntMap(encoded);

        assertEquals(input, decoded);
    }

    @Test
    void stringMapRoundTrip() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("keepInventory", "false");
        input.put("lastDamageCause", "ENTITY_ATTACK");

        String encoded = MapCodec.encodeStringMap(input);
        Map<String, String> decoded = MapCodec.decodeStringMap(encoded);

        assertEquals(input, decoded);
    }

    @Test
    void specialCharactersAreEscaped() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("key=with=equals", "value;with;semicolons");
        input.put("key\\with\\backslash", "normal");

        String encoded = MapCodec.encodeStringMap(input);
        Map<String, String> decoded = MapCodec.decodeStringMap(encoded);

        assertEquals(input, decoded);
    }

    @Test
    void emptyStringDecodesToEmptyMap() {
        assertTrue(MapCodec.decodeIntMap("").isEmpty());
        assertTrue(MapCodec.decodeIntMap(null).isEmpty());
        assertTrue(MapCodec.decodeStringMap("").isEmpty());
        assertTrue(MapCodec.decodeStringMap(null).isEmpty());
    }

    @Test
    void malformedIntValuesAreSkipped() {
        Map<String, Integer> decoded = MapCodec.decodeIntMap("sharpness=5;broken=notanumber;unbreaking=3");
        assertEquals(2, decoded.size());
        assertEquals(5, decoded.get("sharpness"));
        assertEquals(3, decoded.get("unbreaking"));
    }
}
