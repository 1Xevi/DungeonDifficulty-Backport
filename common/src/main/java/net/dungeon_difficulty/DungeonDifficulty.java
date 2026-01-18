package net.dungeon_difficulty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.dungeon_difficulty.config.ConfigServer;
import net.dungeon_difficulty.logic.ItemScaling;
import net.dungeon_difficulty.logic.LocalScalingLootFunction;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import org.slf4j.Logger;

public class DungeonDifficulty {
    public static final String MODID = "dungeon_difficulty";

    public static void init() {
        AutoConfig.register(ConfigServer.class, GsonConfigSerializer::new);
        ItemScaling.initialize();
    }

    public static void registerLootFunctions() {
        Registry.register(Registries.LOOT_FUNCTION_TYPE, LocalScalingLootFunction.ID, LocalScalingLootFunction.TYPE);
    }
}
