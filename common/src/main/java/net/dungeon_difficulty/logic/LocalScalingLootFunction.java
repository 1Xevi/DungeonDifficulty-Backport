package net.dungeon_difficulty.logic;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.dungeon_difficulty.util.Compat.CIdentifier;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameter;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.function.ConditionalLootFunction;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Set;

public class LocalScalingLootFunction extends ConditionalLootFunction {
    public static final String NAME = "local_scaling";
    public static final Identifier ID = CIdentifier.of("dungeon_difficulty", NAME);
    public static final LootFunctionType TYPE = new LootFunctionType(new Serializer());

    public Identifier lootTableId;
    public LocalScalingLootFunction(LootCondition[] conditions, Identifier lootTableId) {
        super(conditions);
        this.lootTableId = lootTableId;
    }

    @Override
    public LootFunctionType getType() {
        return TYPE;
    }

    @Override
    public Set<LootContextParameter<?>> getRequiredParameters() {
        return Set.of();
    }

    @Override
    public ItemStack process(ItemStack itemStack, LootContext lootContext) {
        var position = lootContext.get(LootContextParameters.ORIGIN);
        BlockPos blockPosition = null;
        if (position != null) {
            blockPosition = BlockPos.ofFloored(position);
        }
        ItemScaling.scale(itemStack, lootContext.getWorld(), blockPosition, lootTableId);
        return itemStack;
    }

    public static class Serializer extends ConditionalLootFunction.Serializer<LocalScalingLootFunction> {
        @Override
        public void toJson(JsonObject json, LocalScalingLootFunction object, JsonSerializationContext context) {
            super.toJson(json, object, context);
            json.addProperty("loot_table_namespace", object.lootTableId.getNamespace());
            json.addProperty("loot_table_path", object.lootTableId.getPath());
        }

        @Override
        public LocalScalingLootFunction fromJson(JsonObject json, JsonDeserializationContext context, LootCondition[] conditions) {
            String namespace = JsonHelper.getString(json, "loot_table_namespace");
            String path = JsonHelper.getString(json, "loot_table_path");

            // FIX 1 (again): Use 'new Identifier' here too
            return new LocalScalingLootFunction(conditions, new Identifier(namespace, path));
        }
    }
}