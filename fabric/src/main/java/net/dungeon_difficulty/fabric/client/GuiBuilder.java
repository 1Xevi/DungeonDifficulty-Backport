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
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.ObjectInputFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class GuiBuilder {
    private static final ConfigServer DEFAULTS = new ConfigServer();

    // GENERIC FRAME (Used for sub-screens like editing Attributes)
    private static <T> Screen createGeneric(
            Screen parent,
            Text title,
            T item,
            Consumer<T> saveConsumer,
            @Nullable Runnable deleteAction,
            Consumer<ConfigCategory.Builder> contentInjector,
            @Nullable Runnable onSaveAndClose
    ) {
        var categoryBuilder = ConfigCategory.createBuilder().name(title);
        contentInjector.accept(categoryBuilder);

        if (deleteAction != null) {
            categoryBuilder.option(ButtonOption.createBuilder()
                    .name(Text.literal("§c[X] Delete Entry"))
                    .description(OptionDescription.of(Text.literal("Permanently remove this entry.")))
                    .action((s, b) -> deleteAction.run())
                    .build());
        }

        return YetAnotherConfigLib.createBuilder()
                .title(title)
                .category(categoryBuilder.build())
                .save(() -> {
                    saveConsumer.accept(item);
                    if (onSaveAndClose != null) onSaveAndClose.run();
                })
                .build()
                .generateScreen(parent);
    }

    // drown down list
    private static <T> void addGenericList(
            ConfigCategory.Builder category,
            String listTitle,
            String itemLabel,
            List<T> list,
            Screen parent,
            Supplier<T> constructor,
            Function<T, String> nameProvider,
            Function<T, String> descriptionProvider,
            EditorInjector<T> injector
    ) {
        // separator
        var headerGroup = OptionGroup.createBuilder()
                .name(Text.literal(listTitle)) //
                .collapsed(false);

        // add button
        headerGroup.option(ButtonOption.createBuilder()
                .name(Text.literal("§a[+] Add New " + itemLabel))
                .action((s, b) -> {
                    list.add(constructor.get());
                    AutoConfig.getConfigHolder(ConfigServer.class).save();
                    MinecraftClient.getInstance().setScreen(GuiBuilder.create(parent));
                })
                .build());

        category.group(headerGroup.build());

        // LOOPING
        for (int i = 0; i < list.size(); i++) {
            int finalI = i;
            T item = list.get(i);

            var itemGroup = OptionGroup.createBuilder()
                    .name(Text.literal(nameProvider.apply(item))) // e.g. "preset: adventure"
                    .description(OptionDescription.of(Text.literal(descriptionProvider.apply(item))))
                    .collapsed(true); // Closed by default

            // Inject fields
            injector.inject(itemGroup, item, parent);

            // Delete Button
            itemGroup.option(ButtonOption.createBuilder()
                    .name(Text.literal("§c[X] Delete this entry"))
                    .action((s, b) -> {
                        list.remove(finalI);
                        AutoConfig.getConfigHolder(ConfigServer.class).save();
                        MinecraftClient.getInstance().setScreen(GuiBuilder.create(parent));
                    })
                    .build());

            category.group(itemGroup.build());
        }
    }

    private static <T> void addSubListButton(
            OptionGroup.Builder builder,
            String title, String label,
            List<T> list,
            Screen parent,
            Supplier<T> ctor,
            Function<T, String> namer,
            Function<T, String> descriptionProvider,
            EditorInjector<T> injector) {
        StringBuilder previewText = new StringBuilder("§7Contents:");

        if (list.isEmpty()) {
            previewText.append("\n§8(Empty)");
        } else {
            int limit = 5;
            for (int i = 0; i < Math.min(list.size(), limit); i++) {
                previewText.append("\n§7- ").append(namer.apply(list.get(i)));
            }

            // If there are more, show a count
            if (list.size() > limit) {
                previewText.append("\n§8... and ").append(list.size() - limit).append(" more.");
            }
        }

        builder.option(ButtonOption.createBuilder()
                .name(Text.literal("§e[>] Edit " + title + " §7(" + list.size() + ")"))
                .description(OptionDescription.of(Text.literal(previewText.toString())))
                .action((s, b) -> MinecraftClient.getInstance().setScreen(
                        createGeneric(
                                s,
                                Text.literal(title),
                                list,
                                (saved) -> AutoConfig.getConfigHolder(ConfigServer.class).save(),
                                null,
                                (cat) -> addGenericList(cat, title, label, list, s, ctor, namer, descriptionProvider, injector),

                                () -> MinecraftClient.getInstance().setScreen(GuiBuilder.create(parent))
                        )
                ))
                .build());
    }

    // --- OVERLOADED EDITORS ---

    // ATTRIBUTES (Leaf Node)
    private static void injectEditor(OptionGroup.Builder builder, AttributeModifier item, Screen parent) {

        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Attribute ID"))
                .binding(item.attribute, () -> item.attribute, v -> item.attribute = v)
                .controller(opt -> DropdownStringControllerBuilder.create(opt).values(getRegistryIds(Registries.ATTRIBUTE)))
                .build());

        builder.option(Option.<Float>createBuilder()
                .name(Text.literal("Value"))
                .binding(item.value, () -> item.value, v -> item.value = v)
                .controller(FloatFieldControllerBuilder::create)
                .build());

        builder.option(Option.<Operation>createBuilder()
                .name(Text.literal("Operation"))
                .binding(item.operation, () -> item.operation, v -> item.operation = v)
                .controller(opt -> EnumControllerBuilder.create(opt).enumClass(Operation.class))
                .build());
    }

    // ENTITIES (Recursive)
    private static void injectEditor(OptionGroup.Builder builder, ConfigServer.EntityModifier item, Screen parent) {
        if (item.entity_matches == null) item.entity_matches = new ConfigServer.EntityModifier.Filters();

        builder.option(Option.<ConfigServer.EntityModifier.Filters.Attitude>createBuilder()
                .name(Text.literal("Attitude"))
                .binding(item.entity_matches.attitude, () -> item.entity_matches.attitude, v -> item.entity_matches.attitude = v)
                .controller(opt -> EnumControllerBuilder.create(opt).enumClass(ConfigServer.EntityModifier.Filters.Attitude.class))
                .build());

        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Entity Type"))
                .binding(item.entity_matches.type != null ? item.entity_matches.type : "minecraft:pig",
                        () -> item.entity_matches.type, v -> item.entity_matches.type = v)
                .controller(opt -> DropdownStringControllerBuilder.create(opt).values(getRegistryIds(Registries.ENTITY_TYPE)))
                .build());

        // Sub-list for Attributes
        addSubListButton(builder, "Attributes", "Attribute", item.attributes, parent,
                () -> new ConfigServer.AttributeModifier("minecraft:generic.movement_speed", 0.1f),
                a -> a.attribute,
                (attr) -> "§7Operation: " + attr.operation + "\n§7Value: " + attr.value,
                GuiBuilder::injectEditor);
    }

    // ITEMS (Recursive)
    private static void injectEditor(OptionGroup.Builder builder, ItemModifier item, Screen parent) {
        if (item.item_matches == null) item.item_matches = new ConfigServer.ItemModifier.Filters();

        // Item ID
        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Target Item ID"))
                .description(OptionDescription.of(Text.literal("Leave empty to match by Regex instead."))) // Explanation!
                .binding(item.item_matches.id,
                        () -> item.item_matches.id,
                        v -> item.item_matches.id = v)
                .controller(opt -> DropdownStringControllerBuilder.create(opt).values(getRegistryIds(Registries.ITEM)))
                .build());

        // Loot Table Regex
        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Loot Table Regex"))
                .description(OptionDescription.of(Text.literal("Matches the loot table name (e.g. '.*chests/.*'). Leave empty to ignore.")))
                .binding(item.item_matches.loot_table_regex,
                        () -> item.item_matches.loot_table_regex,
                        v -> item.item_matches.loot_table_regex = v)
                .controller(StringControllerBuilder::create) // Standard text box
                .build());

        // Rarity Regex
        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Rarity Regex"))
                .description(OptionDescription.of(Text.literal("Matches item rarity (common, uncommon, etc).")))
                .binding(item.item_matches.rarity_regex,
                        () -> item.item_matches.rarity_regex,
                        v -> item.item_matches.rarity_regex = v)
                .controller(StringControllerBuilder::create)
                .build());

        // Attributes Button (Recursive)
        addSubListButton(builder, "Attributes", "Attribute", item.attributes, parent,
                () -> new ConfigServer.AttributeModifier("minecraft:generic.armor", 0f),
                a -> a.attribute,
                (attr) -> "§7Op: " + attr.operation + " | Val: " + attr.value,
                GuiBuilder::injectEditor);
    }

    // E. DIFFICULTY TYPES (Recursive)
    private static void injectEditor(OptionGroup.Builder builder, ConfigServer.DifficultyType item, Screen parent) {
        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Preset Name"))
                .binding(item.name, () -> item.name, v -> item.name = v)
                .controller(StringControllerBuilder::create)
                .build());

        builder.option(buildBool(
                "Allow Loot Scaling",
                "Allow loots scaling for locations tagged with this difficulty",
                true,
                () -> item.allow_loot_scaling,
                v -> item.allow_loot_scaling = v
        ));

        // Sub-list for Entity Rules in this preset
        addSubListButton(builder, "Entity Rules", "Rule", item.entities, parent,
                ConfigServer.EntityModifier::new,
                e -> e.entity_matches.type,
                (entityRule) -> {
                    int attrCount = entityRule.attributes.size();
                    String xp = (entityRule.experience_multiplier > 0) ? " | XP: x" + entityRule.experience_multiplier : "";
                    return "§7Modifies " + attrCount + " attributes" + xp;
                },
                GuiBuilder::injectEditor);
    }

    private static void injectEditor(OptionGroup.Builder builder, ConfigServer.ScalingRule item, Screen parent) {
        // 1Match Criteria
        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Dimension"))
                .binding(item.match.dimension, () -> item.match.dimension, v -> item.match.dimension = v)
                .controller(StringControllerBuilder::create)
                .build());

        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Structure"))
                .binding(item.match.structure, () -> item.match.structure, v -> item.match.structure = v)
                .controller(opt -> {
                    // SAFE LOOKUP: Check if world is null (Main Menu) vs loaded (In-Game)
                    var client = MinecraftClient.getInstance();
                    var values = (client.world != null)
                            ? getRegistryIds(client.world.getRegistryManager().get(RegistryKeys.STRUCTURE))
                            : List.of(""); // Empty list if at Main Menu

                    return DropdownStringControllerBuilder.create(opt).values(values);
                })
                .build());


        // Difficulty Assignment
        builder.option(Option.<String>createBuilder()
                .name(Text.literal("Difficulty Name"))
                .binding(item.difficulty.name, () -> item.difficulty.name, v -> item.difficulty.name = v)
                .controller(StringControllerBuilder::create)
                .build());

        builder.option(Option.<Integer>createBuilder()
                .name(Text.literal("Difficulty Level"))
                .binding(item.difficulty.level, () -> item.difficulty.level, v -> item.difficulty.level = v)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                        .range(0, 10)
                        .step(1))
                .build());

        // Sub-list for Overrides (Recursive)
        addSubListButton(builder, "§6§l[ Sub-Overrides ]", "Override", item.overrides, parent,
                ConfigServer.ScalingRule::new,
                r -> r.match.structure.isEmpty() ? r.match.dimension : r.match.structure,
                r -> "§7Difficulty: " + r.difficulty.name + " (Lvl " + r.difficulty.level + ")",
                GuiBuilder::injectEditor);


    }

    // -- CATEGORY BUILDERS
    private static ConfigCategory buildCategory(ConfigServer.Meta key, ConfigServer config, Screen parent ) {
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

    private static  ConfigCategory buildCategory(ConfigServer.Announcement key, ConfigServer config, Screen parent) {
        var builder = ConfigCategory.createBuilder().name(Text.literal("Announcements"));

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


    private static ConfigCategory buildCategory(ConfigServer key, ConfigServer config, Screen parent) {
        var builder = ConfigCategory.createBuilder().name(Text.literal("Difficulty Settings"));

        builder.group(OptionGroup.createBuilder()
                .name(Text.literal("§6§l[ Per-Player Scaling ]"))
                .description(OptionDescription.of(Text.literal("Settings for scaling difficulty based on player count.")))
                .collapsed(false) // Set to true if you want it closed by default

                // Option A: Enabled
                .option(buildBool(
                        "Enabled",
                        "If enabled, difficulty increases based on the number of players nearby.",
                        DEFAULTS.per_player_difficulty.enabled,
                        () -> config.per_player_difficulty.enabled,
                        v -> config.per_player_difficulty.enabled = v
                ))

                // Option B: Counting Mode
                .option(Option.<ConfigServer.PerPlayerDifficulty.Counting>createBuilder()
                        .name(Text.literal("Counting Mode"))
                        .description(OptionDescription.of(Text.literal("Global (Everywhere) or Dimension-specific.")))
                        .binding(
                                DEFAULTS.per_player_difficulty.counting,
                                () -> config.per_player_difficulty.counting,
                                v -> config.per_player_difficulty.counting = v
                        )
                        .controller(opt -> EnumControllerBuilder.create(opt)
                                .enumClass(ConfigServer.PerPlayerDifficulty.Counting.class)
                        )
                        .build())

                // Option C: Cap
                .option(Option.<Integer>createBuilder()
                        .name(Text.literal("Max Players Cap"))
                        .binding(
                                DEFAULTS.per_player_difficulty.cap,
                                () -> config.per_player_difficulty.cap,
                                v -> config.per_player_difficulty.cap = v
                        )
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(1, 20).step(1))
                        .build())
                .build()
        );

        addGenericList(builder, "§6§l[ Difficulty Presets ] ", "Preset", config.difficulty_types, parent,
                () -> new ConfigServer.DifficultyType("new_preset"),
                (preset) -> {
                    String p = (preset.parent == null || preset.parent.isEmpty()) ? "Root" : preset.parent;
                    return "§f" + preset.name + " §7[<" + p + "]";
                },
                (preset) -> "§7Parent: " + (preset.parent.isEmpty() ? "None" : preset.parent) +
                        "\n§7Contains " + preset.entities.size() + " entity rules.",
                (b, preset, p) -> {
                    b.option(Option.<String>createBuilder()
                            .name(Text.literal("Preset Name"))
                            .binding(preset.name, () -> preset.name, v -> preset.name = v)
                            .controller(StringControllerBuilder::create)
                            .build());

                    GuiBuilder.addSubListButton(b,
                            "Entity Rules",
                            "Rule",
                            preset.entities,
                            p,
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

        addGenericList(builder, "§6§l[ Hierarchical Rules ]", "Rule", config.scaling_rules, parent,
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

    private static ConfigCategory buildCategory(ConfigServer.Rewards key, ConfigServer config, Screen parent) {
        var builder = ConfigCategory.createBuilder().name(Text.literal("Loot Scaling"));

                addGenericList(builder, "§6§l[ Weapons List ]", "Weapon Rule", config.loot_scaling.weapons, parent,
                        ConfigServer.ItemModifier::new,
                        ConfigServer.ItemModifier::getSummary,

                        (item) -> {
                            if (item.attributes.isEmpty()) return "§cNo attributes defined.";
                            // Preview the first attribute
                            var first = item.attributes.get(0);
                            return "§7" + first.getSummary() + (item.attributes.size() > 1 ? "..." : "");
                        },
                        (b, item, p) -> {
                            if (item.item_matches == null) item.item_matches = new ConfigServer.ItemModifier.Filters();

                            // ID Dropdown
                            b.option(Option.<String>createBuilder()
                                    .name(Text.literal("Target Item ID"))
                                    .description(OptionDescription.of(Text.literal("Leave empty to match by Regex.")))
                                    .binding("", () -> item.item_matches.id, v -> item.item_matches.id = v)
                                    .controller(opt -> DropdownStringControllerBuilder.create(opt).values(getRegistryIds(Registries.ITEM)))
                                    .build());

                            // Regex Field
                            b.option(Option.<String>createBuilder()
                                    .name(Text.literal("Loot Table Regex"))
                                    .binding("", () -> item.item_matches.loot_table_regex, v -> item.item_matches.loot_table_regex = v)
                                    .controller(StringControllerBuilder::create)
                                    .build());

                            // Attributes Sub-List (Recursive)
                            GuiBuilder.addSubListButton(b,
                                    "Attributes",
                                    "Attribute",
                                    item.attributes,
                                    p,
                                    () -> new ConfigServer.AttributeModifier("minecraft:generic.attack_damage", 1.0f),
                                    AttributeModifier::getSummary,
                                    (attr) -> "§7Operation: " + attr.operation + "\n§7Value: " + attr.value,
                                    GuiBuilder::injectEditor
                            );
                        }
                );

        // ARMOR LIST
        addGenericList(builder, "§6§l[ Armor List ]", "Armor Rule", config.loot_scaling.armor, parent,
                ConfigServer.ItemModifier::new,
                ConfigServer.ItemModifier::getSummary,

                (item) -> {
                    if (item.attributes.isEmpty()) return "§cNo attributes defined.";
                    // Preview the first attribute
                    var first = item.attributes.get(0);
                    return "§7" + first.getSummary() + (item.attributes.size() > 1 ? "..." : "");
                },
                (b, item, p) -> {
                    if (item.item_matches == null) item.item_matches = new ConfigServer.ItemModifier.Filters();

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

                    GuiBuilder.addSubListButton(b, "Attributes", "Attribute", item.attributes, p,
                            () -> new ConfigServer.AttributeModifier("minecraft:generic.armor", 1.0f),
                            (attr) -> attr.getSummary(),
                            (attr) -> "§7Operation: " + attr.operation + "\n§7Value: " + attr.value,
                            GuiBuilder::injectEditor
                    );
                }
        );

        return builder.build();
    }

    @FunctionalInterface
    interface EditorInjector<T> {
        void inject(OptionGroup.Builder builder, T item, Screen parent);
    }

    // -- HELPERS
    private static Option<Boolean> buildBool(String name, String desc, boolean def, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder().name(Text.literal(name)).description(OptionDescription.of(Text.literal(desc))).binding(def, getter, setter).controller(TickBoxControllerBuilder::create).build();
    }

    private static List<String> getRegistryIds(@Nullable net.minecraft.registry.Registry<?> registry) {
        if (registry == null) return List.of("");

        List<String> ids = new ArrayList<>(registry.getIds().stream()
                .map(Identifier::toString)
                .sorted()
                .toList());

        ids.add(0, "");
        return ids;
    }

    // --- MAIN ENTRY POINT
    public static Screen create(Screen parent) {
        var config = ConfigServer.fetch();


        return YetAnotherConfigLib.createBuilder()
                .title(Text.literal("Dungeon Difficulty"))
                .category(buildCategory(config.meta, config, parent))
                .category(buildCategory(config.announcement, config, parent))
                .category(buildCategory(config, config, parent))
                .category(buildCategory(config.loot_scaling, config, parent))
                .save(() -> AutoConfig.getConfigHolder(ConfigServer.class).save())
                .build()
                .generateScreen(parent);
    }
}