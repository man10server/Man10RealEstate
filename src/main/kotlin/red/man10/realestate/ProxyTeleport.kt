package red.man10.realestate

import com.google.common.io.ByteStreams
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import red.man10.realestate.region.Region
import red.man10.realestate.util.Utility
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

object ProxyTeleport: Listener {
    val cookieNamespacedKey by lazy {
        NamespacedKey(Plugin.plugin, "man10realestate_cookie")
    }

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val player = e.player
        val cookie = player.retrieveCookie(cookieNamespacedKey)

        cookie.whenComplete { data, error ->
            if (error != null) {
                Plugin.plugin.logger.warning("Failed to retrieve cookie for player ${player.name}: ${error.message}")
                return@whenComplete
            }

            if (data == null) {
                return@whenComplete
            }

            val dataInput = ByteStreams.newDataInput(data)
            val regionId = dataInput.readInt()

            player.storeCookie(cookieNamespacedKey, ByteArray(0))

            Bukkit.getScheduler().runTask(Plugin.plugin, Runnable {
                teleportToRegion(player, regionId)
            })
        }
    }

    private fun teleportToRegion(player: Player, regionId: Int) {
        val region = Region.regionMap[regionId]
        if (region == null) {
            Utility.sendMessage(player, "§c土地が見つかりませんでした")
            return
        }

        if (!Utility.hasRegionPermission(player, regionId) && region.data.denyTeleport) {
            Utility.sendMessage(player, "§cこの土地はテレポートを許可されていません")
            return
        }

        region.teleport(player)
    }

    fun sendTeleportRequest(player: Player, server: String, regionId: Int) {

        val byteArray = ByteArrayOutputStream()
        val dataOutput = DataOutputStream(byteArray)
        dataOutput.writeInt(regionId)
        player.storeCookie(cookieNamespacedKey, byteArray.toByteArray())

        val connectOutput = ByteStreams.newDataOutput()
        connectOutput.writeUTF("Connect")
        connectOutput.writeUTF(server)
        player.sendPluginMessage(Plugin.plugin, "BungeeCord", connectOutput.toByteArray())
    }
}