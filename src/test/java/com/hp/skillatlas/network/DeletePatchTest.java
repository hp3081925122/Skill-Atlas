package com.hp.skillatlas.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hp.skillatlas.data.AtlasDefinition;
import com.hp.skillatlas.data.Category;
import com.hp.skillatlas.data.EditOps;
import com.hp.skillatlas.data.Icon;
import com.hp.skillatlas.data.PointPool;
import com.hp.skillatlas.data.Prerequisite;
import com.hp.skillatlas.data.SkillLevel;
import com.hp.skillatlas.data.SkillNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeletePatchTest {
    @Test
    void singleDeletionSurvivesTransportAndClearsPrerequisite() {
        AtlasDefinition server = atlas();
        AtlasDefinition client = server.copy();
        JsonArray ops = new JsonArray();
        ops.add(EditOps.deleteNodesOp(List.of("armor_12")));
        JsonObject patch = editAndTransfer(server, ops);

        assertTrue(patch.getAsJsonObject("nodes").has("armor_12"));
        assertTrue(patch.getAsJsonObject("nodes").get("armor_12").isJsonNull());
        EditOps.applyPatch(client, patch);
        client.validate();
        assertFalse(client.nodes.containsKey("armor_12"));
        assertTrue(client.nodes.get("armor_13").prerequisites.isEmpty());
        assertEquals(server.toJson(), client.toJson());
    }

    @Test
    void batchDeletionSurvivesTransport() {
        AtlasDefinition server = atlas();
        AtlasDefinition client = server.copy();
        JsonArray ops = new JsonArray();
        ops.add(EditOps.deleteNodesOp(List.of("armor_12", "armor_13")));

        EditOps.applyPatch(client, editAndTransfer(server, ops));
        client.validate();
        assertEquals(List.of("haste"), List.copyOf(client.nodes.keySet()));
        assertEquals(server.toJson(), client.toJson());
    }

    @Test
    void categoryDeletionSurvivesTransportAndKeepsOtherCategory() {
        AtlasDefinition server = atlas();
        AtlasDefinition client = server.copy();
        JsonArray ops = new JsonArray();
        ops.add(EditOps.deleteOp("delete_category", "combat"));

        EditOps.applyPatch(client, editAndTransfer(server, ops));
        client.validate();
        assertFalse(client.categories.containsKey("combat"));
        assertEquals(List.of("haste"), List.copyOf(client.nodes.keySet()));
        assertTrue(client.pools.containsKey("general"));
        assertEquals(server.toJson(), client.toJson());
    }

    private static JsonObject editAndTransfer(AtlasDefinition server, JsonArray ops) {
        EditOps.Changes changes = EditOps.apply(server, ops);
        server.validate();
        PayloadAssembler assembler = new PayloadAssembler(16);
        PayloadAssembler.Complete complete = null;
        List<Payload> chunks = Payload.split(Payload.PATCH, EditOps.patch(server, changes, 42), 16);
        assertTrue(chunks.size() > 1);
        for (Payload chunk : chunks) {
            complete = assembler.accept(chunk);
        }
        assertNotNull(complete);
        assertEquals(Payload.PATCH, complete.kind());
        assertEquals(42, complete.json().get("revision").getAsLong());
        return complete.json();
    }

    private static AtlasDefinition atlas() {
        AtlasDefinition d = new AtlasDefinition();
        d.pools.put("general", new PointPool("general", "通用", 0xECC873, 0));
        d.categories.put("combat", new Category("combat", "战斗", Icon.item("minecraft:iron_sword"), "general"));
        d.categories.put("mining", new Category("mining", "挖掘", Icon.item("minecraft:iron_pickaxe"), "general"));
        node(d, "armor_12", "combat");
        node(d, "armor_13", "combat").prerequisites.add(new Prerequisite("armor_12", true));
        node(d, "haste", "mining");
        d.validate();
        return d;
    }

    private static SkillNode node(AtlasDefinition d, String id, String category) {
        SkillNode node = new SkillNode();
        node.id = id;
        node.category = category;
        node.levels.add(new SkillLevel());
        d.nodes.put(id, node);
        return node;
    }
}
