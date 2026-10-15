package com.hp.skillatlas.server;

import com.hp.skillatlas.data.PlayerProgress;
import com.hp.skillatlas.data.PointPool;
import com.hp.skillatlas.data.SkillNode;
import com.hp.skillatlas.data.Texts;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class AtlasCommands {
    private static final SuggestionProvider<CommandSourceStack> POOLS = (c, b) -> SharedSuggestionProvider.suggest(AtlasServer.definition().pools.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> NODES = (c, b) -> SharedSuggestionProvider.suggest(AtlasServer.definition().nodes.keySet(), b);

    private AtlasCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("skillatlas").requires(s -> s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("reload").executes(c -> {
                    if (AtlasServer.reload()) {
                        c.getSource().sendSuccess(() -> Component.translatable("skill_atlas.command.reload.ok", AtlasServer.definition().nodes.size()), true);
                        return 1;
                    }
                    c.getSource().sendFailure(Component.translatable("skill_atlas.command.reload.failed", AtlasServer.loadError()));
                    return 0;
                }))
                .then(Commands.literal("points")
                        .then(Commands.literal("get")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("pool", StringArgumentType.word()).suggests(POOLS).executes(c -> {
                                            ServerPlayer player = EntityArgument.getPlayer(c, "player");
                                            String pool = StringArgumentType.getString(c, "pool");
                                            if (!AtlasServer.definition().pools.containsKey(pool)) {
                                                c.getSource().sendFailure(Component.translatable("skill_atlas.error.missing_pool", pool));
                                                return 0;
                                            }
                                            long value = AtlasServer.progress(player).points(pool);
                                            c.getSource().sendSuccess(() -> Component.translatable("skill_atlas.command.points.get", player.getDisplayName(), value, poolName(pool, c.getSource())), false);
                                            return (int) Math.min(Integer.MAX_VALUE, value);
                                        }))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("pool", StringArgumentType.word()).suggests(POOLS)
                                                .then(Commands.argument("amount", LongArgumentType.longArg(-PlayerProgress.MAX_POINTS, PlayerProgress.MAX_POINTS))
                                                        .executes(c -> points(c, true))))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("pool", StringArgumentType.word()).suggests(POOLS)
                                                .then(Commands.argument("amount", LongArgumentType.longArg(0, PlayerProgress.MAX_POINTS))
                                                        .executes(c -> points(c, false)))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> reset(c, null))
                                .then(Commands.argument("node", StringArgumentType.word()).suggests(NODES)
                                        .executes(c -> reset(c, StringArgumentType.getString(c, "node"))))))
                .then(Commands.literal("grant")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("node", StringArgumentType.word()).suggests(NODES)
                                        .executes(c -> grant(c, -1))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0, SkillNode.MAX_LEVELS))
                                                .executes(c -> grant(c, IntegerArgumentType.getInteger(c, "level"))))))));
    }

    private static Component poolName(String id, CommandSourceStack source) {
        PointPool p = AtlasServer.definition().pools.get(id);
        if (p == null) {
            return Component.literal(id);
        }
        return Texts.component(AtlasServer.text(p.name, source), p.id).withStyle(s -> s.withColor(p.color));
    }

    private static int points(CommandContext<CommandSourceStack> c, boolean add) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        String pool = StringArgumentType.getString(c, "pool");
        if (!AtlasServer.definition().pools.containsKey(pool)) {
            c.getSource().sendFailure(Component.translatable("skill_atlas.error.missing_pool", pool));
            return 0;
        }
        long amount = LongArgumentType.getLong(c, "amount");
        for (ServerPlayer player : targets) {
            PlayerProgress p = AtlasServer.progress(player);
            if (add) {
                p.addPoints(pool, amount);
            } else {
                p.setPoints(pool, amount);
            }
            AtlasServer.sendProgress(player);
        }
        AtlasServer.data().setDirty();
        String key = "skill_atlas.command.points.set";
        if (add) {
            key = "skill_atlas.command.points.add";
        }
        String finalKey = key;
        c.getSource().sendSuccess(() -> Component.translatable(finalKey, amount, poolName(pool, c.getSource()), targets.size()), true);
        return targets.size();
    }

    private static int reset(CommandContext<CommandSourceStack> c, String node) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        if (node != null && !AtlasServer.definition().nodes.containsKey(node)) {
            c.getSource().sendFailure(Component.translatable("skill_atlas.error.missing_node", node));
            return 0;
        }
        for (ServerPlayer player : targets) {
            List<String> nodes = new ArrayList<>();
            if (node == null) {
                nodes.addAll(AtlasServer.progress(player).levels().keySet());
            } else {
                nodes.add(node);
            }
            AtlasServer.reset(player, nodes);
        }
        c.getSource().sendSuccess(() -> Component.translatable("skill_atlas.command.reset", targets.size()), true);
        return targets.size();
    }

    private static int grant(CommandContext<CommandSourceStack> c, int level) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        String id = StringArgumentType.getString(c, "node");
        SkillNode node = AtlasServer.definition().nodes.get(id);
        if (node == null) {
            c.getSource().sendFailure(Component.translatable("skill_atlas.error.missing_node", id));
            return 0;
        }
        int target = level;
        if (target < 0) {
            target = node.maxLevel();
        }
        int applied = 0;
        for (ServerPlayer player : targets) {
            applied = AtlasServer.grant(player, node, target);
        }
        int shown = applied;
        c.getSource().sendSuccess(() -> Component.translatable("skill_atlas.command.grant", Texts.component(AtlasServer.text(node.name, c.getSource()), node.id), shown, targets.size()), true);
        return targets.size();
    }
}
