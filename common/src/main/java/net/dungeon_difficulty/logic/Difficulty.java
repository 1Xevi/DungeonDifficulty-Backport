package net.dungeon_difficulty.logic;

import net.dungeon_difficulty.config.ConfigServer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

public record Difficulty(ConfigServer.DifficultyType type,
                         int level,
                         int entityLevel,
                         int rewardLevel) {
    private static final ConfigServer.DifficultyType EMPTY_TYPE = new ConfigServer.DifficultyType("empty");
    public static final Difficulty EMPTY = new Difficulty(EMPTY_TYPE, 0, 0, 0);

    public Difficulty withType(ConfigServer.DifficultyType newType) {
        return new Difficulty(newType, level, entityLevel, rewardLevel);
    }

    public boolean isValid() {
        return type != null && level > 0;
    }

    public boolean typeEquals(Difficulty other) {
        return type.name.equals(other.type.name) && level == other.level;
    }

    public String typeTranslationKey() {
        var suffix = type.translation_code != null ? type.translation_code : type.name;
        return "difficulty.type." + suffix.toLowerCase(Locale.ENGLISH);
    }

    public record Announcement(Difficulty difficulty, int timestamp, String dimensionId, @Nullable Identifier matchId) {
        public static Announcement EMPTY = new Announcement(Difficulty.EMPTY, 0, "", null);

        public String locationName() {
            return (matchId != null) ? matchId.toString() : "global";
        }

        public boolean equals(Announcement other) {
            if (other == null) return false;
            return difficulty.typeEquals(other.difficulty)
                    && Objects.equals(dimensionId, other.dimensionId)
                    && Objects.equals(matchId, other.matchId);
        }
    }

    public boolean allowsLootScaling() {
        return type.allow_loot_scaling != null && type.allow_loot_scaling;
    }
}
