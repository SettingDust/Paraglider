package com.github.settingdust.paraglider.component

import com.github.settingdust.paraglider.Paraglider
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent
import dev.onyxstudios.cca.api.v3.component.tick.CommonTickingComponent
import net.minecraft.block.BlockState
import net.minecraft.block.CampfireBlock
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.tag.BlockTags
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.quiltmc.qkl.library.nbt.set

object UpdraftAccessor {
    fun World.findUpdraft(player: PlayerEntity) =
        Paraglider.Components.UPDRAFT[this].findUpdraft(player)

    fun PlayerEntity.findUpdraft() =
        world.findUpdraft(this)
}

data class UpdraftComponent(
    private val world: World
) : AutoSyncedComponent, CommonTickingComponent {
    companion object {
        val UPDRAFT_PREDICATE = { block: BlockState ->
            block.isInAndMatches(BlockTags.CAMPFIRES) { // TODO TAG
                if ((CampfireBlock.LIT in it)) it[CampfireBlock.LIT]
                else true
            }
        }
    }

    private var sources = mutableSetOf<BlockPos>()

    private var dirty = false
    override fun readFromNbt(tag: NbtCompound) {
        sources =
            tag.getLongArray("sources")
                .mapTo(mutableSetOf()) { BlockPos.fromLong(it) }
    }

    override fun writeToNbt(tag: NbtCompound) {
        val iterator = sources.iterator()
        tag["sources"] = LongArray(sources.size) { iterator.next().asLong() }
    }

    override fun shouldSyncWith(player: ServerPlayerEntity) = player.world == world

    private fun addSource(pos: BlockPos) {
        if (sources.add(pos)) {
            dirty = true
        }
    }

    private fun removeSource(pos: BlockPos) {
        if (sources.remove(pos)) {
            dirty = true
        }
    }

    fun findUpdraft(player: PlayerEntity): Int? {
        if (!Paraglider.config.updraftEnable() || player.world != world) return null
        sources.forEach {
            if (!UPDRAFT_PREDICATE(world.getBlockState(it))) removeSource(it)
        }
        val source = getSource(player.blockPos)
        if (source != null) return source
        val minPos = player.blockPos.x - 4 to player.blockPos.z - 4
        val maxPos = player.blockPos.x + 4 to player.blockPos.z + 4
        val halfHeight = (Paraglider.config.updraftHeight() / 2).toInt()
        val yRange = (player.blockPos.y - halfHeight) until (player.blockPos.y + halfHeight)
        for (x in minPos.first until maxPos.first) {
            for (z in (minPos.second until maxPos.second)) {
                for (y in yRange) {
                    val pos = BlockPos(x, y, z)
                    if (!world.isAir(pos) && UPDRAFT_PREDICATE(world.getBlockState(pos))) addSource(pos)
                }
            }
        }
        return getSource(player.blockPos)
    }

    private fun getSource(pos: BlockPos) = sources.firstOrNull {
        val subtracted = pos.subtract(it)
        subtracted.y in 1..Paraglider.config.updraftHeight().toInt() &&
            it.withY(0).isWithinDistance(pos.withY(0), 4.0)
    }?.let { pos.y - it.y }

    override fun tick() {
        if (Paraglider.config.updraftEnable() && dirty) {
            dirty = false
            Paraglider.Components.UPDRAFT.sync(world)
        }
    }
}
