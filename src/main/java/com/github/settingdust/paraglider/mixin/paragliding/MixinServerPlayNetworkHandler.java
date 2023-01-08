package com.github.settingdust.paraglider.mixin.paragliding;

import com.github.settingdust.paraglider.component.ParaglidingAccessor;
import com.github.settingdust.paraglider.component.ParaglidingComponentKt;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class MixinServerPlayNetworkHandler {
    @Shadow
    public ServerPlayerEntity player;

    @ModifyExpressionValue(
            method = "m_rpfiixll",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;hasVehicle()Z")
    )
    public boolean paraglider$avoidFloating(boolean original) {
        return original && ParaglidingAccessor.INSTANCE.getParagliding(player);
    }

    @Inject(
            method = "onUpdateSelectedSlot",
            at = @At(
                    value = "INVOKE_ASSIGN",
                    shift = At.Shift.BEFORE,
                    ordinal = 0,
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;getInventory()Lnet/minecraft/entity/player/PlayerInventory;"
            )
    )
    public void paraglider$resetParaglider(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        ParaglidingComponentKt.disableParaglidingAfterSwitchHeld(player, packet);
    }
}
