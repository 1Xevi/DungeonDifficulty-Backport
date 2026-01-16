package net.dungeon_difficulty.logic;

import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.dungeon_difficulty.DungeonDifficulty;
import net.dungeon_difficulty.config.Config;
import net.dungeon_difficulty.util.Compat.CIdentifier;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.*;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

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
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            var function = new LocalScalingLootFunction(new LootCondition[0], id);
            tableBuilder.apply(function);
        });
    }

    public static void scale(ItemStack itemStack, ServerWorld world, BlockPos position, Identifier lootTableId) {
        if (isScaled(itemStack)) {
            return; // Avoid scaling items multiple times
        }
        var locationData = PatternMatching.LocationData.create(world, position);
        scale(itemStack, world, lootTableId, locationData);
    }

    public static void scale(ItemStack itemStack, ServerWorld world, Identifier lootTableId, PatternMatching.LocationData locationData) {
        var itemEntry = itemStack.getRegistryEntry();
        var itemId = Registries.ITEM.getId(itemStack.getItem()).toString();
        var rarity = itemStack.getRarity().toString();
        var dimensionId = world.getRegistryKey().getValue().toString(); // Just for logging
        var position = locationData.position();
        var scaling = DungeonDifficulty.config.value.loot_scaling;

        if (itemStack.getItem() instanceof ToolItem || itemStack.getItem() instanceof RangedWeaponItem) {
            var itemData = new PatternMatching.ItemData(PatternMatching.ItemKind.WEAPONS, lootTableId, itemEntry, rarity);
            debug("Item scaling start." + " dimension: " + dimensionId + " position: " + position + ", loot table: " + lootTableId + ", item: " + itemId + ", rarity: " + rarity);
            var result = PatternMatching.getModifiersForItem(locationData, itemData, world, scaling);
            debug("Pattern matching found " + result.modifiers().size() + " attribute modifiers");

            var hasHandModifiers = false;
            var nbt = itemStack.getNbt();
            if (nbt != null && itemStack.getNbt().contains("AttributeModifiers", 9)) {
                NbtList attributes = itemStack.getNbt().getList("AttributeModifiers", 10);
                for (int i = 0; i < attributes.size(); i++) {
                    if (attributes.getCompound(i).getString("Slot").equals(EquipmentSlot.MAINHAND.getName())) {
                        hasHandModifiers = true;
                        break;
                    }
                }
            }

            List<EquipmentSlot> targetSlots;
            if (hasHandModifiers) {
                targetSlots = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);
            } else {
                targetSlots = List.of(EquipmentSlot.MAINHAND);
            }
            applyModifiers(targetSlots, itemId, itemStack, result.modifiers(), result.level());
        }

        if (itemStack.getItem() instanceof ArmorItem armor) {
            var itemData = new PatternMatching.ItemData(PatternMatching.ItemKind.ARMOR, lootTableId, itemEntry, rarity);
            debug("Item scaling start." + " dimension: " + dimensionId + " position: " + position + ", loot table: " + lootTableId + ", item: " + itemId + ", rarity: " + rarity);
            var result = PatternMatching.getModifiersForItem(locationData, itemData, world, scaling);
            debug("Pattern matching found " + result.modifiers().size() + " attribute modifiers");
            applyModifiers(List.of(armor.getSlotType()), itemId, itemStack, result.modifiers(), result.level());
        }
        if (itemStack.getItem() instanceof ShieldItem shield) {
            var itemData = new PatternMatching.ItemData(PatternMatching.ItemKind.ARMOR, lootTableId, itemEntry, rarity);
            debug("Item scaling start." + " dimension: " + dimensionId + " position: " + position + ", loot table: " + lootTableId + ", item: " + itemId + ", rarity: " + rarity);
            var result = PatternMatching.getModifiersForItem(locationData, itemData, world, scaling);
            debug("Pattern matching found " + result.modifiers().size() + " attribute modifiers");
            applyModifiers(List.of(EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND), itemId, itemStack, result.modifiers(), result.level());
        }
    }

    public static void scale(ItemStack itemStack, int level) {
        var itemEntry = itemStack.getRegistryEntry();
        var itemId = Registries.ITEM.getId(itemStack.getItem()).toString();
        var rarity = itemStack.getRarity().toString();
        var lootTableId = CIdentifier.ofVanilla("none");
        var scaling = DungeonDifficulty.config.value.loot_scaling;

        if (itemStack.getItem() instanceof ToolItem || itemStack.getItem() instanceof RangedWeaponItem) {
            var itemData = new PatternMatching.ItemData(PatternMatching.ItemKind.WEAPONS, lootTableId, itemEntry, rarity);
            var result = PatternMatching.getItemScaleResult(itemData, scaling, level);

            var hasHandModifiers = false;
            var nbt = itemStack.getNbt();
            if (nbt != null && itemStack.getNbt().contains("AttributeModifiers", 9)) {
                var attributes = itemStack.getNbt().getList("AttributeModifiers", 10);
                for (int i = 0; i < attributes.size(); i++) {
                    if (attributes.getCompound(i).getString("Slot").equals(EquipmentSlot.MAINHAND.getName())) {
                        hasHandModifiers = true;
                        break;
                    }
                }
            }

            List<EquipmentSlot> targetSlots;
            if (hasHandModifiers) {
                targetSlots = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);
            } else {
                targetSlots = List.of(EquipmentSlot.MAINHAND);
            }

            applyModifiers(targetSlots, itemId, itemStack, result.modifiers(), result.level());
        }
        if (itemStack.getItem() instanceof ArmorItem armor) {
            var itemData = new PatternMatching.ItemData(PatternMatching.ItemKind.ARMOR, lootTableId, itemEntry, rarity);
            var result = PatternMatching.getItemScaleResult(itemData, scaling, level);
            applyModifiers(List.of(armor.getSlotType()), itemId, itemStack, result.modifiers(), result.level());
        }
        if (itemStack.getItem() instanceof ShieldItem shield) {
            var itemData = new PatternMatching.ItemData(PatternMatching.ItemKind.ARMOR, lootTableId, itemEntry, rarity);
            var result = PatternMatching.getItemScaleResult(itemData, scaling, level);
            applyModifiers(List.of(EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND), itemId, itemStack, result.modifiers(), result.level());
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
    }

    public record SlotSpecificItemAttributes(
            EquipmentSlot slot,
            Multimap<EntityAttribute, EntityAttributeModifier> attributes) { }

    private static void applyModifiers(List<EquipmentSlot> slots, String itemId, ItemStack itemStack, List<Config.AttributeModifier> modifiers, int level) {
        if (modifiers.isEmpty() || level == 0) {
            return;
        }

        copyAttributesToNBT(itemStack);

        Map<String, ModifierSummary> summary = new LinkedHashMap<>();

        for (var modifier : modifiers) {
            float value = modifier.randomizedValue(level);
            if (value == 0) continue;

            var element = summary.getOrDefault(modifier.attribute, new ModifierSummary(0, 0));

            if (modifier.operation == Config.Operation.ADDITION) {
                element = element.add(value);
            } else {
                element = element.multiplyBase(value);
            }
            summary.put(modifier.attribute, element);
        }

        var nbt = itemStack.getOrCreateNbt();
        var nbtModifiers = nbt.getList("AttributeModifiers", 10);
        for (EquipmentSlot slot : slots) {
            for (var entry : summary.entrySet()) {
                var totals = entry.getValue();
                if (totals.isEmpty()) continue;

                var attributeName = entry.getKey();

                if (attributeName.equalsIgnoreCase("damage") || attributeName.equalsIgnoreCase("power")) {
                    attributeName = "minecraft:generic.attack_damage";
                } else if (attributeName.equalsIgnoreCase("speed")) {
                    attributeName = "minecraft:generic.attack_speed";
                } else if (attributeName.equalsIgnoreCase("health")) {
                    attributeName = "minecraft:generic.max_health";
                } else if (attributeName.equalsIgnoreCase("armor")) {
                    attributeName = "minecraft:generic.armor";
                }

                var randomUuid = UUID.randomUUID();

                if (totals.add() != 0) {
                    double finalValue = totals.add();
                    Double roundingUnit = getRoundingUnit();
                    if (roundingUnit != null && roundingUnit != 0) {
                        finalValue = Math.round(finalValue / roundingUnit) * roundingUnit;
                    }

                    if (finalValue != 0) {
                        nbtModifiers.add(createRawModifierNbt(attributeName, "DD_Scaled_Add", finalValue, 0, slot, randomUuid));
                    }
                }

                if (totals.multiplyBase() != 0) {
                    nbtModifiers.add(createRawModifierNbt(attributeName, "DD_Scaled_Mult", totals.multiplyBase(), 1, slot, UUID.randomUUID()));
                }
            }
        }

        markAsScaled(itemStack, level);
    }

    public static void markAsScaled(ItemStack itemStack, int level) {
        itemStack.getOrCreateNbt().putInt(REWARD_SCALE_FACTOR, level);
    }

    public static boolean isScaled(ItemStack itemStack) {
        return itemStack.getNbt() != null && itemStack.getNbt().contains(REWARD_SCALE_FACTOR);
    }

    public static int getScaleFactor(ItemStack itemStack) {
        var nbt = itemStack.getNbt();
        if (isScaled(itemStack) && nbt != null) {
            return nbt.getInt(REWARD_SCALE_FACTOR);
        }
        return 0;
    }

    public static void removeScaling(ItemStack itemStack) {
        var nbt = itemStack.getNbt();
        if (nbt != null) {
            nbt.remove(REWARD_SCALE_FACTOR);
            // Also remove the "AttributeModifiers" list to reset it
            nbt.remove("AttributeModifiers");
        }
    }

    public static void rescale(ItemStack itemStack, int newLevel) {
        if (isScaled(itemStack)) {
            ItemScaling.removeScaling(itemStack);
        }
        if (newLevel > 0) {
            ItemScaling.scale(itemStack, newLevel);
        }
    }

    private static void copyAttributesToNBT(ItemStack itemStack) {
        var nbt = itemStack.getOrCreateNbt();
        if (!nbt.contains("AttributeModifiers", 9)) {
            var nbtModifiers = new NbtList();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                Multimap<EntityAttribute, EntityAttributeModifier> baseMap = itemStack.getAttributeModifiers(slot);

                for (Map.Entry<EntityAttribute, EntityAttributeModifier> entry : baseMap.entries()) {
                    var attribute = entry.getKey();
                    var mod = entry.getValue();

                    nbtModifiers.add(createRawModifierNbt(
                            Registries.ATTRIBUTE.getId(attribute).toString(),
                            mod.getName(),
                            mod.getValue(),
                            mod.getOperation().getId(),
                            slot,
                            mod.getId() // Keep the original vanilla UUID for base stats
                    ));
                }
            }

            if (!nbtModifiers.isEmpty()) {
                nbt.put("AttributeModifiers", nbtModifiers);
                itemStack.setNbt(nbt); // Save to stack
            }
        }
    }

    private static NbtCompound createRawModifierNbt(String attribute, String name, double value, int op, EquipmentSlot slot, UUID uuid) {
        NbtCompound compound = new NbtCompound();
        compound.putString("AttributeName", attribute);
        compound.putString("Name", name);
        compound.putDouble("Amount", value);
        compound.putInt("Operation", op);
        compound.putString("Slot", slot.getName());
        compound.putUuid("UUID", uuid);
        return compound;
    }

    private static EntityAttributeModifier createEntityAttributeModifier(EquipmentSlot slot, EntityAttribute attribute, String name, double value, EntityAttributeModifier.Operation operation) {
        UUID hardCodedUUID = null; // = hardCodedUUID(attribute);
        if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
            hardCodedUUID = hardCodedUUID(attribute);
        }
        if (hardCodedUUID != null) {
            return new EntityAttributeModifier(hardCodedUUID, name, value, operation);
        } else {
            return new EntityAttributeModifier(name, value, operation);
        }
    }

    private static UUID hardCodedUUID(EntityAttribute entityAttribute) {
        if (entityAttribute.equals(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
            return AttributeAccessor.hardCodedAttackDamageModifier();
        }
        if (entityAttribute.equals(EntityAttributes.GENERIC_ATTACK_SPEED)) {
            return AttributeAccessor.hardCodedAttackSpeedModifier();
        }
        return null;
    }

    public abstract static class AttributeAccessor extends Item {
        public AttributeAccessor(Settings settings) {
            super(settings);
        }

        public static UUID hardCodedAttackDamageModifier() { return ATTACK_DAMAGE_MODIFIER_ID; }
        public static UUID hardCodedAttackSpeedModifier() { return ATTACK_SPEED_MODIFIER_ID; }
    }

    private static Double getRoundingUnit() {
        var config = DungeonDifficulty.config.value;
        if (config.meta != null && config.meta.rounding_unit != null) {
            return config.meta.rounding_unit;
        }
        return null;
    }
}