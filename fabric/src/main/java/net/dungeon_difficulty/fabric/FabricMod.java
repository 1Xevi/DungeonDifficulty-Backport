package net.dungeon_difficulty.fabric;

import net.dungeon_difficulty.DungeonDifficulty;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.dungeon_difficulty.config.Commands;

public final class FabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        DungeonDifficulty.init();
        DungeonDifficulty.registerLootFunctions();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            Commands.register(dispatcher); // Call the common logic
        });
    }
}
