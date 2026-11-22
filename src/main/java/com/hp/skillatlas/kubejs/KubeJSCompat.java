package com.hp.skillatlas.kubejs;

import com.hp.skillatlas.kubejs.client.ClientLifecycle;
import com.hp.skillatlas.kubejs.network.QuoteNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class KubeJSCompat {
    public static final String NAMESPACE = "skill_atlas_kubejs";

    private KubeJSCompat() {
    }

    public static void init(IEventBus bus) {
        QuoteNetwork.register(bus);
        NeoForge.EVENT_BUS.addListener(KubeJSCompat::tick);
        NeoForge.EVENT_BUS.addListener(KubeJSCompat::stopping);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientLifecycle.register();
        }
    }

    private static void tick(ServerTickEvent.Post event) {
        Hooks.tick();
    }

    private static void stopping(ServerStoppingEvent event) {
        Hooks.flush(null);
        CustomRewards.clear("stop");
    }
}
