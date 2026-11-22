package com.hp.skillatlas.kubejs.mixin;

import com.hp.skillatlas.data.*;
import com.hp.skillatlas.server.AtlasServer;
import com.hp.skillatlas.kubejs.Hooks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;

@Mixin(value = AtlasServer.class, remap = false)
public abstract class AtlasServerMixin {
    @Shadow private static AtlasDefinition definition;
    @Shadow private static long revision;

    @WrapMethod(method = "learn")
    private static void atlasKjsLearn(ServerPlayer player, String node, Operation<Void> original) {
        Hooks.learn(player, node, () -> original.call(player, node));
    }
    @ModifyExpressionValue(method = "learn", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0))
    private static Object atlasKjsQuotedNode(Object node) { return Hooks.quotedNode(node); }
    @WrapMethod(method = "grant")
    private static int atlasKjsGrant(ServerPlayer player, SkillNode node, int target, Operation<Integer> original) {
        return Hooks.grant(player, node, () -> original.call(player, node, target));
    }
    @WrapMethod(method = "reset")
    private static Map<String, Long> atlasKjsReset(ServerPlayer player, Collection<String> nodes, Operation<Map<String, Long>> original) {
        return Hooks.reset(player, nodes, () -> original.call(player, nodes));
    }
    @Inject(method = "progress", at = @At("RETURN"))
    private static void atlasKjsOwner(ServerPlayer player, CallbackInfoReturnable<PlayerProgress> cir) {
        Hooks.owner(player, cir.getReturnValue());
    }
    @Inject(method = "result", at = @At("HEAD"))
    private static void atlasKjsResult(ServerPlayer player, int request, boolean ok, String key, List<String> args, CallbackInfo ci) {
        Hooks.result(player, request, ok, key, args);
    }
    @Redirect(method = "load", at = @At(value = "FIELD", target = "Lcom/hp/skillatlas/server/AtlasServer;definition:Lcom/hp/skillatlas/data/AtlasDefinition;", opcode = 179))
    private static void atlasKjsDefinition(AtlasDefinition next) {
        definition = Hooks.overlay(next);
    }
    @Inject(method = "load", at = @At("RETURN"))
    private static void atlasKjsRevision(CallbackInfoReturnable<Boolean> cir) { Hooks.revision(revision); }
    @Inject(method = "start", at = @At("RETURN"))
    private static void atlasKjsStart(net.minecraft.server.MinecraftServer server, CallbackInfo ci) { Hooks.loaded(!AtlasServer.loadFailed()); }
    @Inject(method = "reload", at = @At("RETURN"))
    private static void atlasKjsReload(CallbackInfoReturnable<Boolean> cir) { Hooks.loaded(cir.getReturnValue()); }
    @WrapMethod(method = "definitionChanged")
    private static void atlasKjsDefinitionChanged(Set<String> shrunk, Operation<Void> original) {
        Hooks.revision(revision);
        Hooks.definitionChange(() -> original.call(shrunk));
    }
    @Inject(method = "canEdit", at = @At("RETURN"), cancellable = true)
    private static void atlasKjsReadOnly(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (Hooks.managed()) cir.setReturnValue(false);
    }
    @ModifyArg(method = "save", at = @At(value = "INVOKE", target = "Lcom/hp/skillatlas/server/AtlasServer;write(Ljava/nio/file/Path;Lcom/hp/skillatlas/data/AtlasDefinition;)V"), index = 1)
    private static AtlasDefinition atlasKjsSaveBase(AtlasDefinition current) { return Hooks.saveDefinition(current); }
    @Inject(method = "logout", at = @At("HEAD"))
    private static void atlasKjsLogout(ServerPlayer player, CallbackInfo ci) { Hooks.flush(player); com.hp.skillatlas.kubejs.Quotes.forget(player); com.hp.skillatlas.kubejs.Navigation.forget(player); }
    @WrapMethod(method = "login")
    private static void atlasKjsLogin(ServerPlayer player, Operation<Void> original) {
        Hooks.login(player, () -> original.call(player));
    }
    @WrapMethod(method = "stop")
    private static void atlasKjsStop(Operation<Void> original) {
        try { Hooks.flush(null); original.call(); } finally { Hooks.clear(); }
    }
}
