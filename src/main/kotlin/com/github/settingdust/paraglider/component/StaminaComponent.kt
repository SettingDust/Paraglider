package com.github.settingdust.paraglider.component

import com.github.settingdust.paraglider.Paraglider
import com.github.settingdust.paraglider.component.StaminalAccessors.maxStamina
import com.github.settingdust.paraglider.component.StaminalConsumers.staminaDelta
import dev.onyxstudios.cca.api.v3.component.Component
import dev.onyxstudios.cca.api.v3.component.ComponentProvider
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent
import dev.onyxstudios.cca.api.v3.component.tick.CommonTickingComponent
import dev.onyxstudios.cca.api.v3.entity.PlayerComponent
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import org.quiltmc.qkl.library.nbt.set
import kotlin.math.min

object StaminalConsumers : MutableList<Pair<Identifier, (PlayerEntity) -> Int?>> by mutableListOf() {
    operator fun set(
        identifier: Identifier,
        consumer: (PlayerEntity) -> Int?
    ) {
        this += identifier to consumer
    }

    init {
        this[Paraglider.identifier("flying")!!] =
            { if (it.abilities.flying) Paraglider.config.flyingStamina() else null }
        this[Paraglider.identifier("vehicle")!!] = { if (it.hasVehicle()) Paraglider.config.vehicleStamina() else null }
        this[Paraglider.identifier("swimming")!!] = { if (it.isSwimming) Paraglider.config.swimmingStamina() else null }
        this[Paraglider.identifier("underwater")!!] =
            {
                if (it.isSubmergedInWater) {
                    if (it.breathable()) Paraglider.config.breathableUnderwaterStamina() else Paraglider.config.underwaterStamina()
                } else null
            }
        this[Paraglider.identifier("sprinting")!!] =
            { if (it.isSprinting) Paraglider.config.spiritingStamina() else null }
        this[Paraglider.identifier("ground")!!] = { if (it.isOnGround) Paraglider.config.groundStamina() else null }
    }

    internal fun PlayerEntity.staminaDelta(): Int {
        var result = 0 // Will be 0 when floating in air
        any { (_, consumer) ->
            val value = consumer(this)
            if (value != null) {
                result = value
                true
            } else false
        }
        return result
    }

    private fun PlayerEntity.breathable() = canBreatheInWater() ||
        hasStatusEffect(net.minecraft.entity.effect.StatusEffects.WATER_BREATHING) ||
        getEquippedStack(EquipmentSlot.FEET).takeIf { !it.isEmpty }
            ?.let { EnchantmentHelper.getLevel(Enchantments.DEPTH_STRIDER, it) > 0 } ?: false
}

data class StaminaComponent(
    private val player: PlayerEntity
) : PlayerComponent<Component>, AutoSyncedComponent, CommonTickingComponent {
    var stamina = 0
    var depleted = false
    private var regenCooldown = 10

    private var dirty = false
    override fun readFromNbt(tag: NbtCompound) {
        stamina = tag.getInt("stamina")
        depleted = tag.getBoolean("depleted")
        regenCooldown = tag.getInt("regenCooldown")
    }

    override fun writeToNbt(tag: NbtCompound) {
        tag["stamina"] = stamina
        tag["depleted"] = depleted
        tag["regenCooldown"] = regenCooldown
    }

    override fun shouldSyncWith(player: ServerPlayerEntity) = player == this.player

    private fun tickStamina() {
        val delta = player.staminaDelta()
        if (delta < 0) {
            regenCooldown = 10
            if (!depleted) stamina = (stamina + delta).coerceAtLeast(0)
        } else if (regenCooldown > 0) regenCooldown--
        else if (delta > 0) stamina = (stamina + delta).coerceAtMost(player.maxStamina)
    }

    private fun tickDepleted() {
        if (depleted) {
            if (stamina >= player.maxStamina) {
                depleted = false
                dirty = true
            }
        } else if (stamina <= 0) {
            depleted = true
            dirty = true
        }
    }

    override fun tick() {
        if (!Paraglider.config.staminaEnable()) return
        tickStamina()
        tickDepleted()
        if (!player.isCreative && depleted) {
            player.addStatusEffect(StatusEffectInstance(Paraglider.StatusEffects.EXHAUSTED, 2, 0, false, false, false))
        }
    }

    override fun serverTick() {
        if (!Paraglider.config.staminaEnable()) return
        super.serverTick()

        if (dirty) {
            Paraglider.Components.STAMINA.syncWith(player as ServerPlayerEntity, player as ComponentProvider)
            dirty = false
        }
    }
}

object StaminalAccessors {
    var PlayerEntity.stamina: Int
        get() = Paraglider.Components.STAMINA[this].stamina
        set(value) {
            Paraglider.Components.STAMINA[this].stamina = value
        }
    val PlayerEntity.maxStamina: Int
        get() = getAttributeInstance(Paraglider.Attributes.MAX_STAMINA)?.value?.toInt() ?: Paraglider.config.maxStamina()

    var PlayerEntity.depleted: Boolean
        get() = Paraglider.Components.STAMINA[this].depleted
        set(value) {
            Paraglider.Components.STAMINA[this].depleted = value
        }

    fun PlayerEntity.giveStamina(amount: Int, simulate: Boolean = false): Int {
        if (amount <= 0) return 0
        val maxStamina = maxStamina
        val finalAmount = min(amount, maxStamina - stamina)
        if (finalAmount <= 0) return 0
        if (!simulate) stamina += finalAmount
        return finalAmount
    }

    fun PlayerEntity.takeStamina(amount: Int, ignoreDepletion: Boolean, simulate: Boolean = false): Int {
        if (amount <= 0 || (depleted && ignoreDepletion)) return 0
        val finalAmount = min(amount, stamina)
        if (finalAmount <= 0) return 0
        if (!simulate) stamina -= finalAmount
        return finalAmount
    }
}
