package net.dungeon_difficulty.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.dungeon_difficulty.config.ConfigServer;
import net.dungeon_difficulty.DungeonDifficulty;

public class Debugger {
    public static final Logger LOGGER = LoggerFactory.getLogger(DungeonDifficulty.MODID);

    public static boolean enabled = false;

    public static void log(String message) {
        if (enabled) {
            LOGGER.info("[Debug] " + message);
        }
    }

    public static void log(String message, Throwable throwable) {
        if (enabled) {
            LOGGER.info("[Debug] " + message, throwable);
        }
    }
}
