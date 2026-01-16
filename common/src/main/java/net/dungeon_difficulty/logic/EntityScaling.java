package net.dungeon_difficulty.logic;

import net.dungeon_difficulty.DungeonDifficulty;
import net.dungeon_difficulty.mixin.AccessorAttributeContainer;
import net.dungeon_difficulty.mixin.AccessorDefaultAttributeContainer;
import net.dungeon_difficulty.util.Compat.CIdentifier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EntityScaling {
    public static void scale(Entity entity, ServerWorld world) {
        if (entity instanceof PlayerEntity) {
            return;
        }
        if (entity instanceof LivingEntity livingEntity) {
            var scalableEntity = ((EntityDifficultyScalable)livingEntity);
            if (scalableEntity.isAlreadyScaled()) {
                return;
            }
            var locationData = PatternMatching.LocationData.create(world, livingEntity.getBlockPos());
            var entityData = PatternMatching.EntityData.create(livingEntity);
            scalableEntity.setScalingLocationData(locationData);

            var relativeHealth = livingEntity.getHealth() / livingEntity.getMaxHealth();

            EntityScaling.apply(PerPlayerDifficulty.getAttributeModifiers(entityData, world), livingEntity);
            var locationScaling = PatternMatching.getAttributeModifiersForEntity(locationData, entityData, world);
            EntityScaling.apply(locationScaling, livingEntity);

//            if (DungeonDifficulty.config.value.meta.entity_equipment_scaling) {
//                for (var itemStack : livingEntity.getItemsEquipped()) {
//                    ItemScaling.scale(itemStack, world, entityData.entityId(), locationData);
//                }
//            }

            // Store location-based level (ignore per-player scaling)
            scalableEntity.markAlreadyScaled(locationScaling.level());
            livingEntity.setHealth(relativeHealth * livingEntity.getMaxHealth());
        }
    }

    private static void apply(PatternMatching.EntityScaleResult scaling, LivingEntity entity) {
        var level = scaling.level();
        if (level <= 0) { return; }
        for (var modifier : scaling.modifiers()) {
            var pattern = modifier.attribute;
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }

            ArrayList<EntityAttribute> matchingAttributes = new ArrayList<>();
            if (pattern.startsWith(PatternMatching.REGEX_PREFIX)) {
                var regex = pattern.substring(PatternMatching.REGEX_PREFIX.length());
                var instances = ((AccessorDefaultAttributeContainer)
                            (AccessorAttributeContainer)entity.getAttributes())
                        .getInstances();
                for (var entry : instances.entrySet()) {
                    var attribute = entry.getKey().value();

                    if (attribute == null) { continue; }
                    var id = attribute.toString();

                    if (PatternMatching.regexMatches(id, regex)) {
                        matchingAttributes.add(attribute);
                    }
                }
            } else {
                var attribute = Registries.ATTRIBUTE.get(CIdentifier.of(modifier.attribute));
                if (attribute == null || !entity.getAttributes().hasAttribute(attribute)) { continue; }
                matchingAttributes.add(attribute);
            }

            var modifierValue = modifier.randomizedValue(level);
            var roundingUnit = modifier.value * 0.25F;
            modifierValue = (float) MathHelper.round(modifierValue, roundingUnit);

            var id = CIdentifier.of(DungeonDifficulty.MODID, scaling.name());

            for (var attribute: matchingAttributes) {
                var operation = switch (modifier.operation) {
                    case ADDITION -> EntityAttributeModifier.Operation.ADDITION;
                    case MULTIPLY_BASE -> EntityAttributeModifier.Operation.MULTIPLY_BASE;
                };

                var modifierUuid = UUID.nameUUIDFromBytes(id.toString().getBytes());
                var entityModifier = new EntityAttributeModifier(modifierUuid, id.toString(), modifierValue, operation);
                var instance = entity.getAttributeInstance(attribute);

                if (instance != null && !instance.hasModifier(entityModifier)) {
                    instance.addPersistentModifier(entityModifier);
                }
            }
        }
    }
}
