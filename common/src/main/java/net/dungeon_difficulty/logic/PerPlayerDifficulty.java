package net.dungeon_difficulty.logic;

import net.dungeon_difficulty.config.ConfigServer;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;

public class PerPlayerDifficulty {
    public static PatternMatching.EntityScaleResult getAttributeModifiers(PatternMatching.EntityData entityData, ServerWorld world) {
        var empty = PatternMatching.EntityScaleResult.EMPTY;
        var perPlayer = ConfigServer.fetch().per_player_difficulty;
        if (perPlayer == null || !perPlayer.enabled || perPlayer.entities == null || perPlayer.entities.isEmpty() || perPlayer.counting == null) {
            return empty;
        }

        var playerCount = 0;
        switch (perPlayer.counting) {
            case EVERYWHERE -> {
                playerCount = world.getServer().getPlayerManager().getPlayerList().size();
            }
            case DIMENSION -> {
                playerCount = world.getPlayers().size();
            }
        }
        if (playerCount < 2) {
            return empty;
        }

        int applyCount = Math.min(playerCount, perPlayer.cap) - 1;
        var attributeModifiers = new ArrayList<ConfigServer.AttributeModifier>();
        for(var entityBaseModifier: perPlayer.entities) {
            if (entityData.matches(entityBaseModifier.entity_matches)) {
                attributeModifiers.addAll(entityBaseModifier.attributes);
            }
        }
        return new PatternMatching.EntityScaleResult("per_player", attributeModifiers, applyCount, 0);
    }
}
