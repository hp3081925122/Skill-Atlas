package com.hp.skillatlas.server;

import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.core.*;
import com.hp.skillatlas.model.*;
import com.hp.skillatlas.api.RewardTypes;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.TagParser;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class AtlasRepository {
    public final Path directory = FMLPaths.CONFIGDIR.get().resolve("skill_atlas");
    private final Path active = directory.resolve("atlas.json");
    private final Path backup = directory.resolve("atlas.previous.json");
    public Definition definition = Definition.defaults();
    public void load() {
        try {
            Files.createDirectories(directory);
            if (!Files.exists(active)) { write(active, Definition.JSON.toJson(definition)); return; }
            Definition candidate = read(active); List<String> errors = validate(candidate);
            if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
            definition = candidate;
        } catch (Exception ex) {
            SkillAtlas.LOGGER.error("Failed to load Skill Atlas definition; retaining valid definition", ex);
            try { if (Files.exists(backup)) { Definition previous = read(backup); if (validate(previous).isEmpty()) definition = previous; } }
            catch (Exception backupError) { SkillAtlas.LOGGER.error("Failed to load definition backup", backupError); }
        }
    }
    public Definition read(Path path) throws java.io.IOException {
        if (Files.size(path) > 4_000_000) throw new IllegalArgumentException("Definition exceeds 4 MB");
        return StrictJson.parse(Files.readString(path, StandardCharsets.UTF_8), Definition.class);
    }
    public static void write(Path path, String content) throws java.io.IOException {
        Files.createDirectories(path.getParent()); Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
    }
    public void publish(Definition candidate, AtlasData data, long expected) throws java.io.IOException {
        if (expected != definition.revision) throw new IllegalArgumentException("Definition changed; reopen the editor before publishing");
        List<String> errors = validate(candidate);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
        for (var p : data.players.values()) {
            for (var entry : p.pools.entrySet()) if (!candidate.pools.containsKey(entry.getKey()) && (entry.getValue().points != 0 || entry.getValue().experience != 0 || entry.getValue().level != 0))
                throw new IllegalArgumentException("Pool still has player balances: " + entry.getKey());
            for (var learned : p.learned.values()) for (var payment : learned.payments) if (!candidate.pools.containsKey(payment.pool))
                throw new IllegalArgumentException("Pool is referenced by payment history: " + payment.pool);
        }
        candidate.revision = Math.max(System.currentTimeMillis(), definition.revision + 1);
        String text = Definition.JSON.toJson(candidate); if (text.getBytes(StandardCharsets.UTF_8).length > 4_000_000) throw new IllegalArgumentException("Definition exceeds size limit");
        write(backup, Definition.JSON.toJson(definition)); write(active, text); definition = candidate;
        SkillAtlas.LOGGER.info("Published Skill Atlas revision {} with {} nodes", candidate.revision, candidate.nodes.size());
    }
    public Path namedFile(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("File name must contain only letters, digits, underscores or hyphens");
        return directory.resolve(name + ".json");
    }
    public Path exportFile(String name) {
        if ("atlas".equalsIgnoreCase(name)) throw new IllegalArgumentException("The active definition file is reserved; use Publish instead");
        return namedFile(name);
    }
    public Path draft(UUID id) { return directory.resolve("drafts").resolve(id + ".json"); }
    public List<String> validate(Definition d) {
        List<String> errors;
        try { errors = new ArrayList<>(AtlasLogic.validate(d)); }
        catch (RuntimeException ex) { return List.of("Malformed definition: " + ex.getMessage()); }
        if (!errors.isEmpty()) return errors;
        d.nodes.forEach((id, n) -> {
            ResourceLocation icon = ResourceLocation.tryParse(n.icon);
            if (icon == null || ("item".equals(n.iconType) && !ForgeRegistries.ITEMS.containsKey(icon))) errors.add("Unknown icon: " + id + " / " + n.icon);
            if (!n.iconNbt.isBlank()) try { TagParser.parseTag(n.iconNbt); } catch (Exception ex) { errors.add("Invalid item NBT: " + id); }
            for (var level : n.levels) for (var reward : level.rewards) {
                var type = RewardTypes.get(reward.type);
                if (type == null) errors.add("Unknown reward type: " + id + " / " + reward.type);
                else type.validate(reward).forEach(error -> errors.add(id + ": " + error));
            }
        });
        d.rules.forEach((id, r) -> {
            ResourceLocation key = ResourceLocation.tryParse(r.target.startsWith("#") ? r.target.substring(1) : r.target);
            if (key == null || (!r.target.startsWith("#") && !("kill".equals(r.event) ? ForgeRegistries.ENTITY_TYPES.containsKey(key) : ForgeRegistries.BLOCKS.containsKey(key)))) errors.add("Unknown rule target: " + id + " / " + r.target);
            else if (r.target.startsWith("#")) {
                boolean known;
                if ("kill".equals(r.event)) { var tags = ForgeRegistries.ENTITY_TYPES.tags(); known = tags != null && tags.isKnownTagName(tags.createTagKey(key)); }
                else { var tags = ForgeRegistries.BLOCKS.tags(); known = tags != null && tags.isKnownTagName(tags.createTagKey(key)); }
                if (!known) errors.add("Unknown rule tag: " + id + " / " + r.target);
            }
        });
        return errors;
    }
    private boolean effect(String key) { ResourceLocation id = ResourceLocation.tryParse(key); return id != null && ForgeRegistries.MOB_EFFECTS.containsKey(id); }
}
