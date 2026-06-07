package red.man10.realestate.menu

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import red.man10.realestate.Plugin
import red.man10.realestate.region.user.Permission
import red.man10.realestate.region.user.User
import red.man10.realestate.util.MenuFramework
import red.man10.realestate.util.Utility
import red.man10.realestate.util.Utility.sendMessage

class ManageUserMenu(p:Player,val user: User) : MenuFramework(p, 9,"${Bukkit.getOfflinePlayer(user.uuid).name}の管理"){

    override fun init() {
        clickable(false)
        val rg = user.region
        val name = Bukkit.getOfflinePlayer(user.uuid).name

        //賃料ボタン
        val rentButton = Button(Material.DIAMOND)
        rentButton.cmd(1)

        rentButton.title("§e§l賃料を設定する(コマンドを使います)")
        rentButton.lore(mutableListOf("§f賃料を設定します","§c金額を0にすると賃料が発生しなくなります"))
        rentButton.setClickAction{
            p.sendMessage(
                Component.text(Plugin.prefix).append(
                    Component.text("§b§l§n[ここをクリック]")
                .clickEvent(ClickEvent.suggestCommand("/mre setrent ${user.region.id} $name "))))
            p.closeInventory()
        }
        setButton(rentButton,1)

        //権限設定
        val permissionButton = Button(Material.EMERALD_BLOCK)
        permissionButton.title("§f§l権限の設定")

        val lore=mutableListOf(
                "§a§l現在の設定",
                "§7§l全権限(現在使用不可):${if (user.permissions.contains(Permission.ALL)) "§a§lo" else "§c§lx"}",
                "§7§lブロック:${if (user.permissions.contains(Permission.BLOCK)) "§a§lo" else "§c§lx"}",
                "§7§lチェストや樽:${if (user.permissions.contains(Permission.INVENTORY)) "§a§lo" else "§c§lx"}",
                "§7§lドア:${if (user.permissions.contains(Permission.DOOR)) "§a§lo" else "§c§lx"}"
        )
        if(Plugin.isIFPEnabled()){
            lore.add("§7§l額縁:${if (user.permissions.contains(Permission.ITEM_FRAME)) "§a§lo" else "§c§lx"}")
        }

        permissionButton.lore(lore)
        permissionButton.setClickAction{
            PermissionMenu(p,user).open()
        }
        setButton(permissionButton,4)

        //退去
        val deleteButton = Button(Material.BARRIER)
        deleteButton.title("§c§l退去させる")
        deleteButton.setClickAction{
            user.asyncDelete()
            sendMessage(p, "§a§l退去できました！")
            p.closeInventory()
        }
        setButton(deleteButton,7)

    }
}

class PermissionMenu(p:Player,val user: User) : MenuFramework(p, 9,"${Bukkit.getOfflinePlayer(user.uuid).name}の権限"){

    override fun init() {
        clickable(false)

        val allButton = Button(if (user.permissions.contains(Permission.ALL)) Material.EMERALD_BLOCK else Material.REDSTONE_BLOCK)
        allButton.title("§e§l全権限(付与注意!)")
        allButton.setClickAction{
            if(user.region.ownerUUID!=p.uniqueId){
                sendMessage(p,"§c§l権限がありません")
                return@setClickAction
            }
            user.switchPermission(Permission.ALL)
            user.asyncSave()
            PermissionMenu(p,user).open()
        }
        setButton(allButton,0)

        val blockButton = Button(if (user.permissions.contains(Permission.BLOCK)) Material.EMERALD_BLOCK else Material.REDSTONE_BLOCK)
        blockButton.title("§e§lブロックの設置破壊")
        blockButton.setClickAction{
            if(user.region.ownerUUID!=p.uniqueId){
                sendMessage(p,"§c§l権限がありません")
                return@setClickAction
            }
            user.switchPermission(Permission.BLOCK)
            user.asyncSave()
            PermissionMenu(p,user).open()
        }
        setButton(blockButton,2)

        val invButton = Button(if (user.permissions.contains(Permission.INVENTORY)) Material.EMERALD_BLOCK else Material.REDSTONE_BLOCK)
        invButton.title("§e§l樽やかまどなどのインベントリ")
        invButton.setClickAction{
            if(user.region.ownerUUID!=p.uniqueId){
                sendMessage(p,"§c§l権限がありません")
                return@setClickAction
            }
            user.switchPermission(Permission.INVENTORY)
            user.asyncSave()
            PermissionMenu(p,user).open()
        }
        setButton(invButton,4)

        val doorButton = Button(if (user.permissions.contains(Permission.DOOR)) Material.EMERALD_BLOCK else Material.REDSTONE_BLOCK)
        doorButton.title("§e§lドアの開閉")
        doorButton.setClickAction{
            if(user.region.ownerUUID!=p.uniqueId){
                sendMessage(p,"§c§l権限がありません")
                return@setClickAction
            }
            user.switchPermission(Permission.DOOR)
            user.asyncSave()
            PermissionMenu(p,user).open()
        }

        setButton(doorButton,6)
        if(Plugin.isIFPEnabled()) {
            val itemFrameButton = Button(if (user.permissions.contains(Permission.ITEM_FRAME)) Material.EMERALD_BLOCK else Material.REDSTONE_BLOCK)
            itemFrameButton.title("§e§l額縁の編集")
            itemFrameButton.setClickAction {
                if (user.region.ownerUUID != p.uniqueId) {
                    sendMessage(p, "§c§l権限がありません")
                    return@setClickAction
                }
                user.switchPermission(Permission.ITEM_FRAME)
                user.asyncSave()
                PermissionMenu(p, user).open()
            }
            setButton(itemFrameButton, 8)
        }
    }
}