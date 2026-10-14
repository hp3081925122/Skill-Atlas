package com.hp.skillatlas.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hp.skillatlas.SkillAtlas;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PayloadAssembler {
    public record Complete(byte kind, JsonObject json) {
    }

    private static final int MAX_PENDING = 4;

    private static final class Partial {
        final byte kind;
        final byte[][] parts;
        int received;
        int size;

        Partial(byte kind, int total) {
            this.kind = kind;
            this.parts = new byte[total][];
        }
    }

    private final Map<Integer, Partial> pending = new LinkedHashMap<>();
    private final int chunk;

    public PayloadAssembler(int chunk) {
        this.chunk = chunk;
    }

    public Complete accept(Payload p) {
        if (p.total <= 0 || p.index < 0 || p.index >= p.total || p.data.length > chunk
                || (long) p.total * chunk > Payload.MAX_COMPRESSED + (long) chunk) {
            SkillAtlas.LOGGER.warn("Dropped malformed payload chunk {}/{} of transfer {}", p.index, p.total, p.transfer);
            pending.remove(p.transfer);
            return null;
        }
        Partial part = pending.get(p.transfer);
        if (part == null) {
            if (pending.size() >= MAX_PENDING) {
                Iterator<Integer> it = pending.keySet().iterator();
                it.next();
                it.remove();
            }
            part = new Partial(p.kind, p.total);
            pending.put(p.transfer, part);
        }
        if (part.parts.length != p.total || part.kind != p.kind || part.parts[p.index] != null) {
            pending.remove(p.transfer);
            return null;
        }
        part.parts[p.index] = p.data;
        part.received++;
        part.size += p.data.length;
        if (part.size > Payload.MAX_COMPRESSED) {
            pending.remove(p.transfer);
            SkillAtlas.LOGGER.warn("Dropped oversized payload transfer {}", p.transfer);
            return null;
        }
        if (part.received < part.parts.length) {
            return null;
        }
        pending.remove(p.transfer);
        ByteArrayOutputStream joined = new ByteArrayOutputStream(part.size);
        for (byte[] b : part.parts) {
            joined.writeBytes(b);
        }
        try {
            String text = new String(Payload.gunzip(joined.toByteArray()), StandardCharsets.UTF_8);
            return new Complete(part.kind, JsonParser.parseString(text).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            SkillAtlas.LOGGER.warn("Failed to decode payload transfer {}: {}", p.transfer, e.toString());
            return null;
        }
    }

    public void clear() {
        pending.clear();
    }
}
