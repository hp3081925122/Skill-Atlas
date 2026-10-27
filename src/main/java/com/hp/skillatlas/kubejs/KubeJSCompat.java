package com.hp.skillatlas.kubejs;

import com.hp.skillatlas.kubejs.client.ClientLifecycle;
import com.hp.skillatlas.kubejs.network.QuoteNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

public final class KubeJSCompat {
    public static final String NAMESPACE = "skill_atlas_kubejs";

    private KubeJSCompat() {
    }

    public static void init() {
        QuoteNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(KubeJSCompat::tick);
        MinecraftForge.EVENT_BUS.addListener(KubeJSCompat::stopping);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientLifecycle.register();
        }
    }

    private static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Hooks.tick();
        }
    }

    private static void stopping(ServerStoppingEvent event) {
        Hooks.flush(null);
        CustomRewards.clear("stop");
    }
}
