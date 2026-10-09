package com.hp.skillatlas.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ChunkAssembler {
    public static final int CHUNK = 24576;
    public static final int MAX_BYTES = 8_000_000;
    private final Map<UUID, Transfer> transfers = new HashMap<>();
    private static final class Transfer {
        long id, touched; int total, next; ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    }
    public String accept(UUID sender, long id, int index, int total, byte[] bytes, long now) {
        transfers.entrySet().removeIf(e -> now - e.getValue().touched > 30000);
        if (total < 1 || total > (MAX_BYTES + CHUNK - 1) / CHUNK || index < 0 || index >= total || bytes.length > CHUNK)
            throw new IllegalArgumentException("Invalid packet fragment");
        if (index == 0) {
            if (transfers.size() >= 16 && !transfers.containsKey(sender)) throw new IllegalArgumentException("Too many pending transfers");
            Transfer t = new Transfer(); t.id = id; t.total = total; t.touched = now; transfers.put(sender, t);
        }
        Transfer t = transfers.get(sender);
        if (t == null || t.id != id || t.next != index || t.total != total || t.bytes.size() + bytes.length > MAX_BYTES) {
            transfers.remove(sender); throw new IllegalArgumentException("Invalid fragment sequence");
        }
        t.bytes.writeBytes(bytes); t.next++; t.touched = now;
        if (t.next < total) return null;
        transfers.remove(sender); return t.bytes.toString(StandardCharsets.UTF_8);
    }
    public void remove(UUID id) { transfers.remove(id); }
    public void clear() { transfers.clear(); }
}
