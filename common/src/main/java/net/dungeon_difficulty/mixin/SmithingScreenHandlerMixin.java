package net.dungeon_difficulty.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.dungeon_difficulty.DungeonDifficulty;
import net.dungeon_difficulty.logic.ItemScaling;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.SmithingRecipe;
import net.minecraft.registry.DynamicRegistryManager;
// import net.minecraft.recipe.input.RecipeInput;
// import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.inventory.Inventory;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.SmithingScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SmithingScreenHandler.class)
public class SmithingScreenHandlerMixin {

    @WrapOperation(
            method = "updateResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/recipe/SmithingRecipe;craft(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/registry/DynamicRegistryManager;)Lnet/minecraft/item/ItemStack;"
            )
    )
    private ItemStack updateResult_craft(SmithingRecipe instance, Inventory inventory, DynamicRegistryManager registryManager, Operation<ItemStack> original) {
        // removed baseItemStack and use input.base() for clarity
        var input = SmithingInput.from(inventory);
        var crafted = original.call(instance, inventory, registryManager);

        var config = DungeonDifficulty.config.value;
        var lootScaling = config.loot_scaling;
        if (lootScaling != null && lootScaling.smithing_upgrade.enabled
                && !input.template().isEmpty()
                && input.template().getRegistryEntry().getKey().get().getValue().toString().contains("upgrade") // Is upgrade?
                && ItemScaling.isScaled(input.base())) {
            var upgrade = lootScaling.smithing_upgrade;
            var level = ItemScaling.getScaleFactor(input.base());
            int newLevel = (int) ((level + upgrade.add_upon_upgrade) * upgrade.multiply_upon_upgrade);
            ItemScaling.rescale(crafted, newLevel);
        }
        return crafted;
    }
}

// Helper for identifying smithing inputs
record SmithingInput(ItemStack template, ItemStack base, ItemStack addition) {
    static SmithingInput from(Inventory inventory) {
        return new SmithingInput(
                inventory.getStack(0), // template
                inventory.getStack(1), // base
                inventory.getStack(2)  // addition
        );
    }
}
