package net.dungeon_difficulty.logic;

import net.dungeon_difficulty.DungeonDifficulty;
import net.dungeon_difficulty.config.Config;
import net.dungeon_difficulty.config.Config.DifficultyType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class DifficultyTypes {
    public static List<DifficultyType> resolved = List.of();

    public static void resolve() {
        var resolved = new ArrayList<DifficultyType>();
        var types = DungeonDifficulty.config.value.difficulty_types;
        for (var type: DungeonDifficulty.config.value.difficulty_types) {
            resolved.add(resolve(type, types));
        }
        DifficultyTypes.resolved = resolved;
    }

    private static DifficultyType resolve(DifficultyType type, List<DifficultyType> types) {
        if (type.parent != null && !type.parent.isEmpty()) {
            var parent = types.stream()
                    .filter(otherType -> type.parent.equals(otherType.name))
                    .findFirst().orElse(null);
            if (parent != null) {
                parent = resolve(parent, types);
                return merge(type, parent);
            }
        }
        return type;
    }

    private static DifficultyType copy(DifficultyType type) {
        var copy = new DifficultyType();
        copy.name = type.name;
        copy.parent = type.parent;
        copy.entities = type.entities;
        copy.rewards = type.rewards;
        return copy;
    }

    private static DifficultyType merge(DifficultyType t1, DifficultyType t2) {
        var merged = copy(t1);
        merged.entities = Stream.concat(t1.entities.stream(), t2.entities.stream()).toList();
        merged.rewards = new Config.Rewards();
        merged.rewards.armor = Stream.concat(t1.rewards.armor.stream(), t2.rewards.armor.stream()).toList();
        merged.rewards.weapons = Stream.concat(t1.rewards.weapons.stream(), t2.rewards.weapons.stream()).toList();
        return merged;
    }
}