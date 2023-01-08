package com.github.settingdust.paraglider.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Item.class)
public abstract class MixinItem {

    @ModifyExpressionValue(
            method = "getItemBarStep",
            at = @At(value = "FIELD", target = "Lnet/minecraft/item/Item;maxDamage:I")
    )
    public int paraglider$getItemBarStep(int maxDamage) {
        return getMaxDamage();
    }

    @ModifyExpressionValue(
            method = "getItemBarColor",
            at = @At(value = "FIELD", target = "Lnet/minecraft/item/Item;maxDamage:I")
    )
    public int paraglider$getItemBarColor(int maxDamage) {
        return getMaxDamage();
    }

    @Shadow
    public abstract int getMaxDamage();
}
