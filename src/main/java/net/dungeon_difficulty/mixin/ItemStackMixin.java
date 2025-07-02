package net.dungeon_difficulty.mixin;

import net.dungeon_difficulty.DungeonDifficulty;
import net.dungeon_difficulty.logic.ItemScaling;
import net.dungeon_difficulty.logic.RarityHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Rarity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackMixin {
    @Unique
    private ItemStack itemStack() {
        return (ItemStack) (Object) this;
    }

    @Inject(method = "getRarity", at = @At("RETURN"), cancellable = true)
    private void injected(CallbackInfoReturnable<Rarity> cir) {
        ItemStack stack = this.itemStack(); // Assuming this helper exists from your previous code

        Rarity rarity = stack.getItem().getRarity(stack);
        if (DungeonDifficulty.clientConfig.value.enable_overriding_enchantment_rarity
                && stack.hasEnchantments()) {
            rarity = RarityHelper.increasedRarity(rarity, 1);
        }
        if (DungeonDifficulty.clientConfig.value.enable_scaled_items_rarity
                && ItemScaling.isScaled(stack)) {
            rarity = RarityHelper.increasedRarity(rarity, 1);
        }

        if (rarity != cir.getReturnValue()) {
            cir.setReturnValue(rarity);
        }
    }
}
