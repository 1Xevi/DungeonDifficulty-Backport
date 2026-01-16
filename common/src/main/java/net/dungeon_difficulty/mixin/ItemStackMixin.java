package net.dungeon_difficulty.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.dungeon_difficulty.DungeonDifficulty;
import net.dungeon_difficulty.logic.RarityHelper;
import net.minecraft.client.item.TooltipContext;
// import net.minecraft.component.ComponentType;
// import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.dungeon_difficulty.logic.ItemScaling;
// import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ItemStack.class)
public class ItemStackMixin {
    private ItemStack itemStack() {
        return (ItemStack) (Object) this;
    }

    @Inject(method = "getRarity", at = @At("RETURN"), cancellable = true)
    private void injected(CallbackInfoReturnable<Rarity> cir) {
        var itemStack = itemStack();
        Rarity rarity = cir.getReturnValue();
        if (DungeonDifficulty.clientConfig.value.enable_overriding_enchantment_rarity && itemStack.hasEnchantments()) {
            rarity = RarityHelper.increasedRarity(rarity, 1);
        }
        if (DungeonDifficulty.clientConfig.value.enable_scaled_items_rarity
                && ItemScaling.isScaled(itemStack)) {
            rarity = RarityHelper.increasedRarity(rarity, 1);
        }

        if (rarity != cir.getReturnValue()) {
            cir.setReturnValue(rarity);
            cir.cancel();
        }
    }

    @Inject(method = "getTooltip", at = @At("RETURN"))
    private void injected(@Nullable PlayerEntity player, TooltipContext context, CallbackInfoReturnable<List<Text>> cir) {
        int level = ItemScaling.getScaleFactor(itemStack());
        if (level > 0) {
            List<Text> lines = cir.getReturnValue();
            lines.add(Text.translatable("item.power.level", level).formatted(Formatting.BLUE));
        }
    }
}
