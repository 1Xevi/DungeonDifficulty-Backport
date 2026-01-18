package net.dungeon_difficulty.config;

import net.dungeon_difficulty.DungeonDifficulty;
import net.dungeon_difficulty.logic.PatternMatching;
import net.dungeon_difficulty.util.Compat.CIdentifier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.ArrayList;

public class Default {
    public static void populate(ConfigServer config) {
        // Difficulty types
        var normalDifficulty = new ConfigServer.DifficultyType("adventure");
        normalDifficulty.entities = List.of(
                createEntityModifier(null,
                        List.of(
                                createAttackDamageMultiplier(0.2F, 0),
                                createArmorBonus(1),
                                createHealthMultiplier(0.25F, 0.1F)
                        ),
                        null,
                        0.2F)
        );

        var meta = new ConfigServer.Meta();
        meta.global_loot_scaling = true;
        meta.rounding_unit = 0.5;
        meta.merge_item_modifiers = true;
        meta.sanitize_config = true;

        var dungeonDifficulty = new ConfigServer.DifficultyType("dungeon");
        dungeonDifficulty.allow_loot_scaling = true;
        dungeonDifficulty.parent = normalDifficulty.name;

        var dungeonSpawners = new ConfigServer.SpawnerModifier();
        dungeonSpawners = new ConfigServer.SpawnerModifier();
        dungeonSpawners.min_spawn_delay_multiplier = -0.1F;
        dungeonSpawners.max_spawn_delay_multiplier = -0.1F;
        dungeonSpawners.spawn_count_multiplier = 0.5F;
        dungeonSpawners.max_nearby_entities_multiplier = 0.25F;

        dungeonDifficulty.entities = List.of(
                createEntityModifier(null,
                        List.of(),
                        dungeonSpawners,
                        0)
        );
        config.loot_scaling.armor = List.of(
                createItemModifier(List.of(
                        createArmorMultiplier(0.1F),
                        createHealthBonus(1)
                ))
        );
        config.loot_scaling.weapons = List.of(
                createItemModifier(List.of(
                        createRegexDamageMultiplier(0.1F, 0.05F),
                        createRegexPowerMultiplier(0.1F, 0.05F)
                ))
        );

        var heroicDifficulty = new ConfigServer.DifficultyType("heroic");
        heroicDifficulty.parent = dungeonDifficulty.name;

        config.difficulty_types = List.of(normalDifficulty, dungeonDifficulty, heroicDifficulty);

        // per player difficulty
        var perPlayerDifficulty = new ConfigServer.PerPlayerDifficulty();
        var perPlayerEntityModifier = new ConfigServer.EntityModifier();
        if (FabricLoader.getInstance().isModLoaded("the_bumblezone")) {
            perPlayerEntityModifier.entity_matches = new ConfigServer.EntityModifier.Filters();
            perPlayerEntityModifier.entity_matches.type = PatternMatching.REGEX_PREFIX + "^(?!the_bumblezone:cosmic_crystal_entity).*$";
        }

        perPlayerEntityModifier.attributes = List.of(
                createAttackDamageMultiplier(0.1F, 0),
                createHealthMultiplier(0.2F, 0F)
        );
        perPlayerDifficulty.entities = List.of(perPlayerEntityModifier);

        config.per_player_difficulty = perPlayerDifficulty;

        // surface
        config.scaling_rules = new ArrayList<>();

        // OVERWORLD TREE
        var overworld = ruleDim("minecraft:overworld", null, 0);

        // nest the old Zones as overrides
        overworld.overrides.add(ruleStructure("#dungeon_difficulty:level_3", "dungeon", 3));
        overworld.overrides.add(ruleStructure("#dungeon_difficulty:level_2", "dungeon", 2));
        overworld.overrides.add(ruleStructure("#dungeon_difficulty:level_1", "dungeon", 1));
        overworld.overrides.add(ruleBiomeRegex("desert|frozen|snowy|ice|jungle", "adventure", 1));

        // old ZoneSpecifier (Bosses) is just another override.
        overworld.overrides.add(ruleStructure("#dungeon_difficulty:bosses", "heroic", 0));

        config.scaling_rules.add(overworld);

        // NETHER TREE
        var nether = ruleDim("minecraft:the_nether", "adventure", 3);
        nether.overrides.add(ruleStructure("#dungeon_difficulty:level_4", "dungeon", 4));
        nether.overrides.add(ruleEntity("minecraft:wither", "dungeon", 3));

        config.scaling_rules.add(nether);

        // END TREE
        var end = ruleDim("minecraft:the_end", "adventure", 4);
        end.overrides.add(ruleBiome("minecraft:the_end", "heroic", 5));
        end.overrides.add(ruleStructure("#dungeon_difficulty:level_6", "dungeon", 6));
        end.overrides.add(ruleStructure("#dungeon_difficulty:level_5", "dungeon", 5));
        end.overrides.add(ruleEntity("minecraft:ender_dragon", "dungeon", 4));

        config.scaling_rules.add(end);
    }

    private static ConfigServer.ItemModifier createItemModifier(List<ConfigServer.AttributeModifier> attributeModifiers) {
        return createItemModifier(null, null, attributeModifiers);
    }

    private static ConfigServer.ItemModifier createItemModifier(String itemIdRegex, String lootTableRegex, List<ConfigServer.AttributeModifier> attributeModifiers) {
        var itemModifier = new ConfigServer.ItemModifier();
        itemModifier.item_matches = new ConfigServer.ItemModifier.Filters();
        if (itemIdRegex != null) {
            itemModifier.item_matches.id = PatternMatching.REGEX_PREFIX + itemIdRegex;
        }
        if (lootTableRegex != null) {
            itemModifier.item_matches.loot_table_regex = lootTableRegex;
        }
        itemModifier.attributes = attributeModifiers;
        return itemModifier;
    }

    private static ConfigServer.AttributeModifier createRegexDamageMultiplier(float value, float randomness) {
        var modifier = new ConfigServer.AttributeModifier("damage", value);
        modifier.randomness = randomness;
        return modifier;
    }

    private static ConfigServer.AttributeModifier createAttackDamageMultiplier(float value, float randomness) {
        var modifier = new ConfigServer.AttributeModifier(Registries.ATTRIBUTE.getId(EntityAttributes.GENERIC_ATTACK_DAMAGE).toString(), value);
        modifier.randomness = randomness;
        return modifier;
    }

    private static ConfigServer.AttributeModifier createRegexPowerMultiplier(float value, float randomness) {
        var modifier = new ConfigServer.AttributeModifier("power", value);
        modifier.randomness = randomness;
        return modifier;
    }

    private static ConfigServer.AttributeModifier createProjectileMultiplier(float value, float randomness) {
        var modifier = new ConfigServer.AttributeModifier("ranged_weapon:damage", value);
        modifier.randomness = randomness;
        return modifier;
    }

    private static ConfigServer.AttributeModifier createArmorMultiplier(float value) {
        return new ConfigServer.AttributeModifier(Registries.ATTRIBUTE.getId(EntityAttributes.GENERIC_ARMOR).toString(), value);
    }

    private static ConfigServer.AttributeModifier createArmorBonus(float value) {
        var modifier = new ConfigServer.AttributeModifier(Registries.ATTRIBUTE.getId(EntityAttributes.GENERIC_ARMOR).toString(), value);
        modifier.operation = ConfigServer.Operation.ADDITION;
        return modifier;
    }

    private static ConfigServer.AttributeModifier createHealthMultiplier(float value, float randomness) {
        var modifier = new ConfigServer.AttributeModifier(Registries.ATTRIBUTE.getId(EntityAttributes.GENERIC_MAX_HEALTH).toString(), value);
        modifier.randomness = randomness;
        return modifier;
    }

    private static ConfigServer.AttributeModifier createHealthBonus(float value) {
        var modifier = new ConfigServer.AttributeModifier(Registries.ATTRIBUTE.getId(EntityAttributes.GENERIC_MAX_HEALTH).toString(), value);
        modifier.operation = ConfigServer.Operation.ADDITION;
        return modifier;
    }

    private static ConfigServer.EntityModifier createEntityModifier(String idRegex, List<ConfigServer.AttributeModifier> attributeModifiers, ConfigServer.SpawnerModifier spawnerModifier, float xpMultiplier) {
        var entityModifier = new ConfigServer.EntityModifier();
        if (idRegex != null) {
            entityModifier.entity_matches = new ConfigServer.EntityModifier.Filters();
            entityModifier.entity_matches.type = PatternMatching.REGEX_PREFIX + idRegex;
        }
        entityModifier.attributes = attributeModifiers;
        entityModifier.spawners = spawnerModifier;
        entityModifier.experience_multiplier = xpMultiplier;
        return entityModifier;
    }

    private static ConfigServer.ScalingRule ruleDim(String dim, String diffName, int level) {
        var r = new ConfigServer.ScalingRule();
        r.match.dimension = dim;
        if (diffName != null) r.difficulty = new ConfigServer.DifficultyReference(diffName, level);
        return r;
    }

    private static ConfigServer.ScalingRule ruleStructure(String struct, String diffName, int level) {
        var r = new ConfigServer.ScalingRule();
        r.match.structure = struct;
        r.difficulty = new ConfigServer.DifficultyReference(diffName, level);
        return r;
    }

    private static ConfigServer.ScalingRule ruleBiome(String biome, String diffName, int level) {
        var r = new ConfigServer.ScalingRule();
        r.match.biome = biome;
        r.difficulty = new ConfigServer.DifficultyReference(diffName, level);
        return r;
    }

    private static ConfigServer.ScalingRule ruleBiomeRegex(String regex, String diffName, int level) {
        var r = new ConfigServer.ScalingRule();
        r.match.biome = "~" + regex; // Assuming PatternMatching.REGEX_PREFIX is "~"
        r.difficulty = new ConfigServer.DifficultyReference(diffName, level);
        return r;
    }

    private static ConfigServer.ScalingRule ruleEntity(String entity, String diffName, int level) {
        var r = new ConfigServer.ScalingRule();
        r.match.entity = entity;
        r.difficulty = new ConfigServer.DifficultyReference(diffName, level);
        return r;
    }
}
