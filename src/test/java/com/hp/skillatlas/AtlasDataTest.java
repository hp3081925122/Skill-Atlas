package com.hp.skillatlas;

import com.hp.skillatlas.server.AtlasData;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class AtlasDataTest {
    @Test void largePlayerSaveRoundTripsCompressedNbt() throws Exception {
        AtlasData data = new AtlasData(); UUID first = null;
        for (int i = 0; i < 200; i++) {
            UUID id = UUID.randomUUID(); if (first == null) first = id;
            var progress = data.player(id); progress.balance("general").points = 42;
            for (int k = 0; k < 30; k++) progress.counters.put("rule_" + k, 7L);
        }
        CompoundTag tag = data.save(new CompoundTag()); assertTrue(tag.getByteArray("players").length > 65535);
        ByteArrayOutputStream output = new ByteArrayOutputStream(); NbtIo.writeCompressed(tag, output);
        CompoundTag restored = NbtIo.readCompressed(new ByteArrayInputStream(output.toByteArray()));
        AtlasData loaded = AtlasData.load(restored); assertEquals(200, loaded.players.size());
        assertEquals(42, loaded.player(first).balance("general").points); assertEquals(7L, loaded.player(first).counters.get("rule_29"));
    }
}
