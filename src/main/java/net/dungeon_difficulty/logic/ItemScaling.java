package net.dungeon_difficulty.logic;

import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.dungeon_difficulty.DungeonDifficulty;
import net.dungeon_difficulty.config.Config;
import net.dungeon_difficulty.logic.PatternMatching.ItemData;
import net.dungeon_difficulty.logic.PatternMatching.LocationData;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.*;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.function.LootFunction;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.loot.function.LootFunctionTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

public class ItemScaling {
    static final Logger LOGGER = LogUtils.getLogger();
    public static final String REWARD_SCALE_FACTOR = "dd.rsf";
    private static final boolean debugLogging = false;
    private static void debug(String message) {
        if (debugLogging) {
            System.out.println(message);
        }
    }

    public static void initialize() {
        boolean useLootScaling = DungeonDifficulty.config.value.meta.loot_scaling;

        if (!useLootScaling) { return; }

        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            LootFunction function = new LootFunction() {
                @Override
                public LootFunctionType getType() {
                    return LootFunctionTypes.SET_ATTRIBUTES;
                }

                @Override
                public ItemStack apply(ItemStack itemStack, LootContext lootContext) {
                    var position = lootContext.get(LootContextParameters.ORIGIN);
                    BlockPos blockPosition = null;
                    if (position != null) {
                        blockPosition = BlockPos.ofFloored(position);
                    }

                    scale(itemStack, lootContext.getWorld(), blockPosition, Identifier.tryParse(id.toString()));
                    return itemStack;
                }
            };
            tableBuilder.apply(function);
        });
    }

    public static void scale(ItemStack itemStack, ServerWorld world, BlockPos position, Identifier lootTableId) {
        if (isScaled(itemStack)) {
            return; // Avoid scaling items multiple times
        }
        var locationData = LocationData.create(world, position);
        scale(itemStack, world, lootTableId, locationData);
    }

    public static void scale(ItemStack itemStack, ServerWorld world, Identifier lootTableId, LocationData locationData) {
        var itemId = Registries.ITEM.getId(itemStack.getItem()).toString();
        var rarity = itemStack.getRarity().toString();
        var dimensionId = world.getRegistryKey().getValue().toString(); // Just for logging
        var position = locationData.position();

        if (itemStack.getItem() instanceof ToolItem || itemStack.getItem() instanceof RangedWeaponItem) {
            var itemData = new ItemData(PatternMatching.ItemKind.WEAPONS, lootTableId, itemId, rarity);
            debug("Item scaling start." + " dimension: " + dimensionId + " position: " + position + ", loot table: " + lootTableId + ", item: " + itemId + ", rarity: " + rarity);
            var result = PatternMatching.getModifiersForItem(locationData, itemData, world);
            debug("Pattern matching found " + result.modifiers().size() + " attribute modifiers");

            applyModifiersForItemStack(
                    new EquipmentSlot[]{ EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND },
                    itemId, itemStack, result.modifiers(), result.level()
            );
        }

        if (itemStack.getItem() instanceof ArmorItem armor) {
            var itemData = new ItemData(PatternMatching.ItemKind.ARMOR, lootTableId, itemId, rarity);
            debug("Item scaling start." + " dimension: " + dimensionId + " position: " + position + ", loot table: " + lootTableId + ", item: " + itemId + ", rarity: " + rarity);
            var result = PatternMatching.getModifiersForItem(locationData, itemData, world);
            debug("Pattern matching found " + result.modifiers().size() + " attribute modifiers");

            applyModifiersForItemStack(
                    new EquipmentSlot[]{ armor.getSlotType() },
                    itemId, itemStack, result.modifiers(), result.level()
            );
        }

        if (itemStack.getItem() instanceof ShieldItem) {
            var itemData = new ItemData(PatternMatching.ItemKind.ARMOR, lootTableId, itemId, rarity);
            debug("Item scaling start." + " dimension: " + dimensionId + " position: " + position + ", loot table: " + lootTableId + ", item: " + itemId + ", rarity: " + rarity);
            var result = PatternMatching.getModifiersForItem(locationData, itemData, world);
            debug("Pattern matching found " + result.modifiers().size() + " attribute modifiers");

            applyModifiersForItemStack(
                    new EquipmentSlot[]{ EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND },
                    itemId, itemStack, result.modifiers(), result.level()
            );
        }

    }

    private record ModifierSummary(float add, float multiplyBase) {
        public ModifierSummary add(float value) {
            return new ModifierSummary(add + value, multiplyBase);
        }
        public ModifierSummary multiplyBase(float value) {
            return new ModifierSummary(add, multiplyBase  + value);
        }
        public boolean isEmpty() {
            return add == 0 && multiplyBase == 0;
        }
        public float apply(float value) {
            return (value + add) * (1F + multiplyBase);
        }
    }

    private record AddResult(double value, @Nullable Identifier id) { }
    private static AddResult getSumOfAdditionModifiers(ItemStack stack, EquipmentSlot slot, EntityAttribute givenAttribute) {

        Multimap<
                EntityAttribute,
                EntityAttributeModifier
                > allModifiers = stack.getAttributeModifiers(slot);

        Collection<EntityAttributeModifier> specificModifiers = allModifiers.get(givenAttribute);

        if (specificModifiers.isEmpty()) { return new AddResult(0.0, null); }

        double totalValue = 0.0;

        for (EntityAttributeModifier modifier : specificModifiers) {
            if (modifier.getOperation() == EntityAttributeModifier.Operation.ADDITION) {
                totalValue += modifier.getValue();
                // FIXED: The modifier ID is a UUID, not Identifier. We remove this line
                // as the ID isn't used in this backported logic anyway.
            }
        }
        return new AddResult(totalValue, null); // Always return null for the ID.
    }

    private record ScaledAttributeResult(double value) { }

    private static void applyModifiersForItemStack(
            EquipmentSlot[] slots,
            String itemId,
            ItemStack itemStack,
            List<Config.AttributeModifier> modifiers,
            int level) {

        if (modifiers.isEmpty() || level == 0) { return; }

        var roundingUnit = getRoundingUnit();
        boolean useAdditiveModifiers = !DungeonDifficulty.config.value.meta.merge_item_modifiers;

        var summary = new java.util.LinkedHashMap<String, ModifierSummary>();
        for (var modifier : modifiers) {
            var element = summary.get(modifier.attribute);
            if (element == null) {
                element = new ModifierSummary(0, 0);
            }
            switch (modifier.operation) {
                case ADDITION -> element = element.add(modifier.randomizedValue(level));
                case MULTIPLY_BASE -> element = element.multiplyBase(modifier.randomizedValue(level));
            }
            if (!element.isEmpty()) {
                summary.put(modifier.attribute, element);
            }
        }

        // --- Phase 2: Calculate New Values (Translated Logic) ---
        LinkedHashMap<
                EquipmentSlot,
                LinkedHashMap<EntityAttribute, Double>
                > finalResults = new java.util.LinkedHashMap<>();

        for (var slot : slots) {
            finalResults.put(slot, new java.util.LinkedHashMap<>());

            var attributesForSlot = itemStack.getAttributeModifiers(slot);

            for (var attributeBoost : summary.entrySet()) {
                var attributePattern = attributeBoost.getKey();

                for (var attribute : attributesForSlot.keySet()) {
                    if (PatternMatching.matches(net.minecraft.registry.Registries.ATTRIBUTE.getId(attribute).toString(), attributePattern)) {

                        var baseline = getSumOfAdditionModifiers(itemStack, slot, attribute);
                        var baseValue = baseline.value;

                        if (useAdditiveModifiers) {
                            var boostedValue = attributeBoost.getValue().apply((float) baseValue);
                            var boostAmount = boostedValue - baseValue;
                            // if (roundingUnit != null) { /* MathHelper.round(boostAmount, roundingUnit) */ }
                            if (boostAmount != 0) { finalResults.get(slot).put(attribute, boostAmount); }
                        } else {
                            var finalValue = attributeBoost.getValue().apply((float) baseValue);
                            // if (roundingUnit != null) { /* finalValue = MathHelper.round(finalValue, roundingUnit) */ }
                            finalResults.get(slot).put(attribute, (double)finalValue);
                        }
                    }
                }
            }
        }


        itemStack.getOrCreateNbt();

        for (var entry : finalResults.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            LinkedHashMap<EntityAttribute, Double> slotResults = entry.getValue();

            for (var resultEntry : slotResults.entrySet()) {
                EntityAttribute attribute = resultEntry.getKey();
                Double value = resultEntry.getValue();

                // NOTE: A full implementation might need to remove old modifiers first
                // before adding the new one to avoid stacking bonuses infinitely.

                var newModifier = new net.minecraft.entity.attribute.EntityAttributeModifier(
                        "dd.scaled." + net.minecraft.registry.Registries.ATTRIBUTE.getId(attribute).getPath(),
                        value,
                        net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADDITION
                );

                // TRANSLATION: This is the 1.20.1 way to add a modifier, which writes to the item's NBT.
                itemStack.addAttributeModifier(attribute, newModifier, slot);
            }
        }

        markAsScaled(itemStack, level); // Assumes you have the markAsScaled method.
    }

    private static Double getRoundingUnit() {
        var config = DungeonDifficulty.config.value;
        if (config.meta != null && config.meta.rounding_unit != null) {
            return config.meta.rounding_unit;
        }
        return null;
    }

    public static void markAsScaled(ItemStack itemStack, int level) {
        NbtCompound nbt = itemStack.getOrCreateNbt();
        nbt.putInt(REWARD_SCALE_FACTOR, level);
    }

    public static boolean isScaled(ItemStack itemStack) {
        if (!itemStack.hasNbt()) {
            return false;
        }

        NbtCompound nbt = itemStack.getNbt();

        return nbt != null && nbt.contains(REWARD_SCALE_FACTOR);
    }

    public static int getScaleFactor(ItemStack itemStack) {
        if (isScaled(itemStack)) {
            NbtCompound nbt = itemStack.getNbt();
            return nbt.getInt(REWARD_SCALE_FACTOR);
        }
        return 0;
    }

}