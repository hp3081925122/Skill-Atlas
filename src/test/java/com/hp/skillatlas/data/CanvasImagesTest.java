package com.hp.skillatlas.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CanvasImagesTest {
    private static CanvasImage picture(String id) {
        CanvasImage image = new CanvasImage();
        image.id = id;
        image.texture = "minecraft:textures/block/stone.png";
        image.x = -40;
        image.y = 80;
        image.width = 240;
        image.height = 120;
        return image;
    }

    @Test
    void categoryCopiesAndFileRoundTripsKeepIndependentImages() {
        AtlasDefinition d = DefaultAtlas.create();
        Category c = d.categories.get("combat");
        c.images.add(picture("image_1"));
        Category copy = c.copy();
        copy.images.get(0).width = 512;
        assertEquals(240, c.images.get(0).width);
        AtlasDefinition loaded = AtlasDefinition.fromJson(d.toJson());
        assertEquals(c.toJson(), loaded.categories.get("combat").toJson());
        assertEquals(1, loaded.categories.get("combat").images.size());
        assertEquals("minecraft:textures/block/stone.png", loaded.categories.get("combat").images.get(0).texture);
    }

    @Test
    void imageChangesAndDeletionTravelThroughTheRealCategoryPatch() {
        AtlasDefinition server = DefaultAtlas.create();
        AtlasDefinition client = server.copy();
        Category c = server.categories.get("combat").copy();
        c.images.add(picture("image_1"));
        apply(server, client, EditOps.categoryOp(c, false), 1);
        assertEquals(c.toJson(), client.categories.get("combat").toJson());
        c.images.get(0).x = 192;
        c.images.get(0).height = 400;
        apply(server, client, EditOps.categoryOp(c, false), 2);
        assertEquals(192, client.categories.get("combat").images.get(0).x);
        assertEquals(400, client.categories.get("combat").images.get(0).height);
        c.images.clear();
        apply(server, client, EditOps.categoryOp(c, false), 3);
        assertTrue(client.categories.get("combat").images.isEmpty());
        c.images.add(picture("image_2"));
        apply(server, client, EditOps.categoryOp(c, false), 4);
        apply(server, client, EditOps.deleteOp("delete_category", "combat"), 5);
        assertFalse(client.categories.containsKey("combat"));
    }

    @Test
    void invalidSizesPathsDuplicateIdsAndExcessImagesAreRejected() {
        JsonObject invalid = picture("image_1").toJson();
        invalid.addProperty("width", 0);
        assertThrows(AtlasException.class, () -> CanvasImage.fromJson(invalid));
        invalid.addProperty("width", CanvasImage.MAX_SIZE + 1);
        assertThrows(AtlasException.class, () -> CanvasImage.fromJson(invalid));
        invalid.addProperty("width", 240);
        invalid.addProperty("texture", "C:\\bad.png");
        assertThrows(AtlasException.class, () -> CanvasImage.fromJson(invalid));
        Category c = DefaultAtlas.create().categories.get("combat");
        c.images.add(picture("same"));
        c.images.add(picture("same"));
        assertThrows(AtlasException.class, () -> Category.fromJson(c.toJson()));
        c.images.clear();
        for (int i = 0; i < Category.MAX_IMAGES; i++) {
            c.images.add(picture("image_" + i));
        }
        assertEquals(Category.MAX_IMAGES, Category.fromJson(c.toJson()).images.size());
        c.images.add(picture("image_over_limit"));
        assertThrows(AtlasException.class, () -> Category.fromJson(c.toJson()));
    }

    private static void apply(AtlasDefinition server, AtlasDefinition client, JsonObject operation, int revision) {
        JsonArray ops = new JsonArray();
        ops.add(operation);
        EditOps.Changes changes = EditOps.apply(server, ops);
        server.validate();
        assertTrue(changes.meta);
        EditOps.applyPatch(client, EditOps.patch(server, changes, revision));
    }
}
