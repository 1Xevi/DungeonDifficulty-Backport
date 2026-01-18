package net.dungeon_difficulty;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.dungeon_difficulty.config.ConfigServer;
import net.dungeon_difficulty.config.Default;
import net.dungeon_difficulty.logic.DifficultyHandler;
import net.dungeon_difficulty.logic.DifficultyTypes;
import net.dungeon_difficulty.logic.ItemScaling;
import net.dungeon_difficulty.logic.LocalScalingLootFunction;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;

public class DungeonDifficulty {
    public static final String MODID = "dungeon_difficulty";

    public static void init() {
        AutoConfig.register(ConfigServer.class, GsonConfigSerializer::new);
        ItemScaling.initialize();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal(MODID + "_config_reload").executes(context -> {
                System.out.println("Reloading config...");

                // New Reload Logic
                AutoConfig.getConfigHolder(ConfigServer.class).load();
                DifficultyTypes.resolve();

                return 1;
            }));
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("power_level")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("players", EntityArgumentType.player())
                        .then(CommandManager.argument("level", IntegerArgumentType.integer(0))
                            .executes(context -> {
                                var players = EntityArgumentType.getPlayers(context, "players");
                                var level = IntegerArgumentType.getInteger(context, "level");
                                if (level < 0) {
                                    level = 0;
                                }
                                for (var player : players) {
                                    var heldItemStack = player.getMainHandStack();
                                    ItemScaling.rescale(heldItemStack, level);
                                }
                                return 1;
                            })
                        )
                    )
            );
        });
    }

    public static void registerLootFunctions() {
        Registry.register(Registries.LOOT_FUNCTION_TYPE, LocalScalingLootFunction.ID, LocalScalingLootFunction.TYPE);
    }
}
