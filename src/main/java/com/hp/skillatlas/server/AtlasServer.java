package com.hp.skillatlas.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hp.skillatlas.AtlasConfig;
import com.hp.skillatlas.ItemCosts;
import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.data.AtlasDefinition;
import com.hp.skillatlas.data.AtlasException;
import com.hp.skillatlas.data.AtlasLogic;
import com.hp.skillatlas.data.DefaultAtlas;
import com.hp.skillatlas.data.EditOps;
import com.hp.skillatlas.data.Json;
import com.hp.skillatlas.data.PlayerProgress;
import com.hp.skillatlas.data.SkillLevel;
import com.hp.skillatlas.data.SkillNode;
import com.hp.skillatlas.network.AtlasNetwork;
import com.hp.skillatlas.network.Payload;
import com.hp.skillatlas.network.PayloadAssembler;
import com.hp.skillatlas.network.ProgressPacket;
import com.hp.skillatlas.network.ResultPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AtlasServer {
    private static final int SAVE_DELAY = 40;
    private static final int RESYNC_COOLDOWN = 100;
    private static final int PROGRESS_COOLDOWN = 10;

    private static MinecraftServer server;
    private static ProgressData data;
    private static AtlasDefinition definition = new AtlasDefinition();
    private static long revision;
    private static boolean loadFailed;
    private static String loadError = "";
    private static int saveTimer = -1;
    private static int ticks;
    private static List<Payload> fullCache;
    private static final Map<UUID, PayloadAssembler> ASSEMBLERS = new HashMap<>();
    private static final Map<UUID, Integer> RESYNC_TIMES = new HashMap<>();
    private static final Map<UUID, Integer> PROGRESS_TIMES = new HashMap<>();
    private static final Set<UUID> PENDING_SYNC = new HashSet<>();

    private AtlasServer() {
    }

    public static AtlasDefinition definition() {
        return definition;
    }

    public static boolean loadFailed() {
        return loadFailed;
    }

    public static String loadError() {
        return loadError;
    }

    public static MinecraftServer server() {
        return server;
    }

    public static ProgressData data() {
        return data;
    }

    public static PlayerProgress progress(ServerPlayer player) {
        return data.of(player.getUUID());
    }

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(SkillAtlas.MOD_ID).resolve("atlas.json");
    }

    public static void start(MinecraftServer s) {
        server = s;
        data = ProgressData.get(s);
        definition = new AtlasDefinition();
        revision = System.currentTimeMillis();
        loadFailed = false;
        loadError = "";
        load();
    }

    public static void stop() {
        if (saveTimer >= 0) {
            save();
        }
        server = null;
        data = null;
        definition = new AtlasDefinition();
        fullCache = null;
        saveTimer = -1;
        ASSEMBLERS.clear();
        RESYNC_TIMES.clear();
        PROGRESS_TIMES.clear();
        PENDING_SYNC.clear();
        RewardManager.clear();
    }

    public static boolean load() {
        Path path = file();
        try {
            AtlasDefinition next;
            if (Files.exists(path)) {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                next = AtlasDefinition.fromJson(JsonParser.parseString(text).getAsJsonObject());
            } else {
                next = DefaultAtlas.create();
                next.validate();
                write(path, next);
                SkillAtlas.LOGGER.info("Created default skill atlas at {}", path);
            }
            definition = next;
            loadFailed = false;
            loadError = "";
            SkillAtlas.LOGGER.info("Loaded skill atlas with {} nodes, {} categories, {} rules", next.nodes.size(), next.categories.size(), next.rules.size());
        } catch (AtlasException e) {
            fail(path, e.getMessage());
        } catch (IOException | RuntimeException e) {
            fail(path, e.toString());
        }
        revision++;
        fullCache = null;
        PointTracker.rebuild(definition);
        return !loadFailed;
    }

    private static void fail(Path path, String error) {
        loadFailed = true;
        loadError = error;
        SkillAtlas.LOGGER.error("Failed to load skill atlas from {}: {}. Editing is disabled until the file is fixed and /skillatlas reload succeeds", path, error);
    }

    private static void write(Path path, AtlasDefinition d) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, Json.PRETTY.toJson(d.toJson()), StandardCharsets.UTF_8);
        if (Files.exists(path)) {
            Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void save() {
        saveTimer = -1;
        if (loadFailed) {
            return;
        }
        try {
            write(file(), definition);
        } catch (IOException e) {
            SkillAtlas.LOGGER.error("Failed to save skill atlas: {}", e.toString());
        }
    }

    public static void tick() {
        ticks++;
        if (saveTimer >= 0 && --saveTimer < 0) {
            save();
        }
        if (ticks % 10 == 0 && !PENDING_SYNC.isEmpty() && server != null) {
            for (UUID id : PENDING_SYNC) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    sendProgress(player);
                }
            }
            PENDING_SYNC.clear();
        }
    }

    public static boolean canEdit(ServerPlayer player) {
        return player.hasPermissions(AtlasConfig.EDIT_PERMISSION.get());
    }

    public static void markSync(ServerPlayer player) {
        PENDING_SYNC.add(player.getUUID());
    }

    public static void login(ServerPlayer player) {
        PlayerProgress p = progress(player);
        boolean changed = p.seed(definition);
        if (!loadFailed && p.migrate(definition, null)) {
            changed = true;
        }
        if (changed) {
            data.setDirty();
        }
        RewardManager.refresh(player);
        sendFull(player);
        sendProgress(player);
    }

    public static void logout(ServerPlayer player) {
        UUID id = player.getUUID();
        ASSEMBLERS.remove(id);
        RESYNC_TIMES.remove(id);
        PROGRESS_TIMES.remove(id);
        PENDING_SYNC.remove(id);
        RewardManager.forget(id);
    }

    private static List<Payload> full() {
        if (fullCache == null) {
            JsonObject o = new JsonObject();
            o.addProperty("revision", revision);
            o.addProperty("locked", loadFailed);
            o.add("definition", definition.toJson());
            fullCache = Payload.split(Payload.FULL, o, Payload.TO_CLIENT_CHUNK);
        }
        return fullCache;
    }

    public static void sendFull(ServerPlayer player) {
        for (Payload p : full()) {
            AtlasNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), p);
        }
    }

    public static void broadcastFull() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendFull(player);
        }
    }

    public static void sendProgress(ServerPlayer player) {
        CompoundTag tag = progress(player).save();
        tag.remove("payments");
        tag.remove("seeded");
        AtlasNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ProgressPacket(tag, canEdit(player)));
    }

    public static void result(ServerPlayer player, int request, boolean ok, String key, List<String> args) {
        AtlasNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ResultPacket(request, ok, key, args));
    }

    public static void receive(ServerPlayer player, Payload p) {
        UUID id = player.getUUID();
        if (p.kind == Payload.EDIT) {
            if (!canEdit(player)) {
                if (p.index == 0) {
                    result(player, 0, false, "skill_atlas.error.no_permission", List.of());
                }
                ASSEMBLERS.remove(id);
                return;
            }
        } else if (p.kind == Payload.RESYNC || p.kind == Payload.PROGRESS) {
            int cooldown = PROGRESS_COOLDOWN;
            Map<UUID, Integer> times = PROGRESS_TIMES;
            if (p.kind == Payload.RESYNC) {
                cooldown = RESYNC_COOLDOWN;
                times = RESYNC_TIMES;
            }
            Integer last = times.get(id);
            if (p.total != 1 || (last != null && ticks - last < cooldown)) {
                return;
            }
            times.put(id, ticks);
            if (p.kind == Payload.RESYNC) {
                sendFull(player);
            }
            sendProgress(player);
            return;
        } else {
            return;
        }
        PayloadAssembler.Complete c = ASSEMBLERS.computeIfAbsent(id, k -> new PayloadAssembler(Payload.TO_SERVER_CHUNK)).accept(p);
        if (c != null) {
            edit(player, c.json());
        }
    }

    private static void edit(ServerPlayer player, JsonObject json) {
        int request = 0;
        try {
            request = json.get("request").getAsInt();
        } catch (RuntimeException ignored) {
        }
        if (loadFailed) {
            result(player, request, false, "skill_atlas.error.load_failed", List.of());
            return;
        }
        AtlasDefinition next = definition.copy();
        EditOps.Changes changes;
        try {
            changes = EditOps.apply(next, Json.array(json, "ops"));
            next.validate();
            RegistryCheck.check(definition, next, changes, player);
        } catch (AtlasException e) {
            result(player, request, false, e.key(), e.args());
            return;
        } catch (RuntimeException e) {
            SkillAtlas.LOGGER.warn("Rejected malformed atlas edit from {}: {}", player.getGameProfile().getName(), e.toString());
            result(player, request, false, "skill_atlas.error.malformed", List.of());
            return;
        }
        if (!changes.isEmpty()) {
            definition = next;
            revision++;
            fullCache = null;
            saveTimer = SAVE_DELAY;
            PointTracker.rebuild(definition);
            List<Payload> patch = Payload.split(Payload.PATCH, EditOps.patch(definition, changes, revision), Payload.TO_CLIENT_CHUNK);
            for (ServerPlayer target : server.getPlayerList().getPlayers()) {
                for (Payload part : patch) {
                    AtlasNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), part);
                }
            }
            definitionChanged(changes.shrunk);
            SkillAtlas.LOGGER.debug("Applied atlas edit from {}: {} nodes, meta {}", player.getGameProfile().getName(), changes.nodes.size(), changes.meta);
        }
        result(player, request, true, "", List.of());
    }

    public static void definitionChanged(Set<String> shrunk) {
        boolean dirty = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerProgress p = progress(player);
            boolean changed = p.seed(definition);
            if (shrunk == null || !shrunk.isEmpty()) {
                if (p.migrate(definition, shrunk)) {
                    changed = true;
                }
            }
            if (changed) {
                dirty = true;
                markSync(player);
            }
            RewardManager.refresh(player);
        }
        if (dirty) {
            data.setDirty();
        }
    }

    public static boolean reload() {
        boolean ok = load();
        broadcastFull();
        if (ok) {
            definitionChanged(null);
        }
        return ok;
    }

    public static void learn(ServerPlayer player, String nodeId) {
        PlayerProgress p = progress(player);
        SkillNode n = definition.nodes.get(nodeId);
        AtlasLogic.Check check = AtlasLogic.check(definition, n, p, costs -> ItemCosts.has(player.getInventory(), costs));
        if (check == AtlasLogic.Check.OK && !AtlasLogic.visible(definition, n, p)) {
            check = AtlasLogic.Check.LOCKED;
        }
        int level = p.level(nodeId);
        if (check == AtlasLogic.Check.OK && !ItemCosts.consume(player.getInventory(), n.levels.get(level).items)) {
            check = AtlasLogic.Check.NO_ITEMS;
        }
        if (check != AtlasLogic.Check.OK) {
            result(player, ResultPacket.LEARN, false, "skill_atlas.learn." + check.name().toLowerCase(Locale.ROOT), List.of(nodeId));
            sendProgress(player);
            return;
        }
        SkillLevel next = n.levels.get(level);
        if (!next.items.isEmpty()) {
            SkillAtlas.LOGGER.debug("Consumed {} delivery item requirement(s) from {} for skill {} level {}", next.items.size(), player.getGameProfile().getName(), n.id, level + 1);
        }
        String pool = definition.poolOf(n);
        p.addPoints(pool, -next.cost);
        p.alignPayments(n.id, level);
        p.recordPayment(n.id, pool, next.cost);
        p.setLevel(n.id, level + 1);
        data.setDirty();
        RewardManager.refresh(player);
        RewardManager.runCommands(player, next);
        sendProgress(player);
        result(player, ResultPacket.LEARN, true, "skill_atlas.learn.ok", List.of(n.id, String.valueOf(level + 1)));
    }

    public static int grant(ServerPlayer player, SkillNode n, int target) {
        PlayerProgress p = progress(player);
        int current = p.level(n.id);
        int level = Math.max(0, Math.min(n.maxLevel(), target));
        if (level < current) {
            p.truncate(n.id, level);
        } else if (level > current) {
            p.alignPayments(n.id, current);
            p.alignPayments(n.id, level);
            p.setLevel(n.id, level);
        }
        data.setDirty();
        RewardManager.refresh(player);
        for (int i = current; i < level; i++) {
            RewardManager.runCommands(player, n.levels.get(i));
        }
        sendProgress(player);
        return level;
    }

    public static Map<String, Long> reset(ServerPlayer player, Collection<String> nodes) {
        PlayerProgress p = progress(player);
        Map<String, Long> refunds = new HashMap<>();
        List<String> ids = new ArrayList<>(nodes);
        for (String id : ids) {
            p.truncate(id, 0).forEach((pool, v) -> refunds.merge(pool, v, Long::sum));
        }
        data.setDirty();
        RewardManager.refresh(player);
        sendProgress(player);
        return refunds;
    }
}
