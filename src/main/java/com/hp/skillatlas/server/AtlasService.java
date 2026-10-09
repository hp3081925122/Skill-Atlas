package com.hp.skillatlas.server;

import com.google.gson.*;
import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.core.*;
import com.hp.skillatlas.model.*;
import com.hp.skillatlas.network.AtlasNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.function.Predicate;

public final class AtlasService {
    private static AtlasService instance;
    public final MinecraftServer server;
    public final AtlasRepository repository = new AtlasRepository();
    public final RewardRuntime rewards = new RewardRuntime();
    public final AtlasData data;
    private final Set<UUID> dirty = new HashSet<>();
    private final Map<UUID, List<PendingBreak>> breaks = new HashMap<>();
    private record PendingBreak(ServerLevel world, BlockPos pos, BlockState state, long tick) {}
    private AtlasService(MinecraftServer server) { this.server = server; this.data = AtlasData.get(server); repository.load(); }
    public static void start(MinecraftServer server) { instance = new AtlasService(server); }
    public static AtlasService get() { return instance; }
    public static void stop() { if (instance != null) instance.rewards.clear(); instance = null; AtlasNetwork.clearServer(); }
    public Definition definition() { return repository.definition; }
    public PlayerProgress progress(ServerPlayer p) { return data.player(p.getUUID()); }
    public void join(ServerPlayer p) {
        PlayerProgress progress = progress(p); AtlasLogic.reconcile(definition(), progress); data.setDirty(); rewards.rebuild(p, definition(), progress); snapshot(p, false);
    }
    public void logout(ServerPlayer p) { rewards.remove(p); dirty.remove(p.getUUID()); breaks.remove(p.getUUID()); AtlasNetwork.disconnect(p.getUUID()); }
    public void changed(ServerPlayer p) { data.setDirty(); dirty.add(p.getUUID()); }
    public void tick(ServerPlayer p) {
        List<PendingBreak> pending = breaks.get(p.getUUID());
        if (pending != null) {
            var iterator = pending.iterator();
            while (iterator.hasNext()) {
                var broken = iterator.next();
                if (broken.world.getGameTime() <= broken.tick) continue;
                iterator.remove();
                if (!broken.world.getBlockState(broken.pos).equals(broken.state))
                    trigger(p, "mine", target -> target.startsWith("#") ? broken.state.is(TagKey.create(Registries.BLOCK, ResourceLocation.parse(target.substring(1)))) : ForgeRegistries.BLOCKS.getKey(broken.state.getBlock()).toString().equals(target));
            }
            if (pending.isEmpty()) breaks.remove(p.getUUID());
        }
        rewards.tick(p);
        if (p.tickCount % 20 == 0 && dirty.remove(p.getUUID())) syncProgress(p);
    }
    public void pendingBreak(ServerPlayer p, ServerLevel world, BlockPos pos, BlockState state) {
        List<PendingBreak> list = breaks.computeIfAbsent(p.getUUID(), id -> new ArrayList<>());
        if (list.size() < 4096 && list.stream().noneMatch(b -> b.world == world && b.pos.equals(pos))) list.add(new PendingBreak(world, pos, state, world.getGameTime()));
    }
    public void trigger(ServerPlayer p, String event, Predicate<String> matches) {
        boolean changed = false;
        for (var entry : definition().rules.entrySet()) if (entry.getValue().event.equals(event) && matches.test(entry.getValue().target)) {
            AtlasLogic.trigger(definition(), progress(p), entry.getKey(), entry.getValue()); changed = true;
        }
        if (changed) changed(p);
    }
    private JsonObject message(String op) { JsonObject o = new JsonObject(); o.addProperty("op", op); return o; }
    public void status(ServerPlayer p, String code, String detail) {
        JsonObject o = message("status"); o.addProperty("code", code); o.addProperty("detail", detail == null ? "" : detail); AtlasNetwork.send(p, Definition.JSON.toJson(o));
    }
    public void syncProgress(ServerPlayer p) {
        JsonObject o = message("progress"); o.add("progress", Definition.JSON.toJsonTree(progress(p))); o.addProperty("revision", definition().revision); AtlasNetwork.send(p, Definition.JSON.toJson(o));
    }
    public void snapshot(ServerPlayer p, boolean open) {
        JsonObject o = message("snapshot"); o.add("definition", Definition.JSON.toJsonTree(definition())); o.add("progress", Definition.JSON.toJsonTree(progress(p)));
        o.addProperty("editor", p.hasPermissions(2)); o.addProperty("open", open);
        if (p.hasPermissions(2)) {
            JsonObject registries = new JsonObject();
            registries.add("attribute", Definition.JSON.toJsonTree(ForgeRegistries.ATTRIBUTES.getKeys().stream().map(Object::toString).sorted().toList()));
            registries.add("effect", Definition.JSON.toJsonTree(ForgeRegistries.MOB_EFFECTS.getKeys().stream().map(Object::toString).sorted().toList()));
            registries.add("item", Definition.JSON.toJsonTree(ForgeRegistries.ITEMS.getKeys().stream().map(Object::toString).sorted().toList()));
            registries.add("kill", Definition.JSON.toJsonTree(ForgeRegistries.ENTITY_TYPES.getKeys().stream().map(Object::toString).sorted().toList()));
            registries.add("mine", Definition.JSON.toJsonTree(ForgeRegistries.BLOCKS.getKeys().stream().map(Object::toString).sorted().toList()));
            o.add("registries", registries);
        }
        AtlasNetwork.send(p, Definition.JSON.toJson(o));
    }
    private void draft(ServerPlayer p, Definition d) { JsonObject o = message("draft"); o.add("definition", Definition.JSON.toJsonTree(d)); AtlasNetwork.send(p, Definition.JSON.toJson(o)); }
    public void request(ServerPlayer p, String text) {
        try {
            JsonObject o = StrictJson.parse(text, JsonObject.class); String op = o.get("op").getAsString();
            if ("open".equals(op)) { snapshot(p, true); return; }
            if ("learn".equals(op)) {
                if (o.get("revision").getAsLong() != definition().revision) { status(p, "stale", ""); snapshot(p, false); return; }
                String result = AtlasLogic.learn(definition(), progress(p), o.get("node").getAsString(), o.get("level").getAsInt());
                if (result.equals("ok")) { changed(p); rewards.rebuild(p, definition(), progress(p)); syncProgress(p); }
                status(p, result, ""); return;
            }
            if (!p.hasPermissions(2)) { status(p, "permission", ""); return; }
            switch (op) {
                case "publish" -> {
                    Definition d = Definition.JSON.fromJson(o.get("definition"), Definition.class);
                    repository.publish(d, data, o.get("revision").getAsLong());
                    for (ServerPlayer online : server.getPlayerList().getPlayers()) { AtlasLogic.reconcile(definition(), progress(online)); rewards.rebuild(online, definition(), progress(online)); snapshot(online, false); }
                    data.setDirty(); status(p, "published", "");
                }
                case "save_draft" -> {
                    Definition d = Definition.JSON.fromJson(o.get("definition"), Definition.class);
                    List<String> errors = AtlasLogic.validate(d); if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
                    AtlasRepository.write(repository.draft(p.getUUID()), Definition.JSON.toJson(d)); status(p, "saved", "");
                }
                case "load_draft" -> {
                    Definition d = repository.read(repository.draft(p.getUUID()));
                    List<String> errors = AtlasLogic.validate(d); if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
                    draft(p, d); status(p, "loaded", "");
                }
                case "import" -> {
                    Definition d = repository.read(repository.namedFile(o.get("name").getAsString()));
                    List<String> errors = repository.validate(d); if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
                    AtlasRepository.write(repository.directory.resolve("import.previous.json"), Definition.JSON.toJson(definition()));
                    d.revision = definition().revision; draft(p, d); status(p, "imported", "");
                }
                case "export" -> {
                    Definition d = Definition.JSON.fromJson(o.get("definition"), Definition.class);
                    AtlasRepository.write(repository.exportFile(o.get("name").getAsString()), Definition.JSON.toJson(d)); status(p, "exported", "");
                }
                default -> status(p, "error", "Unknown request");
            }
        } catch (Exception ex) { SkillAtlas.LOGGER.warn("Skill Atlas request failed for {}: {}", p.getUUID(), ex.getMessage()); status(p, "error", ex.getMessage()); }
    }
    public void reset(ServerPlayer p, String node) { AtlasLogic.reset(definition(), progress(p), node); changed(p); rewards.rebuild(p, definition(), progress(p)); syncProgress(p); }
    public void reload() throws java.io.IOException {
        Definition candidate = repository.read(repository.directory.resolve("atlas.json"));
        repository.publish(candidate, data, definition().revision);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            AtlasLogic.reconcile(definition(), progress(online)); rewards.rebuild(online, definition(), progress(online)); snapshot(online, false);
        }
        data.setDirty();
    }
}
