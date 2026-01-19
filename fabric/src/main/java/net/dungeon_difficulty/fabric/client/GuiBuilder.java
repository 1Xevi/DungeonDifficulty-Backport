package net.dungeon_difficulty.fabric.client;

import com.mojang.datafixers.types.Func;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import dev.isxander.yacl3.api.OptionGroup;
import me.shedaniel.autoconfig.AutoConfig;
import net.dungeon_difficulty.config.ConfigServer;
import net.dungeon_difficulty.config.ConfigServer.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

import java.util.List;

import static net.dungeon_difficulty.fabric.client.GuiUtils.*;


public class GuiBuilder {
    private static final ConfigServer DEFAULTS = new ConfigServer();

    // --- OVERLOADED EDITORS
    // ATTRIBUTES (leaf Node)
    private static void injectEditor(BuilderContext<OptionGroup.Builder> ctx, AttributeModifier item) {
        var builder = ctx.builder();

        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Attribute ID"))
                .description(OptionDescription.of(Text.literal("The registry ID of the attribute (e.g. 'minecraft:generic.attack_damage').")))
                .binding(item.attribute, () -> item.attribute, v -> item.attribute = v)
                .controller(opt -> DropdownStringControllerBuilder.create(opt).values(getRegistryIds(Registries.ATTRIBUTE)))
                .addListener(onUpdate(val -> item.attribute = val, ctx.refreshSelf()))
                .build());

        builder.option(Option.<Float>createBuilder()
                .name(Text.literal("Value"))
                .description(OptionDescription.of(Text.literal("The amount to modify the attribute by. Effect depends on the Operation.")))
                .binding(item.value, () -> item.value, v -> item.value = v)
                .controller(FloatFieldControllerBuilder::create)
                .addListener(onUpdate(val -> item.value = val, ctx.refreshSelf()))
                .build());

        builder.option(Option.<Operation>createBuilder()
                .name(Text.literal("Operation"))
                .description(OptionDescription.of(Text.literal("ADDITION: Adds a flat amount.\nMULTIPLY_BASE: Multiplies the base value (e.g. 0.5 = +50%).")))
                .binding(item.operation, () -> item.operation, v -> item.operation = v)
                .controller(opt -> EnumControllerBuilder.create(opt).enumClass(Operation.class))
                .addListener(onUpdate(val -> item.operation = val, ctx.refreshSelf()))
                .build());
    }

    // ENTITIES (recursive)
    private static void injectEditor(BuilderContext<OptionGroup.Builder> ctx, ConfigServer.EntityModifier item) {
        if (item.entity_matches == null) item.entity_matches = new ConfigServer.EntityModifier.Filters();
        var builder = ctx.builder();

        builder.option(Option.<ConfigServer.EntityModifier.Filters.Attitude>createBuilder()
                .name(Text.literal("Attitude"))
                .description(OptionDescription.of(Text.literal("Filter which mobs this rule applies to based on their hostility.")))
                .binding(item.entity_matches.attitude, () -> item.entity_matches.attitude, v -> item.entity_matches.attitude = v)
                .controller(opt -> EnumControllerBuilder.create(opt).enumClass(ConfigServer.EntityModifier.Filters.Attitude.class))
                //.addListener(onUpdate(val -> item.entity_matches.attitude = val, ctx.refreshSelf()))
                .build());

        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Entity Type"))
                .description(OptionDescription.of(Text.literal("The specific entity ID to target. If empty, matches all mobs allowed by the Attitude filter.")))
                .binding(item.entity_matches.type != null ? item.entity_matches.type : "",
                        () -> item.entity_matches.type, v -> item.entity_matches.type = v)
                .controller(opt -> DropdownStringControllerBuilder.create(opt).values(getRegistryIds(Registries.ENTITY_TYPE)))
                .addListener(onUpdate(val -> item.entity_matches.type = val, ctx.refreshSelf()))
                .build());

        // sublist for attributes
        addSubListButton(ctx,
                "Attributes", "Attribute",
                item.attributes,
                () -> new ConfigServer.AttributeModifier("minecraft:generic.movement_speed", 0.1f),
                a -> a.attribute,
                (attr) -> "§7Operation: " + attr.operation + "\n§7Value: " + attr.value,
                GuiBuilder::injectEditor);
    }

    // ITEMS (recursive)
    private static void injectEditor(BuilderContext<OptionGroup.Builder> ctx, ItemModifier item) {
        if (item.item_matches == null) item.item_matches = new ConfigServer.ItemModifier.Filters();
        var builder = ctx.builder();

        // item ID
        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Target Item ID"))
                .description(OptionDescription.of(Text.literal("Leave empty to match by Regex instead."))) // Explanation!
                .binding(item.item_matches.id,
                        () -> item.item_matches.id,
                        v -> item.item_matches.id = v)
                .controller(opt -> DropdownStringControllerBuilder.create(opt).values(getRegistryIds(Registries.ITEM)))
                .addListener(onUpdate(val -> item.item_matches.id = val, ctx.refreshSelf()))
                .build());

        // loot table Regex
        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Loot Table Regex"))
                .description(OptionDescription.of(Text.literal("Matches the loot table name (e.g. '.*chests/.*'). Leave empty to ignore.")))
                .binding(item.item_matches.loot_table_regex,
                        () -> item.item_matches.loot_table_regex,
                        v -> item.item_matches.loot_table_regex = v)
                .controller(StringControllerBuilder::create)
                .addListener(onUpdate(val -> item.item_matches.loot_table_regex = val, ctx.refreshSelf()))
                .build());

        // rarity regex
        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Rarity Regex"))
                .description(OptionDescription.of(Text.literal("Matches item rarity (common, uncommon, etc).")))
                .binding(item.item_matches.rarity_regex,
                        () -> item.item_matches.rarity_regex,
                        v -> item.item_matches.rarity_regex = v)
                .controller(StringControllerBuilder::create)
                .addListener(onUpdate(val -> item.item_matches.rarity_regex = val, ctx.refreshSelf()))
                .build());

        // attributes button (Recursive)
        addSubListButton(ctx,
                "Attributes", "Attribute",
                item.attributes,
                () -> new ConfigServer.AttributeModifier("minecraft:generic.armor", 0f),
                a -> a.attribute,
                (attr) -> "§7Op: " + attr.operation + " | Val: " + attr.value,
                GuiBuilder::injectEditor);
    }

    private static void injectEditor(BuilderContext<OptionGroup.Builder> ctx, ConfigServer.ScalingRule item) {
        var builder = ctx.builder();

        List<String> validDifficulties = ConfigServer.fetch().difficulty_types.stream()
                .map(type -> type.name)
                .sorted()
                .toList();

        // dimension
        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Dimension"))
                .description(OptionDescription.of(Text.literal("The Dimension ID where this rule applies (e.g. 'minecraft:the_nether').")))
                .binding(item.match.dimension, () -> item.match.dimension, v -> item.match.dimension = v)
                .controller(StringControllerBuilder::create)
                //.addListener(onUpdate(val -> item.match.dimension = val, ctx.refreshSelf()))
                .build());

        // structure
        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Structure"))
                .description(OptionDescription.of(Text.literal("If set, this rule only applies inside this specific structure.")))
                .binding(item.match.structure, () -> item.match.structure, v -> item.match.structure = v)
                .controller(opt -> DropdownStringControllerBuilder.create(opt)
                        .values(GuiUtils.getRegistryIds(RegistryKeys.STRUCTURE))
                )
                .addListener(onUpdate(val -> item.match.structure = val, ctx.refreshSelf()))
                .build());

        // biome
        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Biome"))
                .description(OptionDescription.of(Text.literal("If set, this rule only applies inside this specific biome.")))
                .binding(item.match.biome, () -> item.match.biome, v -> item.match.biome = v)
                .controller(opt -> DropdownStringControllerBuilder.create(opt)
                        .values(GuiUtils.getRegistryIds(RegistryKeys.BIOME))
                )
                .addListener(onUpdate(val -> item.match.biome = val, ctx.refreshSelf()))
                .build());

        // entity
        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Entity"))
                .description(OptionDescription.of(Text.literal("If set, this rule only applies inside this specific entity.")))
                .binding(item.match.entity, () -> item.match.entity, v -> item.match.entity = v)
                .controller(opt -> DropdownStringControllerBuilder.create(opt)
                        .values(GuiUtils.getRegistryIds(RegistryKeys.ENTITY_TYPE))
                )
                .addListener(onUpdate(val -> item.match.entity = val, ctx.refreshSelf())) //
                .build());


        // difficulty assignment
        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Difficulty Name"))
                .description(OptionDescription.of(Text.literal("The ID of the Difficulty Preset to apply here (e.g. 'adventure').")))
                .binding(item.difficulty.name, () -> item.difficulty.name, v -> item.difficulty.name = v)
                .controller(opt -> DropdownStringControllerBuilder.create(opt)
                        .values(validDifficulties) // Pass the list we generated above!
                )
                .addListener(onUpdate(val -> item.difficulty.name = val, ctx.refreshSelf()))
                .build());

        builder.option(Option.<Integer>createBuilder()
                .name(Text.literal("Difficulty Level"))
                .description(OptionDescription.of(Text.literal("The scaling multiplier level. Higher levels = stronger mobs/loot based on the Difficulty.")))
                .binding(item.difficulty.level, () -> item.difficulty.level, v -> item.difficulty.level = v)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                        .range(0, 10)
                        .step(1))
                //.addListener(onUpdate(val -> item.difficulty.level = val, ctx.refreshSelf()))
                .build());

        builder.option(Option.<Integer>createBuilder()
                .name(Text.literal("Difficulty Level"))
                .description(OptionDescription.of(Text.literal("The scaling multiplier level. Higher levels = stronger mobs/loot based on the Difficulty.")))
                .binding(item.difficulty.level, () -> item.difficulty.level, v -> item.difficulty.level = v)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                        .range(0, 10)
                        .step(1))
                //.addListener(onUpdate(val -> item.difficulty.level = val, ctx.refreshSelf()))
                .build());

        // sublist for overrides (recursive)
        addSubListButton(
                ctx,
                "Sub-Overrides", "Override",
                item.overrides,
                ConfigServer.ScalingRule::new,
                r -> r.match.structure.isEmpty() ? r.match.dimension : r.match.structure,
                r -> "§7Difficulty: " + r.difficulty.name + " (Lvl " + r.difficulty.level + ")",
                GuiBuilder::injectEditor);
    }


    // -- CATEGORY BUILDERS

    private static ConfigCategory buildCategory(ConfigServer.Meta key, ConfigServer config, Screen screen) {
        var builder = ConfigCategory.createBuilder().name(Text.literal("Global"));

        builder.option(buildBool(
                "Sanitize Config",
                "Automatically validates and repairs the config file on startup to prevent crashes.",
                DEFAULTS.meta.sanitize_config,
                () -> config.meta.sanitize_config,
                v -> config.meta.sanitize_config = v
        ));

        builder.option(Option.<ConfigServer.RoundingMode>createBuilder()
                .name(Text.literal("Rounding Precision"))
                .description(OptionDescription.of(Text.literal("Snaps attribute values to the nearest grid. Use 'Very High' for speed, 'Whole Numbers' for damage.")))
                .binding(DEFAULTS.meta.rounding_mode,
                        () -> config.meta.rounding_mode,
                        v -> config.meta.rounding_mode = v)
                .controller(opt -> EnumControllerBuilder.create(opt)
                        .enumClass(ConfigServer.RoundingMode.class)
                        .formatValue(v -> Text.literal(v.toString())))
                .build());

        builder.option(buildBool(
                "Merge Item Modifiers",
                "If enabled, difficulty bonuses are added to existing attributes. If disabled, they replace them.",
                DEFAULTS.meta.merge_item_modifiers,
                () -> config.meta.merge_item_modifiers,
                v -> config.meta.merge_item_modifiers = v
        ));

        builder.option(buildBool(
                "Global Loot Scaling",
                "Master switch to enable or disable all loot modification features.",
                DEFAULTS.meta.global_loot_scaling,
                () -> config.meta.global_loot_scaling,
                v -> config.meta.global_loot_scaling = v
        ));

        builder.option(buildBool(
                "Override Enchant Rarity",
                "Allows generating rare enchantments more frequently on high-level items.",
                DEFAULTS.meta.enable_scaled_items_rarity,
                () -> config.meta.enable_overriding_enchantment_rarity,
                v -> config.meta.enable_overriding_enchantment_rarity = v
        ));

        builder.option(buildBool(
                "Enable Scaled Items Rarity",
                "Changes the item name color (Common, Rare, Epic) based on its power level.",
                DEFAULTS.meta.enable_scaled_items_rarity,
                () -> config.meta.enable_scaled_items_rarity,
                v -> config.meta.enable_scaled_items_rarity = v
        ));

        return builder.build();
    }

    private static  ConfigCategory buildCategory(ConfigServer.Announcement key, ConfigServer config, Screen screen) {
        var builder = ConfigCategory.createBuilder().name(Text.literal("Announcements"));
        var ctx = new BuilderContext<>(builder, screen, () -> MinecraftClient.getInstance().setScreen(create(screen)));

        builder.option(buildBool(
                "Enabled",
                "Show titles on area change.",
                DEFAULTS.announcement.enabled,
                () -> config.announcement.enabled, v -> config.announcement.enabled = v));


        builder.option(Option.<Integer>createBuilder()
                .name(Text.literal("Cooldown (Seconds)"))
                .binding(
                        DEFAULTS.announcement.reannounce_cooldown_seconds,
                        () -> config.announcement.reannounce_cooldown_seconds, v -> config.announcement.reannounce_cooldown_seconds = v)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                        .range(0, 300)
                        .step(1))
                .build());

        builder.option(Option.<Integer>createBuilder()
                .name(Text.literal("Interval Check (Seconds)"))
                .binding(DEFAULTS.announcement.check_interval_seconds,
                        () -> config.announcement.check_interval_seconds, v -> config.announcement.check_interval_seconds = v)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                        .range(1, 10)
                        .step(1))
                .build());

        return builder.build();
    }

    private static ConfigCategory buildCategory(ConfigServer key, ConfigServer config, Screen screen) {
        var builder = ConfigCategory.createBuilder().name(Text.literal("Difficulty Settings"));
        var ctx = new BuilderContext<>(builder, screen, () -> MinecraftClient.getInstance().setScreen(create(screen)));

        builder.group(OptionGroup.createBuilder()
                .name(Text.literal("§6§l[ Per-Player Scaling ]"))
                .description(OptionDescription.of(Text.literal("If enabled, difficulty scales dynamically based on how many players are in the area.")))
                .collapsed(false) // Set to true if you want it closed by default

                .option(buildBool(
                        "Enabled",
                        "If enabled, difficulty increases based on the number of players nearby.",
                        DEFAULTS.per_player_difficulty.enabled,
                        () -> config.per_player_difficulty.enabled,
                        v -> config.per_player_difficulty.enabled = v
                ))

                .option(Option.<ConfigServer.PerPlayerDifficulty.Counting>createBuilder()
                        .name(Text.literal("Counting Mode"))
                        .description(OptionDescription.of(Text.literal("Determines how would the difficulty scales relative to player count\n\n- §eEVERYWHERE§r§f: Counts all players in world/server, regardless of dimension they're in\n\n - §eDIMENSIONS§r§f: Counts all players from each dimensions")))
                        .binding(
                                DEFAULTS.per_player_difficulty.counting,
                                () -> config.per_player_difficulty.counting,
                                v -> config.per_player_difficulty.counting = v
                        )
                        .controller(opt -> EnumControllerBuilder.create(opt)
                                .enumClass(ConfigServer.PerPlayerDifficulty.Counting.class)
                        )
                        .build())

                .option(Option.<Integer>createBuilder()
                        .name(Text.literal("Max Players Cap"))
                        .description(OptionDescription.of(Text.literal("The maximum number of players that contribute to the difficulty scaling.")))
                        .binding(
                                DEFAULTS.per_player_difficulty.cap,
                                () -> config.per_player_difficulty.cap,
                                v -> config.per_player_difficulty.cap = v
                        )
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(1, 20).step(1))
                        .build())
                .build()
        );

        addGenericList(
                ctx, "§6§l[ Difficulty Presets ] ", "Preset",
                config.difficulty_types,
                () -> new ConfigServer.DifficultyType("new_preset"),
                (preset) -> {
                    String p = (preset.parent == null || preset.parent.isEmpty()) ? "Root" : preset.parent;
                    return "§f" + preset.name + " §7[<" + p + "]";
                },
                (preset) -> "§7Parent: " + (preset.parent.isEmpty() ? "None" : preset.parent) +
                        "\n§7Contains " + preset.entities.size() + " entity rules.",
                (c, preset) -> {
                    var b = c.builder();
                    b.option(Option.<String>createBuilder()
                            .name(Text.literal("Preset Name"))
                            .binding(preset.name, () -> preset.name, v -> preset.name = v)
                            .controller(StringControllerBuilder::create)
                            //.addListener(onUpdate(v -> preset.name = v, c.refreshSelf()))
                            .build());

                    addSubListButton(
                            c,"Entity Rules", "Rule",
                            preset.entities,
                            ConfigServer.EntityModifier::new,
                            EntityModifier::getSummary,
                            (entityRule) -> {
                                int count = entityRule.attributes.size();
                                String xp = (entityRule.experience_multiplier > 0) ? " | XP x" + entityRule.experience_multiplier : "";
                                return "§7" + count + " Attribute Modifiers" + xp;
                            },
                            GuiBuilder::injectEditor
                    );
                }
        );

        addGenericList(
                ctx,
                "§6§l[ Hierarchical Rules ]", "Rule",
                config.scaling_rules,
                ConfigServer.ScalingRule::new,
                (rule) -> {
                    String target = rule.match.dimension.isEmpty() ? "Global" : rule.match.dimension;
                    if (!rule.match.structure.isEmpty()) target = rule.match.structure;
                    return "§f" + target + " §7(Level " + rule.difficulty.level + ")";
                },
                (rule) -> "§7Overrides: " + rule.overrides.size() + "\n§7Difficulty: " + rule.difficulty.name,
                GuiBuilder::injectEditor);

        return builder.build();
    }

    private static ConfigCategory buildCategory(ConfigServer.Rewards key, ConfigServer config, Screen screen) {
        var builder = ConfigCategory.createBuilder().name(Text.literal("Loot Scaling"));
        var ctx = new BuilderContext<>(builder, screen, () -> MinecraftClient.getInstance().setScreen(create(screen)));

        addGenericList(
                ctx,
                "§6§l[ Weapons List ]", "Weapon Rule",
                config.loot_scaling.weapons,
                ConfigServer.ItemModifier::new,
                ConfigServer.ItemModifier::getSummary,

                (item) -> {
                    if (item.attributes.isEmpty()) return "§cNo attributes defined.";
                    // Preview the first attribute
                    var first = item.attributes.get(0);
                    return "§7" + first.getSummary() + (item.attributes.size() > 1 ? "..." : "");
                },
                (c, item) -> {
                    if (item.item_matches == null) item.item_matches = new ConfigServer.ItemModifier.Filters();
                    var b = c.builder();
                    // id dropdown
                    b.option(Option.<String>createBuilder()
                            .name(Text.literal("Target Item ID"))
                            .description(OptionDescription.of(Text.literal("Leave empty to match by Regex.")))
                            .binding("", () -> item.item_matches.id, v -> item.item_matches.id = v)
                            .controller(opt -> DropdownStringControllerBuilder.create(opt).values(getRegistryIds(Registries.ITEM)))
                            .build());

                    // regex field
                    b.option(Option.<String>createBuilder()
                            .name(Text.literal("Loot Table Regex"))
                            .binding("", () -> item.item_matches.loot_table_regex, v -> item.item_matches.loot_table_regex = v)
                            .controller(StringControllerBuilder::create)
                            .build());

                    // attribute sublist (Recursive)
                    addSubListButton(
                            c,
                            "Attributes", "Attribute",
                            item.attributes,
                            () -> new ConfigServer.AttributeModifier("minecraft:generic.attack_damage", 1.0f),
                            AttributeModifier::getSummary,
                            (attr) -> "§7Operation: " + attr.operation + "\n§7Value: " + attr.value,
                            GuiBuilder::injectEditor
                    );
                }
        );

        // ARMOR LIST
        addGenericList(ctx,
                "§6§l[ Armor List ]",
                "Armor Rule", config.loot_scaling.armor,
                ConfigServer.ItemModifier::new,
                ConfigServer.ItemModifier::getSummary,

                (item) -> {
                    if (item.attributes.isEmpty()) return "§cNo attributes defined.";
                    // Preview the first attribute
                    var first = item.attributes.get(0);
                    return "§7" + first.getSummary() + (item.attributes.size() > 1 ? "..." : "");
                },
                (c, item) -> {
                    if (item.item_matches == null) item.item_matches = new ConfigServer.ItemModifier.Filters();

                    var b = c.builder();
                    b.option(Option.<String>createBuilder()
                            .name(Text.literal("Target Item ID"))
                            .binding("", () -> item.item_matches.id, v -> item.item_matches.id = v)
                            .controller(opt -> DropdownStringControllerBuilder.create(opt).values(getRegistryIds(Registries.ITEM)))
                            .build());

                    b.option(Option.<String>createBuilder()
                            .name(Text.literal("Loot Table Regex"))
                            .binding("", () -> item.item_matches.loot_table_regex, v -> item.item_matches.loot_table_regex = v)
                            .controller(StringControllerBuilder::create)
                            .build());

                    addSubListButton(c,
                            "Attributes", "Attribute",
                            item.attributes,
                            () -> new ConfigServer.AttributeModifier("minecraft:generic.armor", 1.0f),
                            AttributeModifier::getSummary,
                            (attr) -> "§7Operation: " + attr.operation + "\n§7Value: " + attr.value,
                            GuiBuilder::injectEditor
                    );
                }
        );

        return builder.build();
    }


    // -- MAIN ENTRY POINT

    public static Screen create(Screen parent) {
        var config = ConfigServer.fetch();

        return YetAnotherConfigLib.createBuilder()
                .title(Text.literal("Dungeon Difficulty"))
                .category(buildCategory(config.meta, config, parent))
                .category(buildCategory(config.announcement, config, parent))
                .category(buildCategory(config, config, parent))
                .category(buildCategory(config.loot_scaling, config, parent))
                .save(() -> {
                    AutoConfig.getConfigHolder(ConfigServer.class).save();

                    MinecraftClient.getInstance().execute(() -> {
                        MinecraftClient.getInstance().setScreen(create(parent));
                    });
                })
                .build()
                .generateScreen(parent);
    }
}