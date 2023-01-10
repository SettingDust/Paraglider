package com.github.settingdust.paraglider.component

import com.github.settingdust.paraglider.Paraglider
import com.github.settingdust.paraglider.component.StaminalAccessors.maxStamina
import com.github.settingdust.paraglider.component.StaminalAccessors.stamina
import com.github.settingdust.paraglider.component.StaminalConsumers.staminaDelta
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.Tessellator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexFormats
import dev.onyxstudios.cca.api.v3.component.Component
import dev.onyxstudios.cca.api.v3.component.ComponentProvider
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent
import dev.onyxstudios.cca.api.v3.component.tick.CommonTickingComponent
import dev.onyxstudios.cca.api.v3.entity.PlayerComponent
import io.wispforest.owo.ui.component.TextureComponent
import io.wispforest.owo.ui.core.Positioning
import io.wispforest.owo.ui.core.Sizing
import io.wispforest.owo.ui.hud.Hud
import io.wispforest.owo.ui.hud.HudContainer
import io.wispforest.owo.ui.util.Drawer
import net.minecraft.client.render.GameRenderer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import org.quiltmc.loader.api.minecraft.ClientOnly
import org.quiltmc.qkl.library.nbt.set
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.tan

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
        if (delta <= 0) {
            regenCooldown = 10
            if (delta < 0 && !depleted) stamina = (stamina + delta).coerceAtLeast(0)
        }
        else if (regenCooldown > 0) regenCooldown--
        else {
            stamina = (stamina + delta).coerceAtMost(player.maxStamina)
        }
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

    override fun clientTick() {
        super.clientTick()

        if (!Paraglider.config.staminaEnable() && Hud.hasComponent(Paraglider.identifier("stamina")))
            Hud.remove(Paraglider.identifier("stamina"))
        else if (Paraglider.config.staminaEnable() && !Hud.hasComponent(Paraglider.identifier("stamina")))
            Hud.add(Paraglider.identifier("stamina")) { StaminaHud(player) }
    }
}

object StaminalAccessors {
    var PlayerEntity.stamina: Int
        get() = Paraglider.Components.STAMINA[this].stamina
        set(value) {
            Paraglider.Components.STAMINA[this].stamina = value
        }
    val PlayerEntity.maxStamina: Int
        get() = getAttributeInstance(Paraglider.Attributes.MAX_STAMINA)?.value?.toInt()
            ?: Paraglider.config.maxStamina()

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

@ClientOnly
class StaminaHud(player: PlayerEntity) : HudContainer(Sizing.fill(100), Sizing.fill(100)) {
    init {
        positioning(Positioning.absolute(24, 0))
        child(RingComponent(player).apply {
            positioning(Positioning.relative(50, 50))
        })
    }
}

@ClientOnly
class RingComponent(private val player: PlayerEntity) :
    TextureComponent(RING_TEXTURE, 0, 0, RING_SIZE, RING_SIZE, TEXTURE_SIZE, TEXTURE_SIZE) {
    companion object {
        private val RING_TEXTURE = Paraglider.identifier("textures/stamina_ring.png")!!
        private const val TEXTURE_SIZE = 128
        private const val RING_SIZE = 28
        private const val RING_RADIUS = (RING_SIZE / 2).toFloat()
        private const val UV = RING_SIZE / TEXTURE_SIZE.toFloat()
        private const val HALF_UV = UV / 2
    }

    init {
        sizing(Sizing.fixed(RING_SIZE), Sizing.fixed(RING_SIZE))
        applySizing()
    }

    override fun draw(matrices: MatrixStack, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
        RenderSystem.setShaderTexture(0, texture)
        RenderSystem.disableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()

        matrices.push()
        matrices.translate(x.toDouble(), y.toDouble(), 0.0)
        matrices.scale(width / regionWidth.toFloat(), height / regionHeight.toFloat(), 0f)

        val visibleArea = visibleArea.get()

        val bottomEdge = (visibleArea.y() + visibleArea.height()).coerceAtMost(regionHeight)
        val rightEdge = (visibleArea.x() + visibleArea.width()).coerceAtMost(regionWidth)

        RenderSystem.setShaderColor(0F, 0F, 0F, 0.5F)

        Drawer.drawTexture(
            matrices,
            visibleArea.x(),
            visibleArea.y(),
            rightEdge - visibleArea.x(),
            bottomEdge - visibleArea.y(),
            (u + visibleArea.x()).toFloat(),
            (v + visibleArea.y()).toFloat(),
            rightEdge - visibleArea.x(),
            bottomEdge - visibleArea.y(),
            textureWidth, textureHeight
        )

        val matrix = matrices.peek().model
        RenderSystem.setShader { GameRenderer.getPositionTexShader() }
        RenderSystem.setShaderColor(0F, 223F / 255, 83F / 255, 1F)

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.bufferBuilder
        val midX = (visibleArea.x() + visibleArea.width() / 2).toFloat()
        val midY = (visibleArea.y() + visibleArea.height() / 2).toFloat()
        val percent = player.stamina / player.maxStamina.toFloat()
        buffer.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_TEXTURE)
        buffer.vertex(matrix, midX, midY, 25F).uv(HALF_UV, HALF_UV).next()
        buffer.vertex(matrix, midX, 0F, 25F).uv(HALF_UV, 0F).next()

        if (percent >= 0.125) buffer.vertex(matrix, 0F, 0F, 25F).uv(0F, 0F).next()
        if (percent >= 0.25) buffer.vertex(matrix, 0F, midY, 25F).uv(0F, HALF_UV).next()
        if (percent >= 0.375) buffer.vertex(matrix, 0F, bottomEdge.toFloat(), 25F).uv(0F, UV).next()
        if (percent >= 0.5) buffer.vertex(matrix, midX, bottomEdge.toFloat(), 25F).uv(HALF_UV, UV).next()
        if (percent >= 0.625) buffer.vertex(matrix, rightEdge.toFloat(), bottomEdge.toFloat(), 25F).uv(UV, UV).next()
        if (percent >= 0.75) buffer.vertex(matrix, rightEdge.toFloat(), midY, 25F).uv(UV, HALF_UV).next()
        if (percent >= 0.875) buffer.vertex(matrix, rightEdge.toFloat(), 0F, 25F).uv(UV, 0F).next()
        if (percent == 1F) buffer.vertex(matrix, midX, 0F, 25F).uv(HALF_UV, 0F).next()

        if (percent != 1F && percent != 0.75F && percent != 0.5F && percent != 0.25F && percent != 0F) {
            var x = -1F
            var y = -1F
            if ((0.125 > percent || percent > 0.875))
                x = (-tan(percent * 2 * PI)).toFloat()
            else if (percent < 0.375)
                y = -1 / (tan(percent * 2 * PI)).toFloat()
            else if (percent < 0.625) {
                x = (tan(percent * 2 * PI)).toFloat()
                y = 1F
            } else {
                x = 1F
                y = 1 / (tan(percent * 2 * PI)).toFloat()
            }
            buffer
                .vertex(matrix, midX + x * RING_RADIUS, midY + y * RING_RADIUS, 25F)
                .uv((x + 1) * HALF_UV, (y + 1) * HALF_UV).next()
        }

        tessellator.draw()
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F)

        matrices.pop()
    }
}
