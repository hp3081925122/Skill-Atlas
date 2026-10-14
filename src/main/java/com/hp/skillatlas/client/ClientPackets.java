package com.hp.skillatlas.client;

import com.hp.skillatlas.client.screen.AtlasScreen;
import com.hp.skillatlas.data.PlayerProgress;
import com.hp.skillatlas.network.Payload;
import com.hp.skillatlas.network.PayloadAssembler;
import com.hp.skillatlas.network.ProgressPacket;
import com.hp.skillatlas.network.ResultPacket;

public final class ClientPackets {
    private static final PayloadAssembler ASSEMBLER = new PayloadAssembler(Payload.TO_CLIENT_CHUNK);

    private ClientPackets() {
    }

    public static void payload(Payload p) {
        PayloadAssembler.Complete c = ASSEMBLER.accept(p);
        if (c == null) {
            return;
        }
        if (c.kind() == Payload.FULL) {
            ClientAtlas.onFull(c.json());
        } else if (c.kind() == Payload.PATCH) {
            ClientAtlas.onPatch(c.json());
        }
    }

    public static void progress(ProgressPacket p) {
        ClientAtlas.onProgress(PlayerProgress.load(p.progress()), p.canEdit());
    }

    public static void result(ResultPacket r) {
        ClientAtlas.onResult(r);
        AtlasScreen screen = AtlasScreen.current();
        if (screen != null) {
            screen.onResult(r);
        }
    }

    public static void reset() {
        ASSEMBLER.clear();
        ClientAtlas.reset();
    }
}
