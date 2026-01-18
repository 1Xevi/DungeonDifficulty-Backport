package net.dungeon_difficulty.config;

import net.dungeon_difficulty.DungeonDifficulty;
import net.dungeon_difficulty.logic.PatternMatching;
import net.dungeon_difficulty.util.Compat.CIdentifier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;

public class Default {
    public static ConfigServer config = createDefaultConfig();

    private static ConfigServer createDefaultConfig() {
        var config = new ConfigServer();
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

        // Per Player Difficulty
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

        // Surface
        var overworld = new ConfigServer.Dimension();
        overworld.world_matches.dimension = "minecraft:overworld";
        overworld.zones = List.of(
                structureTag("level_3", dungeonDifficulty.name, 3),
                structureTag("level_2", dungeonDifficulty.name, 2),
                structureTag("level_1", dungeonDifficulty.name, 1),
                biomeRegex("desert|frozen|snowy|ice|jungle", normalDifficulty.name, 1)
        );
        overworld.zone_specifiers = List.of(
                zoneOverrideStructure("bosses", heroicDifficulty.name)
        );

        var nether = new ConfigServer.Dimension();
        nether.world_matches.dimension = "minecraft:the_nether";
        nether.difficulty = new ConfigServer.DifficultyReference(normalDifficulty.name, 3);
        nether.zones = List.of(
                structureTag("level_4", dungeonDifficulty.name, 4)
        );
        nether.entities = List.of(
                entitySpecificMatcher(CIdentifier.ofVanilla("wither"), dungeonDifficulty.name, 3)
        );

        var end = new ConfigServer.Dimension();
        end.world_matches.dimension = "minecraft:the_end";
        end.difficulty = new ConfigServer.DifficultyReference(normalDifficulty.name, 4);
        end.zones = List.of(
                biomeSpecific("minecraft:the_end", heroicDifficulty.name, 5),
                structureTag("level_6", dungeonDifficulty.name, 6),
                structureTag("level_5", dungeonDifficulty.name, 5)
        );
        end.entities = List.of(
                entitySpecificMatcher(CIdentifier.ofVanilla("ender_dragon"), dungeonDifficulty.name, 4)
        );

        config.difficulty_types = List.of(normalDifficulty, dungeonDifficulty, heroicDifficulty);
        config.dimensions = new ConfigServer.Dimension[] { overworld, nether, end };
        config.per_player_difficulty = perPlayerDifficulty;
        return config;
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

    private static ConfigServer.Zone biomeRegex(String regex, String difficulty, int level) {
        var zone = new ConfigServer.Zone();
        zone.zone_matches.biome = PatternMatching.REGEX_PREFIX + regex;
        zone.difficulty = new ConfigServer.DifficultyReference(difficulty, level);
        return zone;
    }

    private static ConfigServer.Zone biomeSpecific(String biome, String difficulty, int level) {
        var zone = new ConfigServer.Zone();
        zone.zone_matches.biome = biome;
        zone.difficulty = new ConfigServer.DifficultyReference(difficulty, level);
        return zone;
    }

    private static ConfigServer.Zone structureId(String id, String difficulty, int level) {
        var zone = new ConfigServer.Zone();
        zone.zone_matches.structure = id;
        zone.difficulty = new ConfigServer.DifficultyReference(difficulty, level);
        return zone;
    }

    private static ConfigServer.Zone structureTag(String tag, String difficulty, int level) {
        var zone = new ConfigServer.Zone();
        zone.zone_matches.structure = "#" + DungeonDifficulty.MODID + ":" + tag;
        zone.difficulty = new ConfigServer.DifficultyReference(difficulty, level);
        return zone;
    }

    private static ConfigServer.Zone.TypeOverride zoneOverrideStructure(String tag, String difficulty) {
        var override = new ConfigServer.Zone.TypeOverride();
        override.zone_matches.structure = "#" + DungeonDifficulty.MODID + ":" + tag;
        override.difficulty_name = difficulty;
        return override;
    }

    private static ConfigServer.Zone.TypeOverride zoneOverrideBiome(String biome, String difficulty) {
        var override = new ConfigServer.Zone.TypeOverride();
        override.zone_matches.biome = biome;
        override.difficulty_name = difficulty;
        return override;
    }

    private static ConfigServer.EntityMatcher entityTypeMatcher(String type, String difficulty, int level) {
        var entityMatcher = new ConfigServer.EntityMatcher();
        entityMatcher.entity_type = type;
        entityMatcher.difficulty = new ConfigServer.DifficultyReference(difficulty, level);
        return entityMatcher;
    }

    private static ConfigServer.EntityMatcher entityLootTableMatcher(String lootTable, String difficulty, int level) {
        var entityMatcher = new ConfigServer.EntityMatcher();
        entityMatcher.loot_table = lootTable;
        entityMatcher.difficulty = new ConfigServer.DifficultyReference(difficulty, level);
        return entityMatcher;
    }

    private static ConfigServer.EntityMatcher entitySpecificMatcher(Identifier entityId, String difficulty, int level) {
        var entityMatcher = new ConfigServer.EntityMatcher();
        entityMatcher.entity_type = entityId.toString();
        entityMatcher.loot_table = entityId.getNamespace() + ":" + "entities/" + entityId.getPath();
        entityMatcher.difficulty = new ConfigServer.DifficultyReference(difficulty, level);
        return entityMatcher;
    }
}
