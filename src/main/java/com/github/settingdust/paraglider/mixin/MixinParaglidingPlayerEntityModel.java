package com.github.settingdust.paraglider.mixin;

import com.github.settingdust.paraglider.component.ParaglidingModelProcessor;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class MixinParaglidingPlayerEntityModel extends BipedEntityModel<PlayerEntity> {

    private MixinParaglidingPlayerEntityModel(ModelPart root) {
        super(root);
    }

    @Inject(
            method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",
            at = @At(shift = At.Shift.AFTER, value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/BipedEntityModel;setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V")
    )
    public void paraglider$setAngles(LivingEntity entity, float f, float g, float h, float i, float j, CallbackInfo ci) {
        ParaglidingModelProcessor.INSTANCE.setAngels$paraglider((PlayerEntityModel) (Object) this, (PlayerEntity) entity);
    }
}
