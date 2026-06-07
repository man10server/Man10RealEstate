package red.man10.realestate.region

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import red.man10.realestate.Plugin.Companion.disableWorld
import red.man10.realestate.util.Utility.sendMessage
import tororo1066.itemframeprotector.api.event.IFPCause
import tororo1066.itemframeprotector.api.event.IFPInteractEvent
import tororo1066.itemframeprotector.api.event.IFPRemoveEvent

// ItemFrameProtector(任意導入)のイベントを処理するリスナー。
// IFPのクラスを参照するため、IFP導入時のみ登録すること。
object IFPEvent : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun itemFrameInteractEvent(e:IFPInteractEvent){
        if(disableWorld.contains(e.data.loc.world.name))return
        val p = e.entity
        if (p !is Player)return
        if (e.ifpCause == IFPCause.OP_STAFF)return
        if (!(Event.getCurrentRegion(e.data.loc)?.canEditItemFrame(p)?:p.isOp)){
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
        if (!(Event.getCurrentRegion(e.data.loc)?.canEditItemFrame(p)?:p.isOp)){
            sendMessage(p,"§7この額縁を触ることはできません！")
            e.isCancelled = true
            return
        }
        sendMessage(p,"§a額縁を撤去しました")
        e.isCancelled = false
    }
}
