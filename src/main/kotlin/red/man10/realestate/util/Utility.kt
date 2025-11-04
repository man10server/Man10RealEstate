package red.man10.realestate.util

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent.runCommand
import net.kyori.adventure.text.event.HoverEvent.showText
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.realestate.Command
import red.man10.realestate.Plugin
import red.man10.realestate.Plugin.Companion.prefix
import red.man10.realestate.region.Region
import red.man10.realestate.region.user.Permission
import red.man10.realestate.region.user.User
import kotlin.math.abs

object Utility {

    ////////////////////////////////////////////////////////////
    //立体の対角線の頂点から、指定座標が立体の中にあるかどうか判定するメソッド
    ////////////////////////////////////////////////////////////
    fun isWithinRange(loc: Location, start:Triple<Int,Int,Int>, end:Triple<Int,Int,Int>, world:String,server:String):Boolean{

        if (Plugin.serverName !=server){
            return false
        }
        if (loc.world.name != world){
            return false
        }

        if (abs((start.first+end.first)-2*loc.blockX) > abs(start.first-end.first) ||
            abs((start.third+end.third)-2*loc.blockZ) > abs(start.third-end.third) ||
            abs((start.second+end.second)-2*loc.blockY) > abs(start.second-end.second)){
            return false
        }

        return true

    }

    //ホバーテキスト、クリックイベント
    fun sendClickMessage(p: Player, text: String, command: String,hoverText : String) {
        val c = text()
        val h =

        p.sendMessage(text("$prefix$text").clickEvent(runCommand("/$command")).hoverEvent(showText(text(hoverText))))
    }

    //prefix付きのメッセージ
    fun sendMessage(player: Player, message: String) {
        player.sendMessage("$prefix$message")
    }
    fun sendMessage(sender: CommandSender, message: String) {
        sender.sendMessage("$prefix$message")
    }

    fun sendDebug(sender:CommandSender,message: String){
        sender.sendMessage("$prefix§c§l[DEBUG]$message")
    }

    fun format(double: Double):String{
        return String.format("%,.0f",double)
    }

    //プレイヤーが所持している土地がある都市の名前を返す
    fun playerLivedCities(player:Player):ArrayList<String>{
        val cities=ArrayList<String>()
        Region.regionMap.values.filter { region -> region.ownerUUID==player.uniqueId}.forEach {region->
            region.data.city?.let {
                if(!cities.contains(it)){
                    cities.add(it)
                }
            }
        }

        return cities
    }

    /**
     * 指定リージョンの編集権限を持っているかどうか
     */
    fun hasRegionPermission(p:Player,id:Int):Boolean{

        if (p.hasPermission(Command.OP))return true

        val data = Region.regionMap[id]?:return false

        if (data.status == Region.Status.LOCK)return false

        if (data.ownerUUID == p.uniqueId)return true

        val userData = User.get(p,id)?:return false

        return userData.permissions.contains(Permission.ALL) && userData.status == "Share"
    }
}