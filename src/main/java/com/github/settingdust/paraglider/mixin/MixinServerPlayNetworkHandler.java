package com.github.settingdust.paraglider.mixin;

import com.github.settingdust.paraglider.component.ParaglidingAccessor;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayNetworkHandler.class)
public class MixinServerPlayNetworkHandler {
    @Shadow public ServerPlayerEntity player;

    @ModifyExpressionValue(
            method = "m_rpfiixll",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;hasVehicle()Z")
    )
    public boolean paraglider$avoidFloating(boolean original) {
        return original && ParaglidingAccessor.INSTANCE.getParagliding(player);
    }
}
