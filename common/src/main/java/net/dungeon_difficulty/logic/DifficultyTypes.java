package net.dungeon_difficulty.logic;

import net.dungeon_difficulty.config.ConfigServer;
import net.dungeon_difficulty.config.ConfigServer.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class DifficultyTypes {
    public static List<DifficultyType> resolved = new ArrayList<>();

    public static void resolve(ConfigServer config) {
        System.out.println("Resolving difficulty list...");

        if (resolved == null || resolved.getClass().getName().contains("Immutable")) {
            resolved = new ArrayList<>();
        }
        resolved.clear();


        var types = config.difficulty_types;

        for (var type: types) {
            resolved.add(resolve(type, types));
        }
    }

    public static void resolve() {
        resolve(ConfigServer.fetch());
    }

    private static DifficultyType resolve(DifficultyType type, List<ConfigServer.DifficultyType> types) {
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
        return copy;
    }

    private static ConfigServer.DifficultyType merge(ConfigServer.DifficultyType t1, ConfigServer.DifficultyType t2) {
        var merged = copy(t1);
        merged.entities = Stream.concat(t1.entities.stream(), t2.entities.stream()).toList();
        merged.allow_loot_scaling = t2.allow_loot_scaling;
        if (t1.allow_loot_scaling != null) {
            merged.allow_loot_scaling = t1.allow_loot_scaling;
        }
        return merged;
    }
}