package com.github.settingdust.paraglider.mixin;

import com.github.settingdust.paraglider.component.ParaglidingComponentKt;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HeldItemRenderer.class)
public class MixinHeldItemRenderer {
    @Shadow
    private ItemStack mainHand;
    @Shadow
    private ItemStack offHand;
    @Shadow
    @Final
    private MinecraftClient client;

    @ModifyExpressionValue(
            method = "updateHeldItems",
            at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/item/ItemStack;areEqual(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z")
    )
    public boolean paraglider$areMainHandEqual(boolean value) {
        return value || ParaglidingComponentKt.areSameParaglider(mainHand, client.player.getMainHandStack());
    }

    @ModifyExpressionValue(
            method = "updateHeldItems",
            at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/item/ItemStack;areEqual(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z")
    )
    public boolean paraglider$areOffHandEqual(boolean value) {
        return value || ParaglidingComponentKt.areSameParaglider(offHand, client.player.getOffHandStack());
    }
}
