package com.github.settingdust.paraglider

import com.github.settingdust.paraglider.component.ParaglidingComponent
import com.github.settingdust.paraglider.component.StaminaComponent
import com.github.settingdust.paraglider.component.UpdraftComponent
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer
import dev.onyxstudios.cca.api.v3.world.WorldComponentFactoryRegistry
import dev.onyxstudios.cca.api.v3.world.WorldComponentInitializer

internal class Components : EntityComponentInitializer, WorldComponentInitializer {
    override fun registerEntityComponentFactories(registry: EntityComponentFactoryRegistry) = with(registry) {
        registerForPlayers(Paraglider.Components.STAMINA) { StaminaComponent(it) }
        registerForPlayers(Paraglider.Components.PARAGLIDING) { ParaglidingComponent(it) }
    }

    override fun registerWorldComponentFactories(registry: WorldComponentFactoryRegistry) = with(registry) {
        register(Paraglider.Components.UPDRAFT) { UpdraftComponent(it) }
    }
}
