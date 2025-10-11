package red.man10.realestate

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.apache.commons.lang.math.NumberUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import red.man10.realestate.Plugin.Companion.WAND_NAME
import red.man10.realestate.Plugin.Companion.async
import red.man10.realestate.Plugin.Companion.disableWorld
import red.man10.realestate.Plugin.Companion.plugin
import red.man10.realestate.Plugin.Companion.prefix
import red.man10.realestate.Plugin.Companion.vault
import red.man10.realestate.menu.MainMenu
import red.man10.realestate.region.*
import red.man10.realestate.region.user.Permission
import red.man10.realestate.region.user.User
import red.man10.realestate.util.MySQLManager
import red.man10.realestate.util.Utility
import red.man10.realestate.util.Utility.format
import red.man10.realestate.util.Utility.sendClickMessage
import red.man10.realestate.util.Utility.sendMessage
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object Command:CommandExecutor {

    private const val USER = "mre.user"
    private const val GUEST = "mre.guest"
    private const val SET_OWNER="mre.user.setowner"
    private const val SET_PRICE="mre.user.setprice"
    const val OP = "mre.op"

    private val userConfirm = ConcurrentHashMap<UUID,Int>()
    private val buyConfirmKey = HashMap<UUID, Int>()
    private val ownerConfirmKey = HashMap<UUID,Int>()

    private var isRunning=AtomicBoolean(false)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

//        if (sender !is Player)return false

        if (label == "mre"){

            if (sender !is Player)return false

            if (args.isEmpty()){

                if (!hasPermission(sender,GUEST))return false

                MainMenu(sender).open()
                return true
            }

            when(args[0]){

                "dirt"->{
                    val loc=sender.location
                    if(loc.block.type!=Material.AIR){
                        sendMessage(sender,"§c§l足元が空気ブロックではありません")
                        return true
                    }
                    Region.regionMap.forEach{ entry ->
                        val rg = entry.value

                        if (Utility.isWithinRange(sender.location,rg.startPosition,rg.endPosition,rg.world,rg.server)) {
                            if (rg.ownerUUID == sender.uniqueId) {
                                if(!vault.withdraw(sender.uniqueId,10.0)){
                                    sendMessage(sender,"§c§lお金が足りません!")
                                }
                                else{
                                    loc.block.type=Material.DIRT
                                    sendMessage(sender,"§a§lブロックを設置しました")
                                }
                                return true
                            }
                        }
                    }
                    sendMessage(sender,"§c§l自分の土地以外に対して使用することはできません")
                }

                "buy" ->{

                    if (!hasPermission(sender,USER))return false

                    val id = buyConfirmKey[sender.uniqueId]?:return false
                    buyConfirmKey.remove(sender.uniqueId) //購入確認キー消去

                    async.execute {
                        val rg = Region.regionMap[id]
                        if (rg == null){
                            sendMessage(sender,"§c§l存在しない土地です")
                            return@execute
                        }
                        rg.buy(sender)
                    }
                    return true
                }

                "buyconfirm" ->{

                    if (!hasPermission(sender,USER))return false

                    if (args.size != 2)return false

                    val id = args[1].toIntOrNull()?:return false

                    val rg = Region.regionMap[id]?:return false

                    if (rg.status != Region.Status.ON_SALE){
                        sendMessage(sender,"§c§lこの土地は販売されていません！")
                        return false
                    }

                    // 購入確認キーを生成
                    buyConfirmKey[sender.uniqueId] = id


                    sendMessage(sender,"§e§l値段：${format(rg.price)}")
                    sendMessage(sender,"§e§lID：${id}")
                    sendMessage(sender,"§a§l現在のオーナー：${rg.ownerName}")
                    sendMessage(sender,"§e§l本当に購入しますか？(購入しない場合は無視してください)")

                    sender.sendMessage(text(prefix).append(text("§a§l[購入する]")
                        .clickEvent(ClickEvent.runCommand("/mre buy")))
                        .hoverEvent(HoverEvent.showText(text("§6§l電子マネー${format(rg.price)}円")
                    )))

                    return true
                }

                "bookmark" ->{

                    if (args.size !=2)return false

                    if (!hasPermission(sender,GUEST))return false

                    val id = args[1].toIntOrNull()?:return false


                    Bookmark.changeBookmark(sender,id)

                    return true
                }

                "adduser" ->{

                    if (!hasPermission(sender,USER))return false

                    if (args.size < 3){
                        sendMessage(sender,"§c§l/mre adduser <ID> <ユーザー名>")
                        return false
                    }

                    val id = args[1].toIntOrNull()?:return false

                    if (!hasRegionPermission(sender,id)){ return false }

                    val data = Region.regionMap[id]

                    if (data == null){
                        sendMessage(sender,"§c§l存在しない土地です！")
                        return false
                    }

                    val user = Bukkit.getPlayer(args[2])

                    if (user == null){
                        sendMessage(sender,"§c§lユーザーがオフラインの可能性があります！")
                        return false
                    }

                    if (user.uniqueId == data.ownerUUID){
                        return false
                    }

                    if (User.get(user,id) != null){
                        sendMessage(sender,"§c§lこのユーザーは既に住人です！")
                        return false
                    }

                    async.execute {
                        sendMessage(sender,"§a§l現在承諾待ちです....")

                        //住人側へのメッセージ
                        sendMessage(user,"§a§l=================土地の情報==================")
                        sendMessage(user,"§a§lオーナー：${sender.name}")
                        sendMessage(user,"§a§l土地のID：$id")
//                        if (rent>0.0){
//                            sendMessage(user,"§a§l賃料：${rent}円 スパン:${spanDisplay}")
//                            sendMessage(user,"§a§l住人になる場合、初回賃料を銀行から引き出されます！")
//                        }
                        sendMessage(user,"§a§l===========================================")

                        user.sendMessage(text(prefix).append(text("§b§l§n[住民になる]")
                            .clickEvent(ClickEvent.runCommand("/mre acceptuser"))))
                        userConfirm[user.uniqueId] = id
                    }

                    return true

                }

                "acceptuser"->{

                    if (!hasPermission(sender,GUEST))return false

                    if (!userConfirm.keys.contains(sender.uniqueId))return false

                    val id = userConfirm[sender.uniqueId]?:return false
                    val rg = Region.regionMap[id]!!
                    userConfirm.remove(sender.uniqueId)

                    val owner = Bukkit.getPlayer(rg.ownerUUID!!)

                    if (owner == null){
                        sendMessage(sender,"家主がログアウトしたためキャンセルされました")
                        return true
                    }

                    rg.addUser(sender)

                    return true

                }

                "removeuser" ->{

                    if (args.size != 3)return false

                    if (!hasPermission(sender,USER))return false

                    val id = args[1].toIntOrNull()?:return false

                    if (!hasRegionPermission(sender,id))return false

                    val p = Bukkit.getPlayer(args[2])

                    if (p == null){
                        sendMessage(sender,"§c§l住人がオフラインなので退去させられません！")
                        return false
                    }

                    User.get(p,id)?.asyncDelete()
                    sendMessage(sender,"§a§l退去できました！")
                    return true

                }

                "span" ->{
                    if (!hasPermission(sender,USER))return false

                    if (args.size!=3)return false

                    val id = args[1].toIntOrNull()?:return false
                    val span = args[2].toIntOrNull()?:return false

                    if (!hasRegionPermission(sender,id))return false

                    val rg = Region.regionMap[id]?:return false
                    rg.span = span
                    rg.asyncSave()


                }

                "setowner" ->{
                    if (!hasPermission(sender,USER))return false

                    if(sender.isPermissionSet(SET_OWNER)&&!hasPermission(sender, SET_OWNER))return false

                    if (args.size != 3)return false

                    val id = args[1].toIntOrNull()?:return false
                    val rg = Region.regionMap[id]?:return false
                    val city = City.where(rg.teleport)?:return false

                    if (sender.uniqueId != rg.ownerUUID && !sender.hasPermission(OP))return false

                    val p = Bukkit.getPlayer(args[2])

                    if (rg.taxStatus == Region.TaxStatus.WARN){
                        sendMessage(sender,"§c§l税金滞納中はオーナーの譲渡はできません")
                        return true
                    }

                    if (p == null){
                        sendMessage(sender,"§c§lオンラインのユーザーを入力してください")
                        return true
                    }

                    sendMessage(sender,"現在承認待ち・・・")

                    sendMessage(p,"§a§l土地のオーナー変更の依頼が来ています")
                    sendMessage(p,"§a§l現在のオーナー:${sender.name}")
                    sendMessage(p,"§a§lID:${id}")
                    sendMessage(p,"§a§l都市名:${city.cityId}")
                    sendMessage(p,"§a§l税額:${City.getTax(id)}円")
                    p.sendMessage(text(prefix).append(text("§b§l§n[変更を受け入れる]").clickEvent(ClickEvent.runCommand("/mre acceptowner"))))

                    ownerConfirmKey[p.uniqueId] = id

                    return true

                }

                "acceptowner" ->{
                    if (!hasPermission(sender, USER))return false

                    val id = ownerConfirmKey[sender.uniqueId]?:return false
                    val rg = Region.regionMap[id]?:return false

                    if(!rg.canOwn(sender))return false

                    if (rg.ownerUUID!=null){
                        val oldOwner = Bukkit.getPlayer(rg.ownerUUID!!)
                        oldOwner?.let { sendMessage(it,"承認されました") }
                    }

                    sendMessage(sender,"土地のオーナーになりました")

                    ownerConfirmKey.remove(sender.uniqueId)

                    rg.setOwner(sender)
                    rg.asyncSave()

                }

                "settp" ->{
                    if (!sender.hasPermission(USER))return true

                    val id = args[1].toIntOrNull()?:return false

                    if (!hasRegionPermission(sender,id))return false

                    val loc = sender.location

                    val rg = Region.regionMap[id]?:return false

                    if (!Utility.isWithinRange(loc,rg.startPosition,rg.endPosition,rg.world,rg.server)){
                        sendMessage(sender,"§c土地の外にテレポートポイントを登録することはできません")
                        return true
                    }

                    rg.teleport = loc
                    rg.asyncSave()

                    sendMessage(sender,"§e§l登録完了！")
                    return true
                }

                "setrent" ->{// mre setrent id p amount

                    if (!hasPermission(sender,USER))return false

                    if (args.size != 4)return false

                    val id = args[1].toIntOrNull()?:return false

                    if (!hasRegionPermission(sender,id))return false

                    val rent = args[3].toDoubleOrNull()
                    val p = Bukkit.getPlayer(args[2])

                    if (rent == null || rent< 0.0 || rent == -0.0){
                        sendMessage(sender,"金額の設定に問題があります！")
                        return true
                    }

                    if (p == null){
                        sendMessage(sender,"§c§l住人がオンラインのときのみ、賃料を変更できます")
                        return false
                    }

                    val user = User.get(p,id)?:return false

                    user.rentAmount = rent
                    user.asyncSave()

                    sendMessage(sender,"§a§l設定完了！")
                    sendMessage(p,"§a§lID:${id}の賃料が変更されました！賃料:$rent")
                    sendMessage(p,"§a§l不当な変更の場合はレポートをしてください")

                    return true
                }

                "setstatus" ->{
                    if (!hasPermission(sender,USER))return false

                    if (args.size != 3)return false

                    val id = args[1].toIntOrNull()?:return false
                    val status = args[2]
                    val rg = Region.regionMap[id]?:return false


                    rg.setStatus(sender,Region.Status.valueOf(status))
                    rg.asyncSave()

                    return true

                }

                "setprice" ->{
                    if (!hasPermission(sender,USER))return false

                    if(sender.isPermissionSet(SET_PRICE)&&!hasPermission(sender, SET_PRICE))return false


                    if (args.size != 3)return false

                    if (!NumberUtils.isNumber(args[2]))return false

                    val id = args[1].toIntOrNull()?:return false
                    val rg = Region.regionMap[id]?:return false

                    if (!hasRegionPermission(sender,id))return false

                    val price = args[2].toDoubleOrNull()

                    if (price==null || price <0.0 || price == -0.0){
                        sendMessage(sender,"§c§l金額の設定に問題があります！")
                        return false
                    }

                    rg.price = price
                    rg.asyncSave()

                    sendMessage(sender,"§a§l${id}の金額を${args[2]}に変更しました")

                }

                "tp" ->{
                    if (!hasPermission(sender, USER))return false

                    if (args.size < 2)return false

                    val id = args[1].toIntOrNull()

                    if (id == null){
                        sendMessage(sender,"§c§l数字を入力してください")
                        return true
                    }
                    val rg = Region.regionMap[id]

                    if (rg==null){
                        sendMessage(sender,"§c§l指定したIDの土地は存在しません")
                        return true
                    }

                    if (!hasRegionPermission(sender,id) && rg.data.denyTeleport){
                        sendMessage(sender,"この土地はテレポートを許可されていません")
                        return true
                    }

                    if (rg.server != Plugin.serverName){
                        sender.performCommand("warpsystem:tp" +
                                " ${sender.name} ${rg.teleport.x} ${rg.teleport.y} ${rg.teleport.z} ${rg.teleport.yaw} ${rg.teleport.pitch}" +
                                " ${rg.server} ${rg.teleport.world.name}")
                        return true
                    }

                    sender.teleport(rg.teleport)
                    return true

                }

                "confirminit" -> {
                    if (!hasPermission(sender, USER))return false

                    if (args.size < 2)return false

                    val id = args[1].toIntOrNull()

                    if (id == null){
                        sendMessage(sender,"§c§l数字を入力してください")
                        return true
                    }
                    val rg = Region.regionMap[id]

                    if (rg==null){
                        sendMessage(sender,"§c§l指定したIDの土地は存在しません")
                        return true
                    }

                    if (rg.ownerUUID != sender.uniqueId){
                        sendMessage(sender,"§c§l持ち主以外は使用できません")
                        return false
                    }

                    sendMessage(sender,"§c§l=============土地を手放します==============")
                    sendMessage(sender,"§c§l手放すと買い直さないと元に戻りません！")
                    sendMessage(sender,"§c§l手放すときに１ヶ月分の税金の支払いが必要です")
                    sender.sendMessage(text(prefix).append(text("§c§l§n[土地を手放す]")
                        .clickEvent(ClickEvent.runCommand("/mre init $id"))))

                }

                "init" -> {
                    if (!hasPermission(sender, USER))return false

                    if (args.size < 2)return false

                    val id = args[1].toIntOrNull()

                    if (id == null){
                        sendMessage(sender,"§c§l数字を入力してください")
                        return true
                    }
                    val rg = Region.regionMap[id]

                    if (rg==null){
                        sendMessage(sender,"§c§l指定したIDの土地は存在しません")
                        return true
                    }

                    if (rg.ownerUUID != sender.uniqueId){
                        sendMessage(sender,"§c§l持ち主以外は使用できません")
                        return false
                    }

                    val tax = City.getTax(id)

                    if (!vault.withdraw(sender.uniqueId,tax)){
                        sendMessage(sender,"§c§l所持金が足りません！(必要額:${format(tax)}円)")
                        return false
                    }

                    rg.initialize(tax = rg.data.tax)

                    sendMessage(sender,"§c§l手放しました")
                }


                "balance" ->{

                    if (!hasPermission(sender, USER))return false

                    sendMessage(sender,"§e§l支払う税金")
                    Region.regionMap.filterValues { it.ownerUUID == sender.uniqueId }.forEach {
                        sendMessage(sender,"§eID:${it.key}:税額:${City.getTax(it.key)}")
                    }

                }

                "where" ->{
                    if (!hasPermission(sender,USER))return false

                    val loc = sender.location

                    for (rg in Region.regionMap.values) {
                        if(Utility.isWithinRange(loc, rg.startPosition, rg.endPosition, rg.world,rg.server)) {
                            rg.showRegionData(sender)
                            return true
                        }
                    }
                    sendMessage(sender,"§7このエリアには土地がありませんでした")
                }

                else ->{
                    sendMessage(sender,"§c§l不明なコマンドです！")
                    return false

                }
            }
        }

        if (label == "mreop"){

            if (sender is Player && !hasPermission(sender,OP))return false

            if (args.isEmpty()){

                sendMessage(sender,"""
                    §e§l/mreop wand : 範囲指定用のワンドを取得
                    §e§l/mreop create <rg/city> <リージョン名/都市名> <値段/税額> : 新規リージョンを作成します
                    §e§l範囲指定済みの${WAND_NAME}§e§lを持ってコマンドを実行してください
                    §e§l/mreop delete <rg/city> <id> : 指定idのリージョンを削除します
                    §e§l/mreop reload : 再読み込みをします
                    §e§l/mreop where : 現在地点がどのリージョンが確認します
                    §e§l/mreop reset <rg/city> <id> : 指定idのrg/cityの範囲を再指定します
                    §e§l/mreop disableWorld <add/remove> <world> : 指定ワールドの保護を外します
                    §e§l/mreop tax <rg/city> <id/city名> <tax>: 指定土地/都市の税額を変更します
                    §e§l/mreop buyscore <id> <score>: 指定都市の買うのに必要なスコアを変更します
                    §e§l/mreop livescore <id> <score>: 指定都市の住むのに必要なスコアを変更します
                    §e§l/mreop init <id> <price> : 指定リージョンを初期化する
                    §e§l/mreop maxuser <city> <int> : 指定都市の上限人数を設定する
                    §e§l/mreop setprice <id/city名> <price> : 指定土地/都市内の土地全ての値段を変更する
                    §e§l/mreop starttax : 手動で税金を徴収する
                    §e§l/mreop search : 指定ユーザーの持っている土地を確認する"
                    §e§l/mreop editcity <city> : 指定都市の編集コマンド一覧を表示する"
                    §e§l/mreop editrg <city> : 指定リージョンの編集コマンド一覧を表示する"
                    §e§l/mreop denytp <regionID> : 指定リージョンのmre tpの規制を編集"
                    §e§l/mreop reloadCityData <regionID/all> : 指定リージョンの所属している土地情報をリロードする"
                    §e§l/mreop citylimit <city名> <数字> : 指定都市の所持数上限を設定する"
                    §e§l/mreop payTaxFromWarnRegion : 手動で滞納税金を徴収する"
                """.trimIndent())

                return true
            }

            when(args[0]){

                //mreop create city <name> <tax>
                //mreop create rg <name> <tax>
                "create" ->{

                    if (sender !is Player)return false

                    if (args.size != 4)return false

                    val wand = sender.inventory.itemInMainHand

                    if (!wand.hasItemMeta() || wand.itemMeta.displayName != WAND_NAME){
                        sendMessage(sender,"${WAND_NAME}§e§lを持ってください！")
                        return true
                    }

                    val lore = wand.lore

                    if (lore == null || wand.lore!!.size != 5){
                        sendMessage(sender,"§e§fの範囲指定ができていません！")
                        return true
                    }

                    if (!NumberUtils.isNumber(args[3])){
                        sendMessage(sender,"§3§lパラメータの入力方法が違います！")
                        sendMessage(sender,"§3§l/mreop create city [都市の名前] [税額]")
                        sendMessage(sender,"§3§l/mreop create rg [リージョンの名前] [初期の値段]")
                        return true
                    }

                    val amount = args[3].toDouble()

                    sendMessage(sender,"§a§l現在登録中です・・・")

                    async.execute {
                        val meta = wand.itemMeta

                        val c1 = meta.persistentDataContainer[NamespacedKey.fromString("first")!!,
                            PersistentDataType.STRING]?.split(";")?:return@execute
                        val startPosition = Triple(c1[0].toInt(),c1[1].toInt(),c1[2].toInt())

                        val c2 = meta.persistentDataContainer[NamespacedKey.fromString("second")!!,
                            PersistentDataType.STRING]?.split(";")?:return@execute

                        val endPosition = Triple(c2[0].toInt(),c2[1].toInt(),c2[2].toInt())

                        var id = -1

                        if (args[1] == "city"){

                            if(City.cityMap.contains(args[2])){
                                sendMessage(sender,"§c§l${args[2]}は既に存在します")
                                return@execute
                            }

                            val city=City(args[2],sender.world.name,Plugin.serverName,startPosition,endPosition,amount)
                            city.registerCityForRegion()
                            city.asyncSave()
//                            val city = City()
//                            city.name = args[2]
//                            city.world = sender.world.name
//                            city.server = Plugin.serverName
//                            city.setStart(startPosition)
//                            city.setEnd(endPosition)
//                            city.tax = amount
//                            city.asyncSave()

                            sendMessage(sender,"§a§l登録処理終了")

                            return@execute

                        }else if (args[1] == "rg"){
                            id = Region.create(startPosition,endPosition,args[2],amount,sender.location,sender)
                        }

                        if (id == -1){
                            sendMessage(sender,"§c§l登録失敗！")
                            return@execute
                        }

                        sendMessage(sender,"§a§l登録完了！")

                        if (args[1] == "rg"){
                            sendMessage(sender,"§a§l”mre:$id”と記入した看板を置いてください！")
                        }
                    }
                }

                "editcity" ->{ //mreop editcity <id>

                    if (args.size != 2){
                        sendMessage(sender,"/mreop editcity <id>")
                        return true
                    }
                    val name = args[1]

                    sendMessage(sender,"${name}の設定")

                    sender.sendMessage(text("§b§n税金")
                        .clickEvent(ClickEvent.suggestCommand("/mreop tax city $name ")))
                    sender.sendMessage(text("§b§n都市内リージョン初期化時の金額を設定")
                        .clickEvent(ClickEvent.suggestCommand("/mreop defaultPrice city $name ")))
                    sender.sendMessage(text("§b§n所有スコア")
                        .clickEvent(ClickEvent.suggestCommand("/mreop buyscore $name ")))
                    sender.sendMessage(text("§b§n居住スコア")
                        .clickEvent(ClickEvent.suggestCommand("/mreop livescore $name ")))
                    sender.sendMessage(text("§b§n最大居住人数")
                        .clickEvent(ClickEvent.suggestCommand("/mreop maxuser $name ")))
                    sender.sendMessage(text("§c§n都市の削除")
                        .clickEvent(ClickEvent.suggestCommand("/mreop delete city $name")))


                    return true
                }

                "editrg" -> {
                    if (args.size != 2){
                        sendMessage(sender,"/mreop editrg <id>")
                        return true
                    }

                    val id = args[1].toInt()

                    sendMessage(sender,"${id}の設定")
                    sender.sendMessage(text("§b§n金額の設定")
                        .clickEvent(ClickEvent.suggestCommand("/mre setprice $id ")))
                    sender.sendMessage(text("§b§n初期化時の金額を設定(0にすると、都市で設定した金額になる)")
                        .clickEvent(ClickEvent.suggestCommand("/mreop defaultPrice rg $id ")))
                    sender.sendMessage(text("§b§n税金(0にすると都市で設定した金額になる)")
                        .clickEvent(ClickEvent.suggestCommand("/mreop tax rg $id ")))
                    sender.sendMessage(text("§b§nテレポート拒否")
                        .clickEvent(ClickEvent.runCommand("/mreop denytp $id")))
                    sender.sendMessage(text("§b§n免税")
                        .clickEvent(ClickEvent.runCommand("/mreop remit $id")))
                    sender.sendMessage(text("§c§n初期化")
                        .clickEvent(ClickEvent.suggestCommand("/mreop init $id ")))
                    sender.sendMessage(text("§c§n削除")
                        .clickEvent(ClickEvent.suggestCommand("/mreop delete rg $id")))

                    return true
                }

                "delete" ->{

                    if (args.size != 3)return false


                    val isRg = args[1] == "rg"

                    if (isRg){

                        if (!NumberUtils.isNumber(args[2])){
                            sendMessage(sender,"§c§l数字を入力してください")
                            return true
                        }

                        val id = args[2].toInt()
                        val rg = Region.regionMap[id]

                        if (rg == null){
                            sendMessage(sender,"§c§l存在しない土地です！")
                            return true

                        }

                        rg.asyncDelete()
                        sendMessage(sender,"§a§l削除完了！")

                        return true

                    }

                    val id = args[2]
                    val city = City.cityMap[id]

                    if (city == null){
                        sendMessage(sender,"§c§l存在しない都市です！")
                        return true

                    }
                    city.asyncDelete()
                    City.cityMap.remove(id)
                    sendMessage(sender,"§a§l削除完了！")

                }

                "wand" ->{

                    if (sender !is Player)return false

                    val wand = ItemStack(Material.STICK)
                    val meta = wand.itemMeta
                    meta.displayName(text(WAND_NAME))
                    wand.itemMeta = meta
                    sendMessage(sender,"範囲指定棒の取得")
                    sender.inventory.addItem(wand)
                    return true

                }

                "reload" ->{

                    City.asyncLoad()
                    Region.asyncLoad()
                    User.asyncLoad()
                    plugin.loadConfig()

                    sendMessage(sender,"§e§lリロード完了")

                }

                "disableWorld" ->{

                    if (args.size != 3)return true

                    if (args[2].isBlank()){
                        sendMessage(sender,"§3§l保護を外すワールドを指定してください")
                        return true
                    }

                    if (args[1] == "add"){
                        disableWorld.add(args[2])

                        Bukkit.getScheduler().runTaskAsynchronously(plugin,Runnable {
                            plugin.config.set("disableWorld", disableWorld)
                            plugin.saveConfig()
                            sendMessage(sender,"追加完了！")
                        })
                    }
                    if (args[1] == "remove"){
                        disableWorld.remove(args[2])

                        Bukkit.getScheduler().runTaskAsynchronously(plugin,Runnable {
                            plugin.config.set("disableWorld", disableWorld)
                            plugin.saveConfig()
                            sendMessage(sender,"削除完了！")

                        })
                    }
                }

                "where" ->{

                    if (sender !is Player)return false

                    val loc = sender.location

                    Bukkit.getScheduler().runTaskAsynchronously(plugin,Runnable {

                        sendMessage(sender,"土地数:${Region.regionMap.size}")
                        sendMessage(sender,"都市数:${City.cityMap.size}")

                        sendMessage(sender, "§e§l=====================================")

                        for (rg in Region.regionMap.values) {

                            if(Utility.isWithinRange(loc, rg.startPosition, rg.endPosition, rg.world,rg.server)) {
                                sendMessage(sender, "§e§lRegionID:${rg.id}")
                                sendMessage(sender, "§7Name:${rg.name}")
                                sendMessage(sender, "§8Price:${rg.price}")
                                sendMessage(sender, "§7Owner:${rg.ownerName}")
                                sendMessage(sender,"§8Tax:${City.getTax(rg.id)}")
                                sendMessage(sender,"§7User:")
                                User.userMap.filterKeys { pair->pair.second==rg.id }.forEach {
                                    sendMessage(sender,"§8${Bukkit.getOfflinePlayer(it.key.first).name}")
                                }
                            }
                        }


                        for (city in City.cityMap.values){

                            if(Utility.isWithinRange(loc, city.getStart(), city.getEnd(), city.data.world,city.data.server)) {
                                sendMessage(sender, "§e§lCityID:${city.cityId}")
                                sendMessage(sender, "§8Tax:${city.data.tax}")
                                sendMessage(sender, "§7MaxUser:${city.data.maxUser}")
                            }
                        }

                        sendMessage(sender, "§e§l=====================================")

                    })
                }

                //都市の範囲の再設定
                "reset" ->{//mreop reset city id
                    if (sender !is Player)return false

                    if (args.size != 3)return false

                    val wand = sender.inventory.itemInMainHand

                    if (!wand.hasItemMeta() || wand.itemMeta.displayName != WAND_NAME){
                        sendMessage(sender,"${WAND_NAME}§e§lを持ってください！")
                        return true
                    }

                    val lore = wand.lore

                    if (lore == null){
                        sendMessage(sender,"§e§fの範囲指定ができていません！")
                        return true
                    }

                    if (!NumberUtils.isNumber(args[2]))return true

                    val isRg = args[1] == "rg"

                    val meta = wand.itemMeta

                    val c1 = meta.persistentDataContainer[NamespacedKey.fromString("first")!!,
                            PersistentDataType.STRING]?.split(";")?:return true
                    val startPosition = Triple(c1[0].toInt(),c1[1].toInt(),c1[2].toInt())

                    val c2 = meta.persistentDataContainer[NamespacedKey.fromString("second")!!,
                            PersistentDataType.STRING]?.split(";")?:return true

                    val endPosition = Triple(c2[0].toInt(),c2[1].toInt(),c2[2].toInt())

                    if (isRg){

                        val id = args[2].toInt()
                        val rg = Region.regionMap[id]

                        if (rg == null){
                            sendMessage(sender,"§c§l存在しない土地です！")
                            return true
                        }

                        rg.startPosition = startPosition
                        rg.endPosition = endPosition

                        rg.asyncSave()

                        sendMessage(sender,"§a§l再設定完了！")
                        return true
                    }

                    val id = args[2]
                    val city = City.cityMap[id]

                    if (city == null){
                        sendMessage(sender,"§c存在しない都市です")
                        return true
                    }

                    city.setStart(startPosition)
                    city.setEnd(endPosition)
                    city.asyncSave()
                    sendMessage(sender,"設定完了")
                }

                "tax" ->{//mreop tax rg/city id tax

                    if (args.size != 4)return false
                    if (!NumberUtils.isNumber(args[3]))return false

                    val tax= args[3].toDouble()

                    if (args[1] == "rg"){
                        val id = args[2].toIntOrNull()
                        if(id!=null){
                            val rg = Region.regionMap[id] ?: return false
                            rg.data.tax = tax
                            rg.asyncSave()
                            sendMessage(sender,"§a§l設定完了！")
                        }
                        else{
                            if(isRunning.get()){
                                sendMessage(sender,"§c§l現在別の処理が走っています")
                                return true
                            }
                            isRunning.set(true)
                            sendMessage(sender,"§a§l設定変更中...")
                            async.execute {
                                City.getPartialMatchCities(args[2]).forEach { city ->
                                    Region.regionMap.filter { it.value.data.city == city.cityId }.forEach { (i, region) ->
                                        region.data.tax = tax
                                        region.asyncSave()
                                    }
                                }
                                isRunning.set(false)
                                sendMessage(sender,"§a§l設定完了！")
                            }
                        }
                        return true
                    }

                    if(args[1]=="city") {

                        val cities = City.getPartialMatchCities(args[2])

                        if (cities.isEmpty()) {
                            sendMessage(sender, "存在しない都市")
                            return false
                        }

                        if(isRunning.get()){
                            sendMessage(sender,"§c§l現在別の処理が走っています")
                            return true
                        }
                        isRunning.set(true)
                        sendMessage(sender,"§a§l設定変更中...")
                        async.execute {
                            cities.forEach { city ->
                                city.data.tax = tax
                                city.asyncSave()
                            }
                            isRunning.set(false)
                            sendMessage(sender, "§a§l設定完了！")
                        }
                        return true
                    }

                    sendMessage(sender, "§c§l/mreop tax rg/city id tax")

                    return  true
                }


                "init" ->{

                    val id = args[1].toIntOrNull()
                    val price =  args[2].toDouble()
                    if(id!=null) {
                        val rg = Region.regionMap[id] ?: return false
                        rg.initialize(Region.Status.ON_SALE, price)
                    }
                    else{
                        City.getPartialMatchCities(args[2]).forEach { city->
                            Region.regionMap.filter { it.value.data.city==city.cityId }.forEach { (i,region) ->
                                sender.sendMessage("§aID${i}の土地を初期化")
                                region.initialize(Region.Status.ON_SALE,price)
                            }
                        }
                    }
                    sendMessage(sender,"§a§l初期化完了")

                    return true
                }

                "starttax" ->{

                    async.execute {
                        sender.sendMessage("税金の徴収開始")
                        City.payTax()
                        sender.sendMessage("税金の徴収完了")
                    }
                }

                "starttaxwarn" ->{
                    async.execute{
                        sender.sendMessage("税金の徴収開始")
                        City.payTaxFromWarnRegion()
                        sender.sendMessage("税金の徴収完了")
                    }
                }

                "search" ->{

                    if (sender !is Player)return false

                    val uuid = Bukkit.getPlayer(args[1])?.uniqueId

                    if (uuid==null){

                        Bukkit.getScheduler().runTaskAsynchronously(plugin,Runnable {
                            val mysql = MySQLManager(plugin,"mre")

                            mysql.query("select id from region where owner_name = '${args[1]}';")?.let {rs->
                                sendMessage(sender,"オーナーの土地")
                                while (rs.next()){
                                    val id = rs.getInt("id")
                                    sendClickMessage(sender,"§e§lID:$id","mre tp $id","飛ぶ")
                                }
                                rs.close()
                            }

                            mysql.query("select region_id from region_user where player = '${args[1]}';")?.let{userRs->

                                sendMessage(sender,"住人の土地")
                                while (userRs.next()){
                                    val id = userRs.getInt("region_id")
                                    sendClickMessage(sender,"§e§lID:$id","mre tp $id","飛ぶ")
                                }
                                userRs.close()
                            }

                            mysql.close()

                        })

                        return true
                    }
                    sendMessage(sender,"オーナーの土地")
                    for (rg in Region.regionMap.filter { it.value.ownerUUID == uuid }.keys){
                        sendClickMessage(sender,"§e§lID:${rg}","mre tp $rg","飛ぶ")

                    }
                    User.userMap.filterKeys { it.first==uuid }.values.forEach { user->
                        sendMessage(sender, "住人の土地")
                        sendClickMessage(sender, "§e§lID:${user.region.id}", "mre tp ${user.region.id}", "飛ぶ")
                    }
                }

                "maxuser" ->{


                    if (args.size != 3)return false
                    if (!NumberUtils.isNumber(args[2]))return false

                    val cities=City.getPartialMatchCities(args[1])
                    val max= args[2].toInt()

                    if (cities.isEmpty()){
                        sendMessage(sender,"存在しない都市")
                        return false
                    }

                    if(isRunning.get()){
                        sendMessage(sender,"§c§l現在別の処理が走っています")
                        return true
                    }

                    isRunning.set(true)
                    sendMessage(sender,"§a§l設定変更中...")

                    async.execute {
                        cities.forEach { city ->

                            city.data.maxUser = max
                            city.asyncSave()
                        }
                        isRunning.set(false)
                        sendMessage(sender, "§a§l設定完了！")
                    }
                }

                "remit" ->{//mreop remit <id>

                    val id = args[1].toIntOrNull()

                    if(id!=null) {

                        val rg = Region.regionMap[id] ?: return false
                        if (rg.taxStatus == Region.TaxStatus.FREE) {
                            rg.taxStatus = Region.TaxStatus.SUCCESS
                        } else {
                            rg.taxStatus = Region.TaxStatus.FREE
                        }

                        rg.asyncSave()

                        if (rg.taxStatus == Region.TaxStatus.FREE) {
                            sendMessage(sender, "§a§lID$id の税金を免除するようにしました")
                        } else {
                            sendMessage(sender, "§a§lID$id の税金を免除を解除しました")
                        }
                    }
                    else{

                        if(isRunning.get()){
                            sendMessage(sender,"§c§l現在別の処理が走っています")
                            return true
                        }
                        isRunning.set(true)
                        sendMessage(sender,"§a§l設定変更中...")
                        async.execute {
                            City.getPartialMatchCities(args[1]).forEach { city ->
                                Region.regionMap.filter { it.value.data.city == city.cityId }.forEach { (i, region) ->

                                    if (region.taxStatus == Region.TaxStatus.FREE) {
                                        region.taxStatus = Region.TaxStatus.SUCCESS
                                    } else {
                                        region.taxStatus = Region.TaxStatus.FREE
                                    }

                                    region.asyncSave()

                                    if (region.taxStatus == Region.TaxStatus.FREE) {
                                        sendMessage(sender, "§a§lID${i}の税金を免除するようにしました")
                                    } else {
                                        sendMessage(sender, "§a§lID${i}の税金を免除を解除しました")
                                    }
                                }
                            }
                            isRunning.set(false)
                            sendMessage(sender,"§a§l設定完了")
                        }
                    }

                    return true
                }

                "buyscore" ->{

                    if (args.size != 3)return false
                    if (!NumberUtils.isNumber(args[2]))return false



                    val cities = City.getPartialMatchCities(args[1])
                    val score= args[2].toInt()

                    if (cities.isEmpty()){
                        sendMessage(sender,"存在しない都市")
                        return false
                    }
                    if(isRunning.get()){
                        sendMessage(sender,"§c§l現在別の処理が走っています")
                        return true
                    }
                    isRunning.set(true)
                    sendMessage(sender,"§a§l設定変更中...")
                    async.execute {
                        cities.forEach { city ->
                            city.data.ownerScore = score
                            city.asyncSave()
                        }
                        isRunning.set(false)
                        sendMessage(sender, "§a§l設定完了！")
                    }
                }

                "livescore" ->{

                    if (args.size != 3)return false
                    if (!NumberUtils.isNumber(args[2]))return false

                    val cities = City.getPartialMatchCities(args[1])
                    val score= args[2].toInt()

                    if (cities.isEmpty()){
                        sendMessage(sender,"存在しない都市")
                        return false
                    }
                    if(isRunning.get()){
                        sendMessage(sender,"§c§l現在別の処理が走っています")
                        return true
                    }
                    isRunning.set(true)
                    sendMessage(sender,"§a§l設定変更中...")
                    async.execute {
                        cities.forEach { city ->
                            city.data.liveScore = score
                            city.asyncSave()
                        }
                        isRunning.set(false)
                        sendMessage(sender, "§a§l設定完了！")
                    }
                }

                "defaultPrice" ->{//mreop defaultPrice <rg/city> id amount

                    if (args.size != 4)return false

                    val price = args[3].toDoubleOrNull() ?: return true
                    if (args[1] == "rg"){
                        val id = args[2].toIntOrNull()

                        if(id!=null) {
                            val rg = Region.regionMap[id] ?: return false
                            rg.data.defaultPrice = price
                            rg.asyncSave()
                        }
                        else{
                            if(isRunning.get()){
                                sendMessage(sender,"§c§l現在別の処理が走っています")
                                return true
                            }
                            isRunning.set(true)
                            sendMessage(sender,"§a§l設定変更中...")
                            async.execute {
                                City.getPartialMatchCities(args[2]).forEach { city ->
                                    Region.regionMap.filter { it.value.data.city == city.cityId }.forEach { (i, region) ->
                                        region.data.defaultPrice = price
                                        region.asyncSave()
                                    }
                                }
                                isRunning.set(false)
                                sendMessage(sender,"§a§l設定完了！")
                            }
                        }

                        return true
                    }
                    if(args[1]=="city"){

                        val cities = City.getPartialMatchCities(args[2])

                        if (cities.isEmpty()){
                            sendMessage(sender,"存在しない都市")
                            return false
                        }
                        if(isRunning.get()){
                            sendMessage(sender,"§c§l現在別の処理が走っています")
                            return true
                        }
                        isRunning.set(true)
                        sendMessage(sender,"§a§l設定変更中...")

                        async.execute {
                            cities.forEach { city ->
                                city.data.defaultPrice = price
                                city.asyncSave()
                            }
                            isRunning.set(false)

                            sendMessage(sender, "§a§l設定完了！")
                        }
                        return true
                    }
                    sendMessage(sender,"/mreop defaultPrice <rg/city> id amount")
                }

                "denytp" -> { //mreop denytp <id>

                    val id = args[1].toIntOrNull()?:return false

                    val rg = Region.regionMap[id]?:return false
                    val data = rg.data

                    data.denyTeleport = ! data.denyTeleport
                    rg.data = data
                    rg.asyncSave()

                    if (data.denyTeleport){
                        sendMessage(sender,"§a§l$id のテレポートを禁止しました")
                    }else{
                        sendMessage(sender,"§a§l$id のテレポートを許可しました")
                    }
                }


                "setprice"->{


                    val id = args[1].toIntOrNull()
                    val price = args[2].toDoubleOrNull()
                    if (price==null || price <0.0 || price == -0.0) {
                        sendMessage(sender, "§c§l金額の設定に問題があります！")
                        return false
                    }
                    if(id!=null){
                        val rg = Region.regionMap[id]?:return false
                        rg.price = price
                        rg.asyncSave()
                        sendMessage(sender,"§a§l${id}の金額を${args[2]}に変更しました")
                    }
                    else{
                        if(isRunning.get()){
                            sendMessage(sender,"§c§l現在別の処理が走っています")
                            return true
                        }
                        isRunning.set(true)
                        sendMessage(sender,"§a§l設定変更中...")
                        async.execute {
                            City.getPartialMatchCities(args[1]).forEach { city ->
                                Region.regionMap.filter { it.value.data.city == city.cityId }.forEach { (_, region) ->
                                    region.price=price
                                    region.asyncSave()
                                }
                            }
                            isRunning.set(false)
                            sendMessage(sender,"§a§l設定完了！")
                        }

                    }



                }

                "citylimit"-> {

                    if(args.size<3){
                        sendMessage(sender, "mre citylimit <city名> <数字>")
                        return false
                    }
                    val limit=args[2].toIntOrNull()?:run{
                        sendMessage(sender, "mre citylimit <city名> <数字>")
                        return false
                    }

                    if (isRunning.get()) {
                        sendMessage(sender, "§c§l現在別の処理が走っています")
                        return true
                    }
                    isRunning.set(true)
                    sendMessage(sender, "§a§l設定変更中...")
                    async.execute {
                        City.getPartialMatchCities(args[1]).forEach { city ->
                            city.data.regionLimitPerPlayer=limit
                            city.asyncSave()
                        }
                        isRunning.set(false)
                        sendMessage(sender, "§a§l設定完了！")
                    }


                }


                "reloadCityData"->{

                    if(args.size<2)return true

                    if(args[1]=="all"){

                        Plugin.async.execute {
                            sendMessage(sender, "再読み込み中...")

                            for (region in Region.regionMap.values) {
                                region.reloadBelongingCity()
                                region.asyncSave()
                            }

                            sendMessage(sender, "土地の所属都市の情報のリロードが完了しました")

                        }
                    }
                    else{
                        val id=args[1].toIntOrNull()?:return true
                        Plugin.async.execute {
                            sendMessage(sender, "再読み込み中...")

                            val region=Region.regionMap[id]?: return@execute
                            region.reloadBelongingCity()
                            region.asyncSave()


                            sendMessage(sender, "土地ID${id}の所属都市の情報のリロードが完了しました")

                        }
                    }

                }

                "payTaxFromWarnRegion"->{
                    Plugin.async.execute {
                        sendMessage(sender, "徴収中...")
                        City.payTaxFromWarnRegion()
                        sendMessage(sender, "徴収中が完了しました")
                    }
                }





                else ->{
                    sendMessage(sender,"§c§l不明なコマンドです！")

                    return false
                }
            }

            return false
        }



        return true
    }

    private fun hasPermission(p:Player, permission:String):Boolean{

        if (p.hasPermission(permission))return true

        sendMessage(p,"§c§lあなたはこのコマンドを使うことができません！")
        return false
    }


    /**
     * 指定リージョンの編集権限を持っているかどうか
     */
    private fun hasRegionPermission(p:Player,id:Int):Boolean{

        if (p.hasPermission(OP))return true

        val data = Region.regionMap[id]?:return false

        if (data.status == Region.Status.LOCK)return false

        if (data.ownerUUID == p.uniqueId)return true

        val userData = User.get(p,id)?:return false

        return userData.permissions.contains(Permission.ALL) && userData.status == "Share"
    }
}