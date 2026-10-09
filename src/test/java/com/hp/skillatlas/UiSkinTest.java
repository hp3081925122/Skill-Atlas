package com.hp.skillatlas;

import com.google.gson.JsonObject;
import com.hp.skillatlas.client.UiSkin;
import com.hp.skillatlas.core.StrictJson;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class UiSkinTest {
    private UiSkin bundled() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/skill_atlas/ui/default_theme.json")) {
            assertNotNull(stream); return StrictJson.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8), UiSkin.class);
        }
    }
    @Test void allBundledSpriteRegionsFitTheirActualPng() throws Exception {
        UiSkin skin = bundled(); skin.validate(skin); Set<String> decoded = new HashSet<>();
        for (var sprite : skin.textures.values()) {
            if (!decoded.add(sprite.texture)) continue;
            String path = "/assets/" + sprite.texture.replace(':', '/');
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path); var image = ImageIO.read(stream); assertNotNull(image);
                assertEquals(sprite.width, image.getWidth()); assertEquals(sprite.height, image.getHeight());
            }
        }
    }
    @Test void partialPackOverrideKeepsOtherSpriteFieldsAndBaseImmutable() throws Exception {
        UiSkin base = bundled();
        JsonObject overlay = StrictJson.parse("{\"textures\":{\"button\":{\"texture\":\"custom:textures/ui/button.png\"}},\"colors\":{\"title\":\"#FF123456\"}}", JsonObject.class);
        UiSkin custom = UiSkin.merge(base, overlay);
        assertEquals("custom:textures/ui/button.png", custom.textures.get("button").texture);
        assertEquals(base.textures.get("button").border, custom.textures.get("button").border); assertEquals(base.textures.size(), custom.textures.size());
        assertEquals(0xff123456, custom.color("title")); assertNotEquals(custom.color("title"), base.color("title"));
        assertNotEquals(custom.textures.get("button").texture, base.textures.get("button").texture);
    }
    @Test void invalidPathsDimensionsBordersAndAlphaAreRejected() throws Exception {
        UiSkin base = bundled();
        for (String override : List.of("{\"texture\":\"custom:../escape.png\"}", "{\"width\":0}", "{\"regionWidth\":100}", "{\"border\":16}", "{\"alpha\":2}", "{\"tint\":\"blue\"}", "null")) {
            JsonObject overlay = StrictJson.parse("{\"textures\":{\"button\":" + override + "}}", JsonObject.class);
            assertThrows(IllegalArgumentException.class, () -> UiSkin.merge(base, overlay), override);
        }
    }
    @Test void malformedColorsMetricsAndUnknownSlotsAreRejected() throws Exception {
        UiSkin base = bundled();
        for (String text : List.of("{\"colors\":{\"title\":\"red\"}}", "{\"metrics\":{\"tooltipPadding\":1000}}", "{\"metrics\":{\"lineWidth\":0}}", "{\"textures\":{\"typo\":{}}}", "{\"schemaVersion\":2}"))
            assertThrows(IllegalArgumentException.class, () -> UiSkin.merge(base, StrictJson.parse(text, JsonObject.class)), text);
    }
    @Test void nineSliceDestinationCutsNeverOverlapAtSmallSizes() {
        for (int size = 0; size < 300; size++) for (int border = 0; border < 32; border++) {
            int[] cuts = UiSkin.destinationCuts(size, border); assertEquals(0, cuts[0]); assertEquals(size, cuts[3]);
            for (int i = 0; i < 3; i++) assertTrue(cuts[i] <= cuts[i + 1]);
            assertEquals(size, cuts[1] + (cuts[2] - cuts[1]) + (cuts[3] - cuts[2]));
        }
    }
    @Test void emptyResourcePackOverrideRetainsAllSlots() throws Exception {
        UiSkin base = bundled(); UiSkin copy = UiSkin.merge(base, new JsonObject());
        assertEquals(base.textures.keySet(), copy.textures.keySet()); assertEquals(base.metrics, copy.metrics); assertEquals(base.colors, copy.colors);
        copy.textures.get("button").border = 1; assertNotEquals(1, base.textures.get("button").border);
    }
}
