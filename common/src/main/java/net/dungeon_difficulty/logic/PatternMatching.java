package net.dungeon_difficulty.logic;

import net.dungeon_difficulty.config.ConfigServer;
import net.dungeon_difficulty.util.Compat.CIdentifier;
import net.dungeon_difficulty.util.Debugger;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternMatching {

    // --- RECURSIVE SEARCH ENGINE

    @Nullable
    public static DifficultySearchResult getDifficultyResult(
            LocationData locationData,
            @Nullable Identifier sourceId,
            ScalingGoal scalingGoal,
            ServerWorld world
    ) {
        var result = findBestMatchRecursive(ConfigServer.fetch().scaling_rules, locationData, sourceId, world);

        // 2. Print ONLY the Final Verdict
        if (result != null) {
            // Get the name of the biome or structure that triggered the match
            String locationName = (result.matchId() != null) ? result.matchId().toString() : "Unknown/Global";
            String difficultyName = result.difficulty().type().name;
            int level = result.difficulty().level();

            Debugger.log("Match: " + locationName + " -> " + difficultyName + " (Lvl " + level + ")");
        }

        return result;
    }

    private static DifficultySearchResult findBestMatchRecursive(List<ConfigServer.ScalingRule> rules, LocationData loc, @Nullable Identifier sourceId, ServerWorld world) {
        for (var rule : rules) {

            // get the specific Match Details (or null if it failed)
            var matchInfo = getRuleMatch(rule, loc, sourceId, world);

            // if no match, skip.
            if (matchInfo == null) continue;

            // RECURSION: check overrides first
            var bestChild = findBestMatchRecursive(rule.overrides, loc, sourceId, world);
            if (bestChild != null) {
                return bestChild;
            }

            // WINNER: Create result using the selected/matching Match Info
            var diff = findDifficulty(rule.difficulty);
            if (diff != null && diff.isValid()) {
                // FIX: Pass 'matchInfo' instead of 'null' here!
                return new DifficultySearchResult(diff, loc, matchInfo);
            }
        }
        return null;
    }

    // checks all 4 conditions: Dimension, Biome, Structure, Entity(+Loot)
    @Nullable
    private static LocationData.Match getRuleMatch(ConfigServer.ScalingRule rule, LocationData loc, @Nullable Identifier sourceId, ServerWorld world) {
        var m = rule.match;
        if (m == null) return new LocationData.Match(true, LocationData.Scope.DIMENSION, null, null);

        // Dimension Check
        if (!universalMatch(loc.dimensionId, m.dimension)) return null;

        // Biome Check
        if (m.biome != null && !m.biome.isEmpty()) {
            if (loc.biome == null || !entryMatches(loc.biome.biomeEntry, RegistryKeys.BIOME, m.biome)) {
                return null;
            }
        }

        // Entity / Loot Check
        if (m.entity != null && !m.entity.isEmpty()) {
            if (sourceId == null || !idMatches(sourceId, m.entity)) return null;
        }

        // Structure Check
        RegistryEntry<Structure> matchedStruct = null;
        if (m.structure != null && !m.structure.isEmpty()) {
            matchedStruct = loc.matchesStructure(world, m.structure);
            if (matchedStruct == null) return null;
        }

        // Priority: Structure > Biome > Dimension
        if (matchedStruct != null) {
            return new LocationData.Match(true, LocationData.Scope.STRUCTURE, null, matchedStruct);
        }
        if (m.biome != null && !m.biome.isEmpty()) {
            return new LocationData.Match(true, LocationData.Scope.BIOME, loc.biome().biomeEntry(), null);
        }

        return new LocationData.Match(true, LocationData.Scope.DIMENSION, null, null);
    }


    // --- DATA RECORDS

    public record BiomeData(RegistryEntry<Biome> biomeEntry) { }

    public record LocationData(Identifier dimensionId, BlockPos position, BiomeData biome) {
        public static LocationData create(ServerWorld world, BlockPos position) {
            var dimensionId = world.getRegistryKey().getValue();
            BiomeData biome = null;
            if (position != null && world.isChunkLoaded(ChunkPos.toLong(position))) {
                biome = new BiomeData(world.getBiome(position));
            }
            return new LocationData(dimensionId, position, biome);
        }


        public enum Scope { DIMENSION, BIOME, STRUCTURE }
        public record Match(
                boolean matches, Scope scope,
                @Nullable RegistryEntry<Biome> matchingBiome,
                @Nullable RegistryEntry<Structure> matchingStructure
        ) {
            @Nullable public Identifier id() {
                if (matchingBiome != null) return matchingBiome.getKey().get().getValue();
                if (matchingStructure != null) return matchingStructure.getKey().get().getValue();
                return null;
            }
        }

        // optimized structure finder
        public @Nullable RegistryEntry<Structure> matchesStructure(ServerWorld world, String pattern) {
            if (position == null) return null;

            var registries = world.getServer().getRegistryManager();
            var registry = registries.get(RegistryKeys.STRUCTURE);

            // get structures at pos
            var structureStarts = world.getStructureAccessor().getStructureStarts(new ChunkPos(position), s -> true);

            for (var structureStart : structureStarts) {
                if (!isInsideStructure(world, position, structureStart)) continue;

                var entry = registry.getEntry(registry.getRawId(structureStart.getStructure())).orElse(null);
                if (entry != null && PatternMatching.universalMatch(entry, RegistryKeys.STRUCTURE, pattern)) {
                    return entry; // found a match
                }
            }
            return null;
        }
    }

    private static boolean isInsideStructure(ServerWorld world, BlockPos pos, StructureStart structureStart) {
        if (structureStart.hasChildren()) {
            return structureStart.getBoundingBox().contains(pos);
        }
        return false;
    }

    // --- ITEM & ENTITY
    public record ItemData(ItemKind kind, Identifier lootTableId, RegistryEntry<Item> itemEntry, String rarity) {
        public boolean matches(ConfigServer.ItemModifier.Filters filters) {
            if (filters == null) return true;
            var result = PatternMatching.universalMatch(itemEntry, RegistryKeys.ITEM, filters.id)
                    && PatternMatching.regexMatches(lootTableId.toString(), filters.loot_table_regex)
                    && PatternMatching.regexMatches(rarity, filters.rarity_regex);
            return result;
        }
    }

    public enum ItemKind {
        ARMOR, WEAPONS
    }

    public record ItemScaleResult(List<ConfigServer.AttributeModifier> modifiers, int level) { }
    public static ItemScaleResult getModifiersForItem(LocationData locationData, ItemData itemData, ServerWorld world, @Nullable ConfigServer.Rewards scaling) {
        var result = getDifficultyResult(locationData, itemData.lootTableId(), ScalingGoal.LOOT, world);
        var level = 0;
        if (result != null && result.difficulty() != null && result.difficulty().allowsLootScaling()) { // !
            level = result.difficulty.rewardLevel();
        }
        return getItemScaleResult(itemData, scaling, level);
    }

    public static ItemScaleResult getItemScaleResult(ItemData itemData, @Nullable ConfigServer.Rewards scaling, int level) {
        var attributeModifiers = new ArrayList<ConfigServer.AttributeModifier>();
        if (scaling != null && level > 0) {
            List<ConfigServer.ItemModifier> itemModifiers = null;
            switch (itemData.kind) {
                case ARMOR -> {
                    itemModifiers = scaling.armor;
                }
                case WEAPONS -> {
                    itemModifiers = scaling.weapons;
                }
            }
            if (itemModifiers != null) {
                for(var entry: itemModifiers) {
                    if (itemData.matches(entry.item_matches)) {
                        attributeModifiers.addAll(entry.attributes);
                    }
                }
            }
        }
        return new ItemScaleResult(attributeModifiers, level);
    }

    public record EntityData(@Nullable RegistryEntry<EntityType<?>> type, boolean isHostile) {
        public static EntityData create(LivingEntity entity) {
            var type = Registries.ENTITY_TYPE.getEntry(entity.getType());
            var isHostile = entity instanceof Monster;
            return new EntityData(type, isHostile);
        }
        private static final Identifier UNKNOWN = CIdentifier.of("unknown");
        public Identifier entityId() { return type != null ? type.getKey().get().getValue() : UNKNOWN; }

        public boolean matches(ConfigServer.EntityModifier.Filters filters) {
            if (filters == null) return true;
            var matchesAttitude = switch (filters.attitude) {
                case FRIENDLY -> !isHostile;
                case HOSTILE -> isHostile;
                case ANY -> true;
            };
            var result = matchesAttitude && PatternMatching.universalMatch(type, RegistryKeys.ENTITY_TYPE, filters.type);
            return result;
        }
    }

    public record EntityScaleResult(String name, List<ConfigServer.AttributeModifier> modifiers, int level, float experienceMultiplier) {
        public static final EntityScaleResult EMPTY = new EntityScaleResult("none", List.of(), 0, 0);
    }

    public static EntityScaleResult getAttributeModifiersForEntity(LocationData locationData, EntityData entityData, ServerWorld world) {
        var attributeModifiers = new ArrayList<ConfigServer.AttributeModifier>();
        // Pass entityId as sourceId
        var result = getDifficultyResult(locationData, entityData.entityId(), ScalingGoal.ENTITY, world);

        var level = 0;
        float experienceMultiplier = 0;
        var name = EntityScaleResult.EMPTY.name();

        if (result != null && result.difficulty != null) {
            var difficulty = result.difficulty;
            level = difficulty.entityLevel();
            if (level != 0) {
                for (var modifier : getModifiersForEntity(difficulty.type().entities, entityData)) {
                    attributeModifiers.addAll(modifier.attributes);
                    experienceMultiplier += modifier.experience_multiplier;
                }
            }
            name = difficulty.type().name;
        }
        return new EntityScaleResult(name, attributeModifiers, level, experienceMultiplier);
    }

    public record SpawnerScaleResult(List<ConfigServer.SpawnerModifier> modifiers, int level) { }

    public static SpawnerScaleResult getModifiersForSpawner(LocationData locationData, EntityData entityData, ServerWorld world) {
        var spawnerModifiers = new ArrayList<ConfigServer.SpawnerModifier>();
        var difficulty = getDifficulty(locationData, entityData.entityId(), world);
        int level = 0;
        if (difficulty != null) {
            level = difficulty.entityLevel();
            if (level != 0) {
                for (var modifier: getModifiersForEntity(difficulty.type().entities, entityData)) {
                    if (modifier.spawners != null) spawnerModifiers.add(modifier.spawners);
                }
            }
        }
        return new SpawnerScaleResult(spawnerModifiers, level);
    }

    public static List<ConfigServer.EntityModifier> getModifiersForEntity(List<ConfigServer.EntityModifier> definitions, EntityData entityData) {
        var entityModifiers = new ArrayList<ConfigServer.EntityModifier>();
        for(var entityModifier: definitions) {
            if (entityData.matches(entityModifier.entity_matches)) {
                entityModifiers.add(entityModifier);
            }
        }
        return entityModifiers;
    }

    // --- RESULT HELPERS

    public record DifficultySearchResult(Difficulty difficulty, LocationData locationData, LocationData.Match match) {
        @Nullable public Identifier matchId() { return match != null ? match.id() : null; }
    }

    @Nullable
    public static Difficulty getDifficulty(LocationData locationData, @Nullable Identifier sourceId, ServerWorld world) {
        var result = getDifficultyResult(locationData, sourceId, ScalingGoal.ENTITY, world);
        return (result != null) ? result.difficulty() : null;
    }

    @Nullable
    private static Difficulty findDifficulty(ConfigServer.DifficultyReference reference) {
        if (reference == null || reference.name == null || reference.name.isEmpty()) return null;

        for(var entry: DifficultyTypes.resolved) {
            if (reference.name.equals(entry.name)) {
                var rewardLevel = reference.reward_level != null ? reference.reward_level : reference.level;
                var entityLevel = reference.entity_level != null ? reference.entity_level : reference.level;
                return new Difficulty(entry, reference.level, entityLevel, rewardLevel);
            }
        }
        return null;
    }

    // --- UTILS (Regex & Tags)

    public static final String ANY = "*";
    public static final String TAG_PREFIX = "#";
    public static final String REGEX_PREFIX = "~";
    public static final String NEGATE_PREFIX = "!";

    public static <T> boolean universalMatch(@Nullable RegistryEntry<T> entry, RegistryKey<Registry<T>> registryKey, @Nullable String pattern) {
        if (pattern == null || pattern.isEmpty() || pattern.equals(ANY)) return true;
        if (entry == null) return false;

        if (pattern.startsWith(NEGATE_PREFIX)) return !entryMatches(entry, registryKey, pattern.substring(1));
        return entryMatches(entry, registryKey, pattern);
    }

    public static <T> boolean universalMatch(Identifier id, @Nullable String pattern) {
        if (pattern == null || pattern.isEmpty() || pattern.equals(ANY)) return true;
        if (pattern.startsWith(NEGATE_PREFIX)) return !idMatches(id, pattern.substring(1));
        return idMatches(id, pattern);
    }

    public static <T> boolean entryMatches(RegistryEntry<T> entry, RegistryKey<Registry<T>> registryKey, String pattern) {
        if (pattern.startsWith(TAG_PREFIX)) {
            var tag = TagKey.of(registryKey, CIdentifier.of(pattern.substring(1)));
            return entry.isIn(tag);
        }
        return idMatches(entry.getKey().get().getValue(), pattern);
    }

    public static boolean idMatches(Identifier id, String pattern) {
        var idString = id.toString();
        if (pattern.startsWith(REGEX_PREFIX)) {
            return regexMatches(idString, pattern.substring(1));
        }
        return idString.equals(pattern);
    }

    public static boolean regexMatches(String subject, String regex) {
        if (subject == null) return false;
        if (regex == null || regex.isEmpty()) return true;
        try {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            return pattern.matcher(subject).find();
        } catch (Exception e) {
            return false;
        }
    }
}