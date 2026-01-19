package net.dungeon_difficulty.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.annotation.Config;
import net.dungeon_difficulty.DungeonDifficulty;
import net.dungeon_difficulty.logic.DifficultyTypes;
import net.dungeon_difficulty.util.Debugger;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;

@Config(name = DungeonDifficulty.MODID + "-server")
public class ConfigServer implements ConfigData {
    public static ConfigServer fetch() { return AutoConfig.getConfigHolder(ConfigServer.class).getConfig(); }

    public ConfigServer() {
        Debugger.log("DEBUG: Populating defaults...");
        Default.populate(this);
    }

    @Override
    public void validatePostLoad() {
        if (difficulty_types == null || difficulty_types.isEmpty()) {
            Debugger.log("Config missing/empty. Repopulating defaults...");
            Default.populate(this);
        }

        try {
            DifficultyTypes.resolve(this);
        } catch (Exception e) {
            Debugger.log("Failed to auto-resolve difficulty types: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Meta meta = new Meta();
    public Announcement announcement = new Announcement();
    public PerPlayerDifficulty per_player_difficulty = new PerPlayerDifficulty();
    public List<DifficultyType> difficulty_types = new ArrayList<>();
    public Rewards loot_scaling = new Rewards();
    public List<ScalingRule> scaling_rules = new ArrayList<>();

    public static class Meta { public Meta() { }
        public boolean sanitize_config = true;
        public RoundingMode rounding_mode = RoundingMode.HALVES;
        public boolean merge_item_modifiers = true;
        public boolean global_loot_scaling = true;
        public boolean enable_overriding_enchantment_rarity = true;
        public boolean enable_scaled_items_rarity = true;
    }

    public static class Announcement { public Announcement() { }
        public boolean enabled = true;
        public int check_interval_seconds = 5;
        public int reannounce_cooldown_seconds = 20;
        public int history_size = 10;
    }

    public static class PerPlayerDifficulty { public PerPlayerDifficulty() { }
        public boolean enabled = true;
        public enum Counting { EVERYWHERE, DIMENSION }
        public Counting counting = Counting.EVERYWHERE;
        public int cap = 10;
        public List<EntityModifier> entities = List.of();
    }

    public static class DifficultyType { public DifficultyType() { }
        public String name = "custom";
        public String parent = "";
        @Nullable public String translation_code;
        @Nullable public Boolean allow_loot_scaling;
        public List<EntityModifier> entities = new ArrayList<>();
        public DifficultyType(String name) {
            this.name = name;
        }
    }
    ;
    public static class Rewards { public Rewards() { }
        public List<ItemModifier> armor = new ArrayList<>();
        public List<ItemModifier> weapons = new ArrayList<>();

        public static class SmithingUpgrade { public SmithingUpgrade() { }
            public boolean enabled = true;
            public int add_upon_upgrade = 0;
            public float multiply_upon_upgrade = 1;
        }
        public SmithingUpgrade smithing_upgrade = new SmithingUpgrade();
    }

    public static class DifficultyReference { public DifficultyReference() { }
        public String name = "custom";
        public int level = 0;
        @Nullable public Integer entity_level;
        @Nullable public Integer reward_level;
        public DifficultyReference(String name, int level) {
            this.name = name;
            this.level = level;
        }
    }

    public static class ScalingRule {
        public ScalingRule() {
        }

        public static class Context {
            public String dimension = "";
            public String biome = "";
            public String structure = "";
            public String entity = "";
        }

        public Context match = new Context();
        public DifficultyReference difficulty = new DifficultyReference();
        public List<ScalingRule> overrides = new ArrayList<>();
    }

    public enum Operation { ADDITION, MULTIPLY_BASE }

    public enum RoundingMode {
        NONE(0.0, "None (Precise)"),
        TENTHS(0.1, "Very High (0.1)"),
        FIFTHS(0.2, "High (0.2)"),
        QUARTERS(0.25, "Medium (0.25)"),
        HALVES(0.5, "Low (0.5)"),
        INTEGERS(1.0, "Whole Numbers (1.0)");

        public final double value;
        private final String label;

        RoundingMode(double value, String label) {
            this.value = value;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static class EntityModifier { public EntityModifier() { }
        public static class Filters {
            public enum Attitude {
                FRIENDLY, HOSTILE, ANY
            }
            public Attitude attitude = Attitude.ANY;
            public String type = "";
        }
        public Filters entity_matches = new Filters();
        @Nullable public SpawnerModifier spawners = null;
        public List<AttributeModifier> attributes = new ArrayList<>();
        public float experience_multiplier = 0;

        public String getSummary() {
            String type = (entity_matches.type == null || entity_matches.type.isEmpty())
                    ? "ALL MOBS"
                    : entity_matches.type.replace("minecraft:", "");

            String attitude = (entity_matches.attitude == Filters.Attitude.ANY)
                    ? ""
                    : " (" + entity_matches.attitude.name() + ")";

            return type + attitude;
        }
    }

    public static class ItemModifier { public ItemModifier() { }
        public static class Filters {
            public String id = "";
            public String loot_table_regex = "";
            public String rarity_regex = "";
        }
        @Nullable public Filters item_matches = new Filters();

        public List<AttributeModifier> attributes = new ArrayList<>();

        public String getSummary() {
            if (item_matches == null) return "§cInvalid Rule";

            if (item_matches.id != null && !item_matches.id.isEmpty()) {
                return "§b" + item_matches.id.replace("minecraft:", "");
            }

            if (item_matches.loot_table_regex != null && !item_matches.loot_table_regex.isEmpty()) {
                return "§eTable: " + item_matches.loot_table_regex;
            }

            if (item_matches.rarity_regex != null && !item_matches.rarity_regex.isEmpty()) {
                return "§dRarity: " + item_matches.rarity_regex;
            }

            return "§f* Any Item";
        }
    }

    public static class AttributeModifier { public AttributeModifier() { }
        public String attribute;
        public Operation operation = Operation.MULTIPLY_BASE;
        public float randomness = 0;
        public float value = 0;
        public float offset = 0;

        public AttributeModifier(String attribute, float value) {
            this.attribute = attribute;
            this.value = value;
        }

        public String getSummary() {
            String attrName = attribute.replace("minecraft:generic.", "").replace("minecraft:player.", "");
            boolean doesMultiply = (operation == Operation.MULTIPLY_BASE);
            String suffix = doesMultiply ? "%" : "";
            var attrValue = doesMultiply ? value * 100 : value;

            return String.format("+%.1f%s %s", attrValue, suffix, attrName);
        }

        private static final Random rng = new Random();
        public float randomizedValue(int level) {
            var value = this.value * level;
            var randomizedValue = (randomness > 0)
                    ?  rng.nextFloat(value - randomness, value + randomness)
                    : value;
            return this.offset + randomizedValue;
        }
    }

    public static class SpawnerModifier { public SpawnerModifier() { }
        public float spawn_range_multiplier = 0;
        public float spawn_count_multiplier = 0;
        public float max_nearby_entities_multiplier = 0;
        public float min_spawn_delay_multiplier = 0;
        public float max_spawn_delay_multiplier = 0;
        public float required_player_range_multiplier = 0;
    }
}
