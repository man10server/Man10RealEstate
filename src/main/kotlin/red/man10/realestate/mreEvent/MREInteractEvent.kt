package red.man10.realestate.mreEvent

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerInteractEvent
import red.man10.realestate.region.Region

class MREInteractEvent(
        val interactEvent:PlayerInteractEvent,
        @JvmField
        var isCancelled:Boolean,
        val region: Region?=null
): Event(),Cancellable {

    companion object{
        private val handlerList=HandlerList()
        @JvmStatic
        fun getHandlerList():HandlerList= handlerList
    }

    override fun getHandlers(): HandlerList= handlerList

    override fun isCancelled(): Boolean=isCancelled

    override fun setCancelled(cancel: Boolean) {
        isCancelled=cancel
    }
}