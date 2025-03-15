package red.man10.realestate.menu

import org.bukkit.Material
import org.bukkit.entity.Player
import red.man10.realestate.region.Bookmark
import red.man10.realestate.util.MenuFramework

class BookmarkMenu(p:Player,val page:Int = 0) : MenuFramework(p, LARGE_CHEST_SIZE,"ブックマーク"){

    override fun init() {
        clickable(false)

        var inc = 0

        while (menu.getItem(44) == null){

            val index = inc + page*45
            inc ++
            val list = Bookmark.bookmarkMap[p.uniqueId]?: listOf()
            if (list.size <= index)break

            val button = Button(Material.PAPER)
            val id = list[index]
            button.title("§bID:${id}")
            button.lore(listOf("§e左クリック§fでブックマークした土地に飛ぶ",
                    "§cシフト右クリック§fでブックマークを削除"))
            button.setClickAction{
                if(it.isRightClick&&it.isShiftClick){
                    Bookmark.changeBookmark(p,id)
                    menu.remove(button.icon())
                    return@setClickAction
                }
                if(it.isLeftClick) {
                    delete(p)
                    p.closeInventory()
                    p.performCommand("mre tp $id")
                }
            }
            addButton(button)
        }

        //Back
        val back = Button(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
        back.title("")
        arrayOf(45,46,47,48,49,50,51,52,53).forEach { setButton(back,it) }

        //previous
        if (page!=0){
            val previous = Button(Material.RED_STAINED_GLASS_PANE)
            previous.title("前のページへ")
            previous.setClickAction{ BookmarkMenu(p,page-1) }
            arrayOf(45,46,47).forEach { setButton(previous,it) }

        }

        //next
        if (inc>=44){
            val next = Button(Material.RED_STAINED_GLASS_PANE)
            next.title("次のページへ")
            next.setClickAction{ BookmarkMenu(p,page+1) }
            arrayOf(51,52,53).forEach { setButton(next,it) }
        }
    }

}