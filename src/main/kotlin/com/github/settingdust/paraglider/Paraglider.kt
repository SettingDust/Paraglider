package com.github.settingdust.paraglider

import com.github.settingdust.paraglider.component.ParaglidingAccessor.paragliding
import com.github.settingdust.paraglider.component.ParaglidingComponent
import com.github.settingdust.paraglider.component.StaminaComponent
import com.github.settingdust.paraglider.component.UpdraftComponent
import com.github.settingdust.paraglider.item.ItemParaglider
import com.llamalad7.mixinextras.MixinExtrasBootstrap
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry
import net.minecraft.client.item.ModelPredicateProviderRegistry
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.ClampedEntityAttribute
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.effect.StatusEffectType
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.DyeableItem
import net.minecraft.tag.TagKey
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry

object Paraglider {
    const val ID = "paraglider"
    val config = ParagliderConfig.createAndLoad()!!

    @Deprecated(message = "Mod entrypoint", level = DeprecationLevel.ERROR)
    internal fun preLaunch() = MixinExtrasBootstrap.init()

    @Deprecated(message = "Mod entrypoint", level = DeprecationLevel.ERROR)
    internal fun init() {
        StatusEffects
        Attributes
        Items
    }

    @Deprecated(message = "Mod entrypoint", level = DeprecationLevel.ERROR)
    internal fun clientInit() {
        ModelPredicateProviderRegistry.register(identifier("paragliding")) { item, _, _, _ ->
            if (item.paragliding) 1F else 0F
        }

        ColorProviderRegistry.ITEM.register(
            { stack, i -> if (i < 0) -1 else (stack.item as DyeableItem).getColor(stack) },
            Items.PARAGLIDER,
            Items.DEKU_LEAF_PARAGLIDER
        )
    }

    internal object Identifiers {
        val PARAGLIDER = identifier("paraglider")!!
        val DEKU_LEAF_PARAGLIDER = identifier("deku_leaf_paraglider")!!

        val STAMINA = identifier("stamina")!!
        val MAX_STAMINA = identifier("max_stamina")!!

        val PARAGLIDING = identifier("paragliding")!!

        val UPDRAFT = identifier("updraft")!!

        val EXHAUSTED = identifier("exhausted")!!
    }

    fun identifier(path: String) =
        if (':' in path) Identifier.tryParse(path)
        else Identifier(ID, path)

    object Items {
        val PARAGLIDER = ItemParaglider(0xA65955)
        val DEKU_LEAF_PARAGLIDER = ItemParaglider(0x3FB53F)

        init {
            Registry.register(
                Registry.ITEM,
                Identifiers.PARAGLIDER,
                PARAGLIDER
            )
            Registry.register(
                Registry.ITEM,
                Identifiers.DEKU_LEAF_PARAGLIDER,
                DEKU_LEAF_PARAGLIDER
            )
        }
    }

    internal object Tags {
        val LEATHER = TagKey.of(Registry.ITEM_KEY, Identifier("c", "leather"))

        val PARAGLIDER = TagKey.of(Registry.ITEM_KEY, Identifiers.PARAGLIDER)
        val UPDRAFT = TagKey.of(Registry.BLOCK_KEY, Identifier("paraglider", "updraft"))
    }

    object Components {
        val STAMINA = ComponentRegistry.getOrCreate(
            Identifiers.STAMINA,
            StaminaComponent::class.java
        )

        val PARAGLIDING = ComponentRegistry.getOrCreate(
            Identifiers.PARAGLIDING,
            ParaglidingComponent::class.java
        )

        val UPDRAFT = ComponentRegistry.getOrCreate(
            Identifiers.UPDRAFT,
            UpdraftComponent::class.java
        )
    }

    internal object Attributes {
        val MAX_STAMINA = ClampedEntityAttribute("max_stamina", 0.0, 0.0, Double.MAX_VALUE)

        init {
            Registry.register(
                Registry.ATTRIBUTE,
                Identifiers.MAX_STAMINA,
                MAX_STAMINA
            )
        }
    }

    internal object StatusEffects {
        val EXHAUSTED = object : StatusEffect(StatusEffectType.HARMFUL, 0x5a6c81) {
            override fun applyUpdateEffect(entity: LivingEntity, amplifier: Int) {
                entity.isSprinting = false
                entity.isSwimming = false
                if (entity is PlayerEntity) entity.paragliding = false
            }

            override fun canApplyUpdateEffect(duration: Int, amplifier: Int) = true
        }.addAttributeModifier(
            EntityAttributes.GENERIC_MOVEMENT_SPEED,
            "65ed2ca4-ceb3-4521-8552-73006dcba58d",
            -0.3,
            EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        )!!

        init {
            Registry.register(
                Registry.STATUS_EFFECT,
                Identifiers.EXHAUSTED,
                EXHAUSTED
            )
        }
    }
}
