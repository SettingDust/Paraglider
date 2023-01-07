package com.github.settingdust.paraglider.item

import com.github.settingdust.paraglider.Paraglider
import net.minecraft.client.item.TooltipContext
import net.minecraft.item.DyeableItem
import net.minecraft.item.Item
import net.minecraft.item.ItemGroup
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtElement
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.world.World
import org.quiltmc.qsl.item.setting.api.QuiltItemSettings

class ItemParaglider(private val defaultColor: Int) :
    Item(
        QuiltItemSettings().maxCount(1).group(
            ItemGroup.TRANSPORTATION
        )
    ),
    DyeableItem {

    override fun getMaxDamage() = Paraglider.config.durability()

    override fun isDamageable() = maxDamage > 0

    override fun canRepair(stack: ItemStack, ingredient: ItemStack) =
        ingredient.isIn(Paraglider.Tags.LEATHER)

    override fun getColor(stack: ItemStack) =
        stack.getSubNbt(DyeableItem.DISPLAY_KEY)
            ?.takeIf { it.contains(DyeableItem.COLOR_KEY, NbtElement.NUMBER_TYPE.toInt()) }
            ?.getInt(DyeableItem.COLOR_KEY) ?: defaultColor

    override fun appendTooltip(
        stack: ItemStack,
        world: World?,
        tooltip: MutableList<Text>,
        context: TooltipContext
    ) {
        if (stack.isDamageable && stack.maxDamage <= stack.damage) {
            tooltip += Text.translatable("tooltip.paraglider.paraglider_broken").apply {
                style = Style.EMPTY.withColor(Formatting.RED)
            }
        }
    }
}
