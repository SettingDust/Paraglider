package com.github.settingdust.paraglider.component

import com.github.settingdust.paraglider.Paraglider
import com.github.settingdust.paraglider.component.ParaglidingAccessor.paragliding
import com.github.settingdust.paraglider.component.UpdraftAccessor.findUpdraft
import dev.onyxstudios.cca.api.v3.component.Component
import dev.onyxstudios.cca.api.v3.component.ComponentProvider
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent
import dev.onyxstudios.cca.api.v3.component.tick.CommonTickingComponent
import dev.onyxstudios.cca.api.v3.entity.PlayerComponent
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.render.entity.model.PlayerEntityModel
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.util.math.Direction
import org.quiltmc.loader.api.minecraft.ClientOnly
import org.quiltmc.qkl.library.nbt.set
import kotlin.math.max
import kotlin.math.min

object ParaglidingAccessor {
    var PlayerEntity.paragliding: Boolean
        get() = Paraglider.Components.PARAGLIDING[this].paragliding
        set(value) {
            Paraglider.Components.PARAGLIDING[this].paragliding = value
        }

    var ItemStack.paragliding: Boolean
        get() = orCreateNbt.getBoolean("paragliding")
        set(value) {
            val nbt = orCreateNbt
            if (value == nbt.getBoolean("paragliding")) return
            if (value) nbt["paragliding"] = true
            else nbt.remove("paragliding")
        }
}

data class ParaglidingComponent(
    private val player: PlayerEntity
) : PlayerComponent<Component>, AutoSyncedComponent, CommonTickingComponent {
    companion object {
        private val PARAGLIDER_TAG = Paraglider.Tags.PARAGLIDER

        init {
            StaminalConsumers[Paraglider.Identifiers.PARAGLIDING] = {
                if (Paraglider.config.paraglidingStamina() != 0 && it.paragliding) Paraglider.config.paraglidingStamina() else null
            }
        }
    }

    private var dirty = false
    private var accumulatedFallDistance = 0.0
    private var yCache = 0.0
    var paragliding = false
    private var exhaustedParagliding = 0

    init {
        UseItemCallback.EVENT.register { player, _, hand ->
            if (player.paragliding) TypedActionResult.fail(player.getStackInHand(hand))
            else TypedActionResult.pass(player.getStackInHand(hand))
        }
    }

    override fun readFromNbt(tag: NbtCompound) {
        paragliding = tag.getBoolean("paragliding")
    }

    override fun writeToNbt(tag: NbtCompound) {
        tag["paragliding"] = paragliding
    }

    override fun tick() {
        if (player.isOnGround || player.y > yCache) accumulatedFallDistance = 0.0
        else accumulatedFallDistance += yCache - player.y

        val updraftHeight by lazy { Paraglider.config.updraftHeight() }
        val updraft by lazy { player.findUpdraft()?.let { (updraftHeight - it) / updraftHeight } }
        val ascending by lazy { updraft != null }

        val hand by lazy { if (player.mainHandStack.isIn(PARAGLIDER_TAG)) Hand.MAIN_HAND else Hand.OFF_HAND }
        val paraglider by lazy { if (hand == Hand.MAIN_HAND) player.mainHandStack else player.offHandStack }
        val heldParaglider by lazy { player.isHolding { it.isIn(PARAGLIDER_TAG) } }
        val onlyHeldParaglider by lazy {
            heldParaglider && (player.mainHandStack.isEmpty || player.offHandStack.isEmpty)
        }

        val exhausted by lazy { player.hasStatusEffect(Paraglider.StatusEffects.EXHAUSTED) }
        if (exhausted && exhaustedParagliding <= 0) exhaustedParagliding = 40
        if (exhaustedParagliding > 0) exhaustedParagliding--
        val shouldNotParagliding = player.isOnGround ||
            player.hasVehicle() ||
            player.isSwimming ||
            (paraglider.maxDamage != 0 && paraglider.damage >= paraglider.maxDamage) ||
            player.isTouchingWater ||
            (player.isSprinting && !player.isUsingItem) ||
            player.abilities.flying

        if (paragliding &&
            (
                !onlyHeldParaglider ||
                    (exhausted && exhaustedParagliding <= 0) ||
                    shouldNotParagliding
                )
        ) {
            exhaustedParagliding = 0
            paragliding = false
            dirty = true
        } else if (
            !paragliding &&
            !exhausted &&
            onlyHeldParaglider &&
            !shouldNotParagliding &&
            (
                (Paraglider.config.updraftEnable() && ascending) ||
                    (
                        !player.isOnGround &&
                            !player.isFallFlying &&
                            accumulatedFallDistance > 1.45
                        )
                )
        ) {
            paragliding = true
            dirty = true
        }

        if (paragliding) {
            player.fallDistance = 0F
            val velocity = player.velocity
            player.flyingSpeed = Paraglider.config.paraglidingSpeed()
            if (ascending && Paraglider.config.updraftEnable() && (velocity.y < (0.4 * updraft!!))) {
                player.velocity = velocity.withAxis(Direction.Axis.Y, 0.4 * updraft!!)
            } else if (exhaustedParagliding > 0) {
                player.velocity = velocity.withAxis(Direction.Axis.Y, max(velocity.y - 0.05, -0.15))
            } else if (velocity.y < -0.05) {
                player.velocity = velocity.withAxis(Direction.Axis.Y, min(velocity.y + 0.08, -0.05))
            }
        }

        yCache = player.y
        if (heldParaglider) {
            paraglider.paragliding = paragliding
            if (paragliding && paraglider.damage < paraglider.maxDamage) {
                var broken = false
                paraglider.damage(1, player) {
                    it.sendToolBreakStatus(hand)
                    paraglider.count = 2
                    broken = true
                }
                if (broken) {
                    paraglider.count = 1
                    paraglider.damage = paraglider.maxDamage
                }
            }
        }
    }

    override fun serverTick() {
        super.serverTick()
        if (dirty) {
            Paraglider.Components.PARAGLIDING.syncWith(player as ServerPlayerEntity, player as ComponentProvider)
        }
    }
}

internal fun ServerPlayerEntity.disableParaglidingAfterSwitchHeld(packet: UpdateSelectedSlotC2SPacket) {
    if (packet.selectedSlot != inventory.selectedSlot) {
        val old = inventory.getStack(inventory.selectedSlot)
        if (old.paragliding) old.paragliding = false
    }
}

@ClientOnly
private const val ARM_ROTATION = (Math.PI * 2 - 2.9).toFloat()

@ClientOnly
internal fun PlayerEntityModel<PlayerEntity>.setAngels(entity: PlayerEntity) {
    if (entity.paragliding) {
        leftArm.pitch = ARM_ROTATION
        leftArm.yaw = 0f
        rightArm.pitch = ARM_ROTATION
        rightArm.yaw = 0f
        leftLeg.pitch = 0f
        rightLeg.pitch = 0f
    }
}

@ClientOnly
internal fun areSameParaglider(old: ItemStack, new: ItemStack) =
    old.isItemEqualIgnoreDamage(new) && old.paragliding && new.paragliding
