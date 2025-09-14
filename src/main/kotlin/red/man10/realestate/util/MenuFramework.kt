package red.man10.realestate.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * マイクラプラグインでメニューを作るためのフレームワーク
 * このクラスを継承させて使用する。
 * 起動時にsetup関数を呼んでPluginインスタンスを渡す
 *
 * (最終更新 2024/11/11) created by Jin Morikawa
 */
open class MenuFramework(val p:Player,private val menuSize: Int, private val title: String){

    lateinit var menu : Inventory
    private var closeAction : OnCloseListener? = null
    private var clickAction : Button.OnClickListener? = null
    private var clickable=true

    companion object{
        private val menuStack = ConcurrentHashMap<UUID,Stack<MenuFramework>>()
        private lateinit var instance : JavaPlugin

        const val CHEST_SIZE = 27
        const val LARGE_CHEST_SIZE= 54

        //      起動時に読み込む
        fun setup(plugin:JavaPlugin){
            instance = plugin
        }

        fun push(p:Player,menu: MenuFramework){
            val stack = menuStack[p.uniqueId]?: Stack()
            if (stack.isNotEmpty() && menu::class == stack.peek()::class){
                return
            }
            stack.push(menu)
            menuStack[p.uniqueId] = stack
        }

        //      スタックの取り出し
        fun pop(p:Player): MenuFramework?{
            val stack = menuStack[p.uniqueId]?:return null
            if (stack.isEmpty())return null
            val menu = stack.pop()
            menuStack[p.uniqueId] = stack
            return menu
        }

        //      スタックの最新を確かめる
        fun peek(p:Player): MenuFramework? {
            val stack = menuStack[p.uniqueId]
            if (stack.isNullOrEmpty()) return null
            return stack.peek()
        }

        fun delete(p:Player){
            menuStack.remove(p.uniqueId)
        }

        fun dispatch(plugin:JavaPlugin,job:()->Unit){
            Bukkit.getScheduler().runTask(plugin, Runnable(job))
        }
    }

    open fun init(){}

    fun clickable(boolean:Boolean){
        clickable=boolean
    }

    fun open(){
//        p.closeInventory()
        Bukkit.getScheduler().runTask(instance,Runnable {
            menu = Bukkit.createInventory(null,menuSize, text(title))
            init()
            push(p,this)
            p.openInventory(menu)
        })
    }

    //slotは0スタート
    fun setButton(button: Button, slot:Int){
        menu.setItem(slot,button.icon())
    }

    //
    fun addButton(button: Button){
        menu.addItem(button.icon())
    }

    //背景として全埋めする
    fun fill(button: Button){
        for (i in 0 until menu.size){
            setButton(button,i)
        }
    }

    fun setCloseAction(action: OnCloseListener){
        closeAction = action
    }

    fun setClickAction(action: Button.OnClickListener){
        clickAction = action
    }

    fun close(e:InventoryCloseEvent){
        Bukkit.getScheduler().runTask(instance,Runnable {
            closeAction?.closeAction(e)
            if (e.reason == InventoryCloseEvent.Reason.PLAYER) {
                pop(p)//ひとつ前のメニューに戻るためにスタックを一個削除
                pop(p)?.open()
            }
            if (e.reason == InventoryCloseEvent.Reason.PLUGIN){
                menuStack.remove(e.player.uniqueId)
            }
        })
    }

    fun interface OnCloseListener{
        fun closeAction(e: InventoryCloseEvent)
    }

    class Button(icon:Material):Cloneable{

        private var buttonItem : ItemStack
        private var actionData : OnClickListener? = null
        private val key = UUID.randomUUID().toString()

        init {
            buttonItem = ItemStack(icon)
            val meta = buttonItem.itemMeta
            meta.persistentDataContainer.set(BUTTON_KEY, PersistentDataType.STRING,key)
            buttonItem.itemMeta = meta
        }

        companion object{

            private val BUTTON_KEY = NamespacedKey("forest611","button_key")

            private val buttonMap = HashMap<String, Button>()

            fun set(button: Button){
                buttonMap[button.key] = button
            }

            fun get(item:ItemStack): Button?{

                if (!item.hasItemMeta())return null

                val meta = item.itemMeta
                val key = meta.persistentDataContainer[BUTTON_KEY, PersistentDataType.STRING]?:return null

                return buttonMap[key]
            }

            fun isButton(item:ItemStack):Boolean{
                val meta = item.itemMeta?:return false
                return meta.persistentDataContainer[BUTTON_KEY, PersistentDataType.STRING] != null
            }
        }

        fun setIcon(item:ItemStack): Button {
            buttonItem = item.clone()
            val meta = buttonItem.itemMeta
            meta.persistentDataContainer.set(BUTTON_KEY, PersistentDataType.STRING,key)
            buttonItem.itemMeta = meta
            set(this)
            return this
        }

        fun title(text:String): Button {
            val meta = buttonItem.itemMeta
            meta.displayName(text(text))
            buttonItem.itemMeta = meta
            set(this)
            return this
        }

        fun cmd(int:Int): Button {
            val meta = buttonItem.itemMeta
            meta.setCustomModelData(int)
            buttonItem.itemMeta = meta
            set(this)
            return this
        }

        fun lore(lore:List<String>): Button {
            val loreComponent = mutableListOf<Component>()
            lore.forEach { loreComponent.add(text(it)) }

            val meta = buttonItem.itemMeta
            meta.lore(loreComponent)
            buttonItem.itemMeta = meta
            set(this)
            return this

        }

        fun setClickAction(action: OnClickListener): Button {
            actionData = action
            set(this)
            return this
        }

        fun enchant(boolean: Boolean): Button {

            val meta = buttonItem.itemMeta

            if (!boolean){
                meta.enchants.forEach { meta.removeEnchant(it.key) }
                buttonItem.itemMeta = meta
                set(this)
                return this
            }

            meta.addEnchant(Enchantment.FORTUNE,1,false)
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            buttonItem.itemMeta = meta
            set(this)
            return this
        }


        fun click(e:InventoryClickEvent){
            e.isCancelled = true
            actionData?.action(e)
        }

        fun icon():ItemStack{
            return buttonItem
        }

        public override fun clone(): Button {
            return super.clone() as Button
        }

        fun interface OnClickListener{
            fun action(e:InventoryClickEvent)
        }
    }

    object MenuListener:Listener{

        @EventHandler
        fun openInv(e:InventoryOpenEvent){

            if(e.player !is Player)return

            val player=e.player as Player

            val menu = peek(player) ?:return

            //メニューが違う場合は無視
            if (e.view.title != menu.title){
                delete(player)
                e.isCancelled = true
                player.closeInventory()
                player.sendMessage("§c§lメニューを開き直してください")
                return
            }

        }

        @EventHandler
        fun clickEvent(e:InventoryClickEvent){

            val p = e.whoClicked

            if (p !is Player)return

            val item = e.currentItem
            //ボタンをクリックした場合
            if (item != null && Button.isButton(item)){

                //ボタンをクリックしたインベントリがプレイヤーのインベントリの場合
                if (e.clickedInventory == p.inventory){
                    Bukkit.broadcast(text("§c§lエラー発生。レポートをお願いします。-611"))
                    Bukkit.getLogger().info("Man10Commerce Menu Error (${p.name}) -611")
                    item.amount = 0
                    return
                }

                val button = Button.get(item)?.let {
                    it.click(e)
                }
            }

            val menu = peek(p) ?:return

            //メニューが違う場合は無視
            if (e.view.title != menu.title){
                delete(p)
                e.isCancelled = true
                p.closeInventory()
                p.sendMessage("§c§lメニューを開き直してください")
                return
            }

            if(!menu.clickable)e.isCancelled=true

            menu.clickAction?.action(e)
        }

        @EventHandler
        fun closeEvent(e:InventoryCloseEvent){

            if (e.player !is Player)return

            val menu = peek(e.player as Player) ?:return

            //メニューが違う場合は無視
            if (e.view.title != menu.title){
                return
            }

            menu.close(e)
        }

        @EventHandler
        fun interactEvent(e:PlayerInteractEvent){
            val item = e.item?:return
            if (!Button.isButton(item))return
            item.amount = 0

            e.isCancelled = true
            Bukkit.broadcast(text("§c§lエラー発生。レポートをお願いします。-611"))
            Bukkit.getLogger().info("Man10ItemBank Menu Error (${e.player.name}) -611")
        }

    }
}
