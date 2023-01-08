package com.github.settingdust.paraglider

import io.wispforest.owo.config.Option
import io.wispforest.owo.config.annotation.Config
import io.wispforest.owo.config.annotation.Modmenu
import io.wispforest.owo.config.annotation.SectionHeader
import io.wispforest.owo.config.annotation.Sync
import org.jetbrains.annotations.ApiStatus.Internal

@Suppress("unused")
@Internal
@Modmenu(modId = Paraglider.ID)
@Sync(Option.SyncMode.OVERRIDE_CLIENT)
@Config(name = Paraglider.ID, wrapperName = "ParagliderConfig")
class ParagliderConfigModel {
    @JvmField
    @SectionHeader("paraglider")
    var durability = 6000

    @JvmField
    var paraglidingSpeed = 0.026F

    @JvmField
    @SectionHeader("updraft")
    var updraftEnable = true

    @JvmField
    var updraftHeight = 12.0

    @SectionHeader("stamina")
    @JvmField
    var staminaEnable = true

    @JvmField
    var maxStamina = 1000

    @JvmField
    var paraglidingStamina = -3

    @JvmField
    var flyingStamina = 20

    @JvmField
    var vehicleStamina = 20

    @JvmField
    var swimmingStamina = -6

    @JvmField
    var underwaterStamina = 3

    @JvmField
    var breathableUnderwaterStamina = 10

    @JvmField
    var spiritingStamina = -10

    @JvmField
    var groundStamina = 20
}
