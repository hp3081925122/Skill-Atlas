package com.hp.skillatlas.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.hp.skillatlas.core.AtlasLogic;

public final class AtlasCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("skillatlas").requires(s -> s.hasPermission(2));
        root.then(Commands.literal("reload").executes(c -> {
            try { AtlasService.get().reload(); c.getSource().sendSuccess(() -> Component.translatable("skill_atlas.status.published"), true); return 1; }
            catch (Exception ex) { c.getSource().sendFailure(Component.translatable("skill_atlas.status.error").append(": " + ex.getMessage())); return 0; }
        }));
        root.then(Commands.literal("query").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
            ServerPlayer p = EntityArgument.getPlayer(c, "player");
            c.getSource().sendSuccess(() -> Component.literal(com.hp.skillatlas.model.Definition.JSON.toJson(AtlasService.get().progress(p))), false); return 1;
        })));
        root.then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.players())
            .then(Commands.argument("node", StringArgumentType.word()).suggests((c, b) -> { b.suggest("*"); AtlasService.get().definition().nodes.keySet().forEach(b::suggest); return b.buildFuture(); }).executes(c -> {
                String id = StringArgumentType.getString(c, "node");
                if (!id.equals("*") && !AtlasService.get().definition().nodes.containsKey(id)) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.translatable("skill_atlas.status.unknown_node")).create();
                var players = EntityArgument.getPlayers(c, "player"); for (var p : players) AtlasService.get().reset(p, id); return players.size();
            }))));
        for (String resource : new String[]{"points", "experience"}) for (String operation : new String[]{"add", "set"}) {
            root.then(Commands.literal(resource).then(Commands.literal(operation).then(Commands.argument("player", EntityArgument.players())
                .then(Commands.argument("pool", StringArgumentType.word()).suggests((c, b) -> { AtlasService.get().definition().pools.keySet().forEach(b::suggest); return b.buildFuture(); })
                .then(Commands.argument("amount", LongArgumentType.longArg(0, AtlasLogic.LIMIT)).executes(c -> {
                    var service = AtlasService.get(); String pool = StringArgumentType.getString(c, "pool");
                    if (!service.definition().pools.containsKey(pool)) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.translatable("skill_atlas.error.pool")).create();
                    long amount = LongArgumentType.getLong(c, "amount"); var players = EntityArgument.getPlayers(c, "player");
                    for (ServerPlayer p : players) {
                        var balance = service.progress(p).balance(pool);
                        if (resource.equals("points")) balance.points = operation.equals("set") ? amount : AtlasLogic.add(balance.points, amount);
                        else {
                            if (operation.equals("set")) AtlasLogic.setExperience(service.definition().pools.get(pool), balance, amount);
                            else AtlasLogic.experience(service.definition().pools.get(pool), balance, amount);
                        }
                        service.changed(p); service.syncProgress(p);
                    }
                    return players.size();
                }))))));
        }
        dispatcher.register(root);
    }
}
