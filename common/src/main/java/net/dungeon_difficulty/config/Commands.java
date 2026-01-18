package net.dungeon_difficulty.config;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.shedaniel.autoconfig.AutoConfig;
import net.dungeon_difficulty.DungeonDifficulty;
import net.dungeon_difficulty.config.ConfigServer;
import net.dungeon_difficulty.util.Debugger;
import net.dungeon_difficulty.logic.ItemScaling;
import net.dungeon_difficulty.logic.DifficultyHandler;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public class Commands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("dungeon_diff") // The Root Command
                .requires(source -> source.hasPermissionLevel(2)) // OP Level 2+ only

                .then(literal("reload")
                        .executes(context -> {
                            try {
                                var holder = AutoConfig.getConfigHolder(ConfigServer.class);
                                holder.load();

                                context.getSource().sendFeedback(() -> Text.literal("§a[DungeonDiff] Config reloaded successfully!"), true);
                                return 1;
                            } catch (Exception e) {
                                context.getSource().sendError(Text.literal("§c[DungeonDiff] RELOAD FAILED! Check console for syntax errors."));

                                Debugger.log("Failed to reload config: ", e);
                                return 0;
                            }
                        })
                )

                .then(literal("power_level")
                        .then(CommandManager.argument("players", EntityArgumentType.player())
                                .then(CommandManager.argument("level", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            var players = EntityArgumentType.getPlayers(context, "players");
                                            var level = IntegerArgumentType.getInteger(context, "level");
                                            if (level < 0) level = 0;

                                            for (var player : players) {
                                                var heldItemStack = player.getMainHandStack();
                                                ItemScaling.rescale(heldItemStack, level);
                                            }
                                            return 1;
                                        })
                                )

                        )
                )
                .then(literal("history")
                    .executes(context -> {
                        return printHistory(context, context.getSource().getPlayerOrThrow());
                    })

                    .then(CommandManager.argument("target", EntityArgumentType.player())
                            .executes(context -> {
                                var targetPlayer = EntityArgumentType.getPlayer(context, "target");
                                return printHistory(context, targetPlayer);
                            })
                    )
                )

                .then(literal("logging")
                        .executes(context -> {
                            Debugger.enabled = !Debugger.enabled; // Toggle the static variable

                            String status = Debugger.enabled ? "§aENABLED" : "§cDISABLED";
                            context.getSource().sendFeedback(() -> Text.literal("§7[DungeonDiff] Debug logging is now " + status), true);
                            return 1;
                        })
                )
        );
    }

    private static int printHistory(CommandContext<ServerCommandSource> context, ServerPlayerEntity player) {
        var config = ConfigServer.fetch().announcement;
        var source = context.getSource();
        var history = ((DifficultyHandler) player).getLastDifficultyAnnouncements();

        if (history.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§e[Debug] History is empty for " + player.getName().getString()), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("§6=== History for " + player.getName().getString() + " ==="), false);

        for (int i = history.size() - 1; i >= 0; i--) {
            var entry = history.get(i);
            long ageTicks = player.age - entry.timestamp();
            double secondsAgo = ageTicks / 20.0;

            String color = (secondsAgo < config.reannounce_cooldown_seconds) ? "§a" : "§7";

            String line = String.format("%s[ -%.1fs ] §f%s §8(%s)",
                    color,
                    secondsAgo,
                    entry.locationName(),
                    entry.difficulty().type().name
            );
            source.sendFeedback(() -> Text.literal(line), false);
        }
        return 1;
    }
}
