package red.man10.realestate.region

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.TileState
import org.bukkit.block.data.Openable
import org.bukkit.block.data.type.Bed
import org.bukkit.block.data.type.Bell
import org.bukkit.block.data.type.Cake
import org.bukkit.block.data.type.Campfire
import red.man10.realestate.region.user.Permission

object BlockMaterialUtils {

    private val interactiveBlockMap= hashMapOf(
            Pair(Permission.INVENTORY, listOf(
                    Material.CHEST,
                    Material.ENDER_CHEST,
                    Material.HOPPER,
                    Material.TRAPPED_CHEST,
                    Material.DISPENSER,
                    Material.DROPPER,
                    Material.BLAST_FURNACE,
                    Material.FURNACE,
                    Material.BARREL,
                    Material.SHULKER_BOX,
                    Material.BLACK_SHULKER_BOX,
                    Material.BLUE_SHULKER_BOX,
                    Material.BROWN_SHULKER_BOX,
                    Material.CYAN_SHULKER_BOX,
                    Material.GRAY_SHULKER_BOX,
                    Material.GREEN_SHULKER_BOX,
                    Material.LIGHT_BLUE_SHULKER_BOX,
                    Material.LIGHT_GRAY_SHULKER_BOX,
                    Material.LIME_SHULKER_BOX,
                    Material.MAGENTA_SHULKER_BOX,
                    Material.ORANGE_SHULKER_BOX,
                    Material.PINK_SHULKER_BOX,
                    Material.PURPLE_SHULKER_BOX,
                    Material.RED_SHULKER_BOX,
                    Material.WHITE_SHULKER_BOX,
                    Material.YELLOW_SHULKER_BOX,
                    Material.BEACON,
                    Material.DECORATED_POT)),

            Pair(Permission.DOOR,listOf(
                    Material.ACACIA_DOOR,
                    Material.BAMBOO_DOOR,
                    Material.DARK_OAK_DOOR,
                    Material.BIRCH_DOOR,
                    Material.CHERRY_DOOR,
                    Material.CRIMSON_DOOR,
                    Material.JUNGLE_DOOR,
                    Material.MANGROVE_DOOR,
                    Material.OAK_DOOR,
                    Material.SPRUCE_DOOR,
                    Material.SPRUCE_DOOR,
                    Material.WARPED_DOOR,

                    Material.ACACIA_TRAPDOOR,
                    Material.BAMBOO_TRAPDOOR,
                    Material.DARK_OAK_TRAPDOOR,
                    Material.BIRCH_TRAPDOOR,
                    Material.CHERRY_TRAPDOOR,
                    Material.CRIMSON_TRAPDOOR,
                    Material.JUNGLE_TRAPDOOR,
                    Material.MANGROVE_TRAPDOOR,
                    Material.OAK_TRAPDOOR,
                    Material.SPRUCE_TRAPDOOR,
                    Material.SPRUCE_TRAPDOOR,
                    Material.WARPED_TRAPDOOR,

                    Material.ACACIA_FENCE_GATE,
                    Material.BAMBOO_FENCE_GATE,
                    Material.BIRCH_FENCE_GATE,
                    Material.CHERRY_FENCE_GATE,
                    Material.CRIMSON_FENCE_GATE,
                    Material.DARK_OAK_FENCE_GATE,
                    Material.JUNGLE_FENCE_GATE,
                    Material.MANGROVE_FENCE_GATE,
                    Material.OAK_FENCE_GATE,
                    Material.SPRUCE_FENCE_GATE,
                    Material.WARPED_FENCE_GATE)
            )
    )

    fun isInteractive(block:Block):Boolean{

        val data=block.blockData

        if(block.state is TileState)return true//チェストやかまど等の内部値を持つもの
        if(data is Openable)return true//ドアなどの開けられるもの. 上のリストでも良いが一応こっち

        //上でカバーできないもの
        if(listOf(
                Material.BAMBOO_BUTTON,
                Material.ACACIA_BUTTON,
                Material.BIRCH_BUTTON,
                Material.CHERRY_BUTTON,
                Material.CRIMSON_BUTTON,
                Material.DARK_OAK_BUTTON,
                Material.JUNGLE_BUTTON,
                Material.MANGROVE_BUTTON,
                Material.OAK_BUTTON,
                Material.POLISHED_BLACKSTONE_BUTTON,
                Material.SPRUCE_BUTTON,
                Material.STONE_BUTTON,
                Material.WARPED_BUTTON,
                Material.ANVIL,
                Material.CHIPPED_ANVIL,
                Material.DAMAGED_ANVIL,
                Material.CARTOGRAPHY_TABLE,
                Material.CRAFTING_TABLE,
                Material.STONECUTTER,
                Material.FLETCHING_TABLE,
                Material.SMITHING_TABLE,
                Material.GRINDSTONE,
                Material.LOOM,
                Material.COMPARATOR,
                Material.NOTE_BLOCK,
                Material.CAULDRON,
                Material.FLOWER_POT,
                Material.DRAGON_EGG,
                Material.LEVER,
                Material.REPEATER,
                Material.REDSTONE,
                Material.CAKE,
                Material.SWEET_BERRY_BUSH
        ).contains(block.type))return true

        return false
    }

    fun getAllowedBlocks(permission: Permission):List<Material>{
        return interactiveBlockMap[permission]?: listOf()
    }

}