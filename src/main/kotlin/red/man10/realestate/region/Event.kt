package red.man10.realestate.region

import net.kyori.adventure.text.Component.text
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.Container
import org.bukkit.block.Sign
import org.bukkit.block.data.Openable
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.hanging.HangingPlaceEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.material.Attachable
import org.bukkit.material.Colorable
import org.bukkit.persistence.PersistentDataType
import red.man10.realestate.Command
import red.man10.realestate.Plugin.Companion.WAND_NAME
import red.man10.realestate.Plugin.Companion.disableWorld
import red.man10.realestate.Plugin.Companion.serverName
import red.man10.realestate.mreEvent.MREInteractEvent
import red.man10.realestate.region.user.Permission
import red.man10.realestate.region.user.User
import red.man10.realestate.util.Utility
import red.man10.realestate.util.Utility.sendMessage
import tororo1066.itemframeprotector.api.event.IFPCause
import tororo1066.itemframeprotector.api.event.IFPInteractEvent
import tororo1066.itemframeprotector.api.event.IFPRemoveEvent

object Event :Listener{

    var maxContainers = 24

    /**
     * 看板のアップデート
     */
    private fun updateSign(sign: Sign, id:Int){

        val rg = Region.regionMap[id]?:return

        sign.line(0, text("§eID:$id"))
        sign.line(1, text(rg.name))
        sign.line(2, text("§d§l${rg.ownerName}"))
        sign.line(3, text("§b§l${Region.formatStatus(rg.status)}"))

        sign.update()

    }

    private fun setFirstPosition(p:Player,loc: Location,wand:ItemStack){

        val meta = wand.itemMeta

        val lore = meta.lore()?: mutableListOf()

        meta.persistentDataContainer.set(NamespacedKey.fromString("first")!!, PersistentDataType.STRING,"${loc.blockX};${loc.blockY};${loc.blockZ}")


        if (lore.size>=5){
            lore[0] = text("§aOwner:§f${p.name}")
            lore[1] = text("§aServer:§f$serverName")
            lore[2] = text("§aWorld:§f${p.world.name}")
            lore[3] = text("§aStart:§fX:${loc.blockX},Y:${loc.blockY},Z:${loc.blockZ}")
        }else{
            lore.add(text("§aOwner:§f${p.name}"))
            lore.add(text("§aServer:§f"+p.server.name))
            lore.add(text("§aWorld:§f"+p.world.name))
            lore.add(text("§aStart:§fX:${loc.blockX},Y:${loc.blockY},Z:${loc.blockZ}"))
        }

        meta.lore(lore)

        wand.itemMeta = meta
        sendMessage(p,"§e§lSet Start:§f§lX:${loc.blockX},Y:${loc.blockY},Z:${loc.blockZ}")
    }

    private fun setSecondPosition(p:Player,loc: Location,wand:ItemStack){

        val meta = wand.itemMeta

        val lore = meta.lore()?: mutableListOf()

        meta.persistentDataContainer.set(NamespacedKey.fromString("second")!!, PersistentDataType.STRING,"${loc.blockX};${loc.blockY};${loc.blockZ}")

        if (lore.size == 5){
            lore[4] = text("§aEnd:§fX:${loc.blockX},Y:${loc.blockY},Z:${loc.blockZ}")
        }else{
            lore.add(text("§aEnd:§fX:${loc.blockX},Y:${loc.blockY},Z:${loc.blockZ}"))
        }

        meta.lore(lore)

        wand.itemMeta = meta
        sendMessage(p,"§e§lSet End:§f§lX:${loc.blockX},Y:${loc.blockY},Z:${loc.blockZ}")

    }

    @EventHandler
    fun joinEvent(e:PlayerJoinEvent){
        val p = e.player
        Bookmark.asyncLoadBookmark(p)
        Region.asyncLoginProcess(p)
        User.asyncLoginProcess(p)
    }

    /**
     * 範囲指定
     */
    @EventHandler
    fun setPosition(e: PlayerInteractEvent){
        val p = e.player

        if (!p.hasPermission(Command.OP))return

        val isFirst = when(e.action){
            Action.RIGHT_CLICK_BLOCK,Action.RIGHT_CLICK_AIR ->false
            Action.LEFT_CLICK_AIR,Action.LEFT_CLICK_BLOCK->true
            else -> return
        }

        val wand = e.item?:return
        if (wand.type != Material.STICK)return
        if (!wand.hasItemMeta())return
        if(wand.itemMeta.displayName != WAND_NAME) return

        val loc = when(e.action){
            Action.LEFT_CLICK_AIR,Action.RIGHT_CLICK_AIR -> p.location
            Action.LEFT_CLICK_BLOCK,Action.RIGHT_CLICK_BLOCK -> e.clickedBlock!!.location
            else ->return
        }

        if (isFirst){
            setFirstPosition(p,loc, wand)
        }else{
            setSecondPosition(p,loc,wand)
        }

        e.isCancelled = true

    }

    //リージョンの看板作成
    @EventHandler
    fun signChangeEvent(e: SignChangeEvent){
        val lines = e.lines
        val p = e.player

        if (lines[0].indexOf("mre:") == 0){

            val id : Int

            try {
                id = lines[0].replace("mre:","").toInt()
            }catch (e:Exception){
                sendMessage(p,"§3§l入力方法：\"mre:<id>\"")
                return
            }

            val rg = Region.regionMap[id]?:return
            if (!Utility.isWithinRange(e.block.location ,rg.startPosition,rg.endPosition,rg.world,rg.server) &&!(getCurrentRegion(e.block.location)?.canEditBlock(p)?:p.isOp)){
                sendMessage(e.player,"§c土地の外に看板を設置することはできません")
                return
            }

            e.line(0, text("§eID:$id"))
            e.line(1, text(rg.name))
            e.line(2, text("§d§l${rg.ownerName}"))
            e.line(3, text("§b§l${Region.formatStatus(rg.status)}"))

            sendMessage(p,"§a§l作成完了！ id:$id name:${rg.name}")
        }
    }

    //看板クリック
    @EventHandler
    fun signClickEvent(e:PlayerInteractEvent){

        if (e.action != Action.RIGHT_CLICK_BLOCK && e.action !=Action.LEFT_CLICK_BLOCK)return

        val b= e.clickedBlock?:return
        val sign = b.state

        if (sign !is Sign)return

        val lines = sign.lines

        val id = lines[0].replace("§eID:","").toIntOrNull()?:return

        val rg = Region.regionMap[id]?:return

        val p = e.player

        //左クリックでブックマーク
        if (e.action == Action.LEFT_CLICK_BLOCK && !p.isSneaking){
            e.isCancelled = true
            p.performCommand("mre bookmark $id")
            return
        }

        rg.showRegionData(p)
        updateSign(sign,id)

//        sendMessage(p,"§a==========${rg.name}§a§lの情報==========")
//        sendMessage(p,"§aID:$id")
//        sendMessage(p,"§aステータス:${Region.formatStatus(rg.status)}")
//        sendMessage(p,"§aオーナー:${rg.ownerName}")
//        sendMessage(p,"§a値段:${format(rg.price)}")
//        sendMessage(p,"§a税額:${format(City.getTax(id))}")
//        if (rg.taxStatus == Region.TaxStatus.WARN){
//            sendMessage(p,"§c§l税金が未払いです")
//        }
//        sendMessage(p,"§a==========================================")
//
//        sendClickMessage(p,"§d§lブックマークする！＝＞[ブックマーク！]","mre bookmark $id","ブックマークをすると、/mreメニューから テレポートをすることができます")
//
//        if (rg.status == Region.Status.ON_SALE){
//            sendClickMessage(p,"§a§l§n[土地を買う！]","mre buyconfirm $id","§e§l値段:${format(rg.price)}")
//        }

    }

    //////////////////////////////////////////////////////////////////////////
    //保護処理 イベント
    ///////////////////////////////////////////////////////////////////////////

    private val containerList = listOf(
            Material.CHEST,
            Material.HOPPER,
            Material.TRAPPED_CHEST
    )

    @EventHandler(priority = EventPriority.LOWEST)
    fun blockBreakEvent(e: BlockBreakEvent){

        if(disableWorld.contains(e.player.world.name))return

        val p = e.player

        if (!(getCurrentRegion(e.block.location)?.canEditBlock(p)?:p.isOp)){
            sendMessage(p,"§7このブロックを壊すことはできません！")
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun blockPlaceEvent(e: BlockPlaceEvent){
        if(disableWorld.contains(e.player.world.name))return
        val p = e.player

        val block = e.block

        if (!(getCurrentRegion(block.location)?.canEditBlock(p)?:p.isOp)){
            sendMessage(p,"§cここにブロックを置くことはできません！")
            e.isCancelled = true
            return
        }

        if (block.type==Material.CHEST){ sendMessage(p,"§7§lチェストより樽の使用をおすすめします！") }

        if (containerList.contains(block.type) && countContainer(block)> maxContainers){
            sendMessage(p,"§7このチャンクには、これ以上このブロックは置けません！")
            e.isCancelled = true
        }

    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerBucketEmpty(e: PlayerBucketEmptyEvent) {
        if(disableWorld.contains(e.player.world.name))return
        val p = e.player

        if (!(getCurrentRegion(e.block.location)?.canEditBlock(p)?:p.isOp)) {
            sendMessage(p,"§7ここに水などを置くことはできません！")
            e.isCancelled = true

        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun interactEvent(e:PlayerInteractEvent){
        if(disableWorld.contains(e.player.world.name))return

        if (e.action == Action.RIGHT_CLICK_AIR && e.action == Action.LEFT_CLICK_AIR)return

        val p = e.player
        //光るイカ墨全部弾く
        if(e.item?.type==Material.GLOW_INK_SAC&&!e.player.isOp){
            sendMessage(p,"§7光るイカ墨を使うことはできません！")
            e.isCancelled=true
            return
        }

        if (!e.hasBlock())return

        val block=e.clickedBlock?:return

        if (e.hasBlock()&&block.state is Sign){

            if (!e.hasItem()){return}

            val dye = e.item!!.itemMeta

            if (dye !is Colorable && e.item!!.type != Material.GLOW_INK_SAC)return
        }


        val region=getCurrentRegion(block.location)
        val mreInteractEvent=MREInteractEvent(e,false,region)

        if (BlockMaterialUtils.getAllowedBlocks(Permission.DOOR).contains(block.type)){

            if(!(region?.canUseDoor(p)?:p.isOp)) {
                mreInteractEvent.isCancelled=true
            }
        }
        else if (BlockMaterialUtils.getAllowedBlocks(Permission.INVENTORY).contains(e.clickedBlock!!.type)){
            if (!(region?.canOpenContainer(p)?:p.isOp)){
                mreInteractEvent.isCancelled=true
            }
        }
        else if(BlockMaterialUtils.isInteractive(block)&&!(region?.canInteract(p)?:p.isOp)){
            mreInteractEvent.isCancelled=true
        }
        else if(e.action==Action.PHYSICAL&&!(region?.canEditBlock(p)?:p.isOp)){
            mreInteractEvent.isCancelled=true
        }

        Bukkit.getPluginManager().callEvent(mreInteractEvent)
        if(mreInteractEvent.isCancelled){
            sendMessage(p,"§7このブロックを触ることはできません！")
            e.isCancelled = true
        }

//        if (!hasPermission(p,e.clickedBlock!!.location,Permission.DOOR)) {
//            sendMessage(p, "§7このブロックを触ることはできません！")
//            e.isCancelled = true
//            return
//        }



    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun signEvent(e:SignChangeEvent){
        if(disableWorld.contains(e.player.world.name))return
        val p = e.player

        if (!(getCurrentRegion(e.block.location)?.canEditBlock(p)?:p.isOp)){
            sendMessage(p,"§7ここに看板を置くことはできません！")
            e.isCancelled = true
        }

    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun hangingPlace(e:HangingPlaceEvent){
        val player=e.player?:return
        if(disableWorld.contains(player.world.name))return
        if(!(getCurrentRegion(e.entity.location)?.canEditItemFrame(player)?:player.isOp)){
            sendMessage(player,"§7ここに額縁を置くことはできません！")//多分絵画でもここ通る
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun breakEntity(e: HangingBreakByEntityEvent){
        if(disableWorld.contains(e.remover.world.name))return
        val p = e.remover?:return

        if (p !is Player)return

        //いるのかわからんのでとりあえず分離している
        if(e.entity.type in listOf(EntityType.ITEM_FRAME,EntityType.GLOW_ITEM_FRAME)){
            if(!(getCurrentRegion(e.entity.location)?.canEditItemFrame(p)?:p.isOp)){
                sendMessage(p,"§7この額縁を触ることはできません！")
                e.isCancelled=true
            }
            return
        }

        if (!(getCurrentRegion(e.entity.location)?.canEditBlock(p)?:p.isOp)){
            sendMessage(p,"§7このブロックを触ることはできません！")
            e.isCancelled = true
        }

    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun interact(e:PlayerInteractEntityEvent){
        if(disableWorld.contains(e.player.world.name))return

        val p = e.player

        //いるのかわからんのでとりあえず分離している
        if(e.rightClicked.type in listOf(EntityType.ITEM_FRAME,EntityType.GLOW_ITEM_FRAME)){
            if(!(getCurrentRegion(e.rightClicked.location)?.canEditItemFrame(p)?:p.isOp)){
                sendMessage(p,"§7この額縁を触ることはできません！")
                e.isCancelled=true
            }
            return
        }

        if (!(getCurrentRegion(e.rightClicked.location)?.canEditBlock(p)?:p.isOp)){
            sendMessage(p,"§7このブロックを触ることはできません！")
            e.isCancelled = true
        }

    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun breakEntity(e:EntityDamageByEntityEvent){
        if(disableWorld.contains(e.damager.world.name))return

        val p = e.damager

        if (p !is Player)return

        if (!(getCurrentRegion(e.entity.location)?.canEditBlock(p)?:p.isOp)){
            sendMessage(p,"§7このブロックを触ることはできません！")
            e.isCancelled = true
        }

    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun armorStand(e:PlayerArmorStandManipulateEvent){
        if(disableWorld.contains(e.player.world.name))return
        val p = e.player

        if (!(getCurrentRegion(e.rightClicked.location)?.canEditBlock(p)?:p.isOp)){
            sendMessage(p,"§7このアーマースタンドを触ることはできません！")
            e.isCancelled = true
        }

    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun itemFrameInteractEvent(e:IFPInteractEvent){
        if(disableWorld.contains(e.data.loc.world.name))return
        val p = e.entity
        if (p !is Player)return
        if (e.ifpCause == IFPCause.OP_STAFF)return
        if (!(getCurrentRegion(e.data.loc)?.canEditItemFrame(p)?:p.isOp)){
            sendMessage(p,"§7この額縁を触ることはできません！")
            e.isCancelled = true
            return
        }
        e.isCancelled = false
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun itemFrameRemoveEvent(e:IFPRemoveEvent){
        if(disableWorld.contains(e.data.loc.world.name))return
        val p = e.remover
        if (p !is Player)return
        if (e.ifpCause == IFPCause.OP_STAFF)return
        if (!(getCurrentRegion(e.data.loc)?.canEditItemFrame(p)?:p.isOp)){
            sendMessage(p,"§7この額縁を触ることはできません！")
            e.isCancelled = true
        }
        sendMessage(p,"§a額縁を撤去しました")
        e.isCancelled = false
    }

    private fun getCurrentRegion(loc:Location):Region?{
        Region.regionMap.values.forEach{ rg ->

            if (Utility.isWithinRange(loc,rg.startPosition,rg.endPosition,rg.world,rg.server)){
                return rg
            }
        }
        return null
    }

//    private fun hasPermission(p:Player, loc: Location, perm: Permission):Boolean{
//
//        if (p.hasPermission(Command.OP))return true
//
//        if (disableWorld.contains(loc.world.name)){ return true }
//
//        val city=City.where(loc)?:return false
//
////        city.getBelongingRegions().forEach {region ->
////            if(region.isInRegion(loc)){
////                region.getUser(p)?.let {user->
////                    user.hasPermission(perm)
////                }?:return false
////            }
////        }
//
//        Region.regionMap.values.forEach{ rg ->
//
//            if (Utility.isWithinRange(loc,rg.startPosition,rg.endPosition,rg.world,rg.server)){
//                return rg.hasPermission(p,perm)
//            }
//        }
//
//        return false
//    }

    private fun countContainer(block: Block): Int {

        val te = block.chunk.tileEntities

        var count = 0

        for (entity in te){
            if (containerList.contains(entity.block.type))count++
        }

        return count
    }


}