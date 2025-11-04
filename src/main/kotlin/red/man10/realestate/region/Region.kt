package red.man10.realestate.region

import com.google.gson.Gson
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import red.man10.realestate.Command
import red.man10.realestate.Plugin
import red.man10.realestate.region.user.Permission
import red.man10.realestate.region.user.User
import red.man10.realestate.util.Logger
import red.man10.realestate.util.MySQLManager
import red.man10.realestate.util.Utility
import java.lang.Exception
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class Region {

    companion object{

        val regionMap = ConcurrentHashMap<Int, Region>()
        val gson = Gson()
        var finishLoading=AtomicBoolean(false)

        fun formatStatus(status: Status):String{
            return when(status){
                Status.PROTECTED -> "保護"
                Status.ON_SALE -> "販売中"
                Status.LOCK -> "ロック(使用不可)"
                Status.FREE -> "フリー"
                else -> status.value
            }
        }

        fun formatSpan(span:Int):String{
            return when(span){
                0 -> "1ヶ月"
                1 -> "1週間"
                2 -> "毎日"
                else -> "不明"
            }
        }

        fun asyncLoad(){

            Logger.logger("土地の読み込み開始")
            finishLoading.set(false)

            Plugin.async.execute {
                regionMap.clear()

                val sql = MySQLManager(Plugin.plugin,"Man10RealEstate Loading")

                val rs = sql.query("SELECT * FROM region;")?:return@execute

                while (rs.next()){

                    val id = rs.getInt("id")

                    val rg = Region()

                    rg.id = id
                    rg.name = rs.getString("name")
                    rg.world = rs.getString("world")
                    rg.server = rs.getString("server")

                    val uuid = rs.getString("owner_uuid")

                    if (uuid!=null && uuid!="null"){
                        rg.ownerUUID = UUID.fromString(uuid)
                        rg.ownerName = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).name
                    }
                    try {
                        rg.status = Status.valueOf(rs.getString("status"))
                    }catch (e:Exception){
                        Bukkit.getLogger().info("$id ${rs.getString("status")}")
                    }
                    try {
                        rg.taxStatus = TaxStatus.valueOf(rs.getString("tax_status"))
                    }catch (e:Exception){
                        Bukkit.getLogger().info("$id ${rs.getString("tax_status")}")
                    }

                    rg.price = rs.getDouble("price")

                    rg.span = rs.getInt("span")

                    rg.startPosition = Triple(
                        rs.getInt("sx"),
                        rs.getInt("sy"),
                        rs.getInt("sz")
                    )
                    rg.endPosition = Triple(
                        rs.getInt("ex"),
                        rs.getInt("ey"),
                        rs.getInt("ez")
                    )

                    rg.teleport = Location(
                        Bukkit.getWorld(rg.world),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch")
                    )

                    rg.data = gson.fromJson(rs.getString("data"),RegionData::class.java)

                    regionMap[id] = rg

                    if (Bukkit.getWorld(rg.world) == null){
                        Logger.logger("存在しないワールドの土地",id)
//                        rg.asyncDelete()
//                        Bukkit.getLogger().warning("id:${id}は存在しないワールドだったので、削除しました!")
                    }else {
                        regionMap[id] = rg
                    }
                }
                rs.close()
                sql.close()
                finishLoading.set(true)
            }
        }

        fun create(pos1:Triple<Int,Int,Int>,pos2:Triple<Int,Int,Int>,name:String,price:Double,tp:Location,p:Player):Int{

            val data = RegionData(false,0.0,0.0)


            val query = "INSERT INTO region " +
                    "(server, world, name, status, price, " +
                    "x, y, z, pitch, yaw, " +
                    "sx, sy, sz, ex, ey, ez, data) " +
                    "VALUES(" +
                    "'${Plugin.serverName}', " +
                    "'${tp.world.name}', " +
                    "'${MySQLManager.escapeStringForMySQL(name)}', " +
                    "'OnSale', " +
                    "$price, " +
                    "${tp.x}, " +
                    "${tp.y}, " +
                    "${tp.z}, " +
                    "${tp.pitch}, " +
                    "${tp.yaw}, " +
                    "${pos1.first}, " +
                    "${pos1.second}, " +
                    "${pos1.third}, " +
                    "${pos2.first}, " +
                    "${pos2.second}, " +
                    "${pos2.third}, " +
                    "'${gson.toJson(data)}'); "

            val mysql = MySQLManager(Plugin.plugin,"Man10RealEstate CreateRegion")

            mysql.execute(query)

            val rs = mysql.query("SELECT * FROM region ORDER BY id DESC LIMIT 1;")?:return -1

            if (!rs.next())return -1

            val id = rs.getInt("id")

            rs.close()
            mysql.close()

            val rg = Region()

            rg.name = name
            rg.id = id
            rg.startPosition = pos1
            rg.endPosition = pos2
            rg.teleport = tp

            rg.world = tp.world.name
            rg.server = Plugin.serverName

            rg.price = price

            rg.data = data
            rg.reloadBelongingCity()

            regionMap[id] = rg

            Logger.logger(p,"土地を作成",id)

            return id

        }
    }

    var id = 0
    var name = "RegionName"
    var ownerUUID : UUID? = null
    var ownerName : String? = if (ownerUUID == null) "サーバー" else Bukkit.getOfflinePlayer(ownerUUID!!).name
    var status : Status = Status.ON_SALE //Lock,Danger,Free,OnSale,Protected
    var taxStatus : TaxStatus = TaxStatus.SUCCESS //SUCCESS,WARN,FREE

    var world = "builder"
    var server = "server"

    var startPosition: Triple<Int,Int,Int> = Triple(0,0,0)
    var endPosition: Triple<Int,Int,Int> = Triple(0,0,0)
    lateinit var teleport : Location

    var price : Double = 0.0
    var span = 0 //0:month 1:week 2:day

    //val users=HashMap<UUID, User>()

    lateinit var data : RegionData


    fun asyncSave(){

        MySQLManager.mysqlQueue.add("UPDATE region SET " +
                "owner_uuid = '${ownerUUID}', " +
                "owner_name = '${if (ownerUUID == null)null
                else{Bukkit.getOfflinePlayer(ownerUUID!!).name}}', " +
                "x = ${teleport.x}," +
                "y = ${teleport.y}, " +
                "z = ${teleport.z}, " +
                "pitch = ${teleport.pitch}, " +
                "yaw = ${teleport.yaw}, " +
                "sx = ${startPosition.first}, " +
                "sy = ${startPosition.second}, " +
                "sz = ${startPosition.third}, " +
                "ex = ${endPosition.first}, " +
                "ey = ${endPosition.second}, " +
                "ez = ${endPosition.third}, " +
                "status = '${status}', " +
                "tax_status = '${taxStatus}'," +
                "price = ${price}, " +
                "profit = 0, " +
                "span = ${span}," +
                "data = '${gson.toJson(data)}' " +
                "WHERE id = $id")

    }

    @Synchronized
    fun buy(p: Player){

        val city = City.where(teleport)
        if (city == null){
            Utility.sendMessage(p,"§c§l都市の外に土地があります。運営に報告してください")
            return
        }

        if (taxStatus == TaxStatus.WARN){
            Utility.sendMessage(p,"§c§lこの土地は税金滞納のため購入ができません")
            return
        }

        if (status != Status.ON_SALE){
            Utility.sendMessage(p, "§c§lこの土地は販売されていません！")
            return
        }

        if (p.uniqueId == ownerUUID){
            Utility.sendMessage(p, "§c§lあなたはこの土地のオーナーです！")
            return
        }

        if(!canOwn(p))return

        if (!Plugin.vault.withdraw(p.uniqueId,price)){
            Utility.sendMessage(p, "§c§l電子マネーが足りません！")
            return
        }

        if (ownerUUID != null){
            Plugin.bank.deposit(ownerUUID!!,price,"Man10RealEstate RegionProfit","土地の売上")
        }

        setOwner(p)
        status = Status.PROTECTED
        asyncSave()

        Logger.logger(p,"土地を購入",id)

        Utility.sendMessage(p, "§a§l土地の購入成功！")
    }


    fun initialize(status: Status = Status.ON_SALE, default: Double? = null,tax:Double=0.0){
        val city = City.where(teleport)?:return
        ownerUUID = null
        ownerName = null
        price = default?:city.data.defaultPrice
        this.status = status
        this.taxStatus = TaxStatus.SUCCESS
        this.data = RegionData(false,0.0,tax, city.cityId)
        User.asyncDeleteAllRegionUser(id)
        asyncSave()
    }

    fun asyncDelete(){
        MySQLManager.mysqlQueue.add("DELETE FROM `region` WHERE  `id`=$id;")
        User.asyncDeleteAllRegionUser(id)
        regionMap.remove(id)
        Logger.logger("土地を削除",id)
    }

    //賃料の支払い
    fun payRent(){
        User.userMap.filterKeys { pair -> pair.second == id }.values.forEach { it.payRent() }
        Logger.logger("賃料の支払い",id)
    }

    //住人の取得
    fun getUserList(): List<User> {
        return User.fromRegion(id)
    }

//    fun getUser(player:Player):User?{
//        return users[player.uniqueId]
//    }


    fun canOwn(player:Player):Boolean{
        if(Plugin.ownableCityNum!=-1){
            val cities=Utility.playerLivedCities(player)
            if(cities.size>=Plugin.ownableCityNum&&!cities.contains(data.city)){

                Utility.sendMessage(player,"§c§lこれ以上他の都市に住むことはできません")

                return false
            }
        }

        val rgNumInCity=regionMap.values.filter { region->region.ownerUUID==player.uniqueId&&region.data.city==data.city }.size
        val city=City.cityMap[data.city]?:return false

        if(rgNumInCity>=city.data.regionLimitPerPlayer){

            Utility.sendMessage(player,"§c§lこれ以上この都市の土地オーナーになることはできません")

            return false
        }

        return true
    }

    fun addUser(player:Player){

        if(player.uniqueId==ownerUUID){
            Utility.sendMessage(player, "§c§lオーナーは住人になれません")
            return
        }

        if(getUserList().size < (City.cityMap[data.city]?.data?.maxUser ?: -1)){
            User(player.uniqueId,this)
                    .asyncSave()

            Utility.sendMessage(player, "§a§lあなたは住人になりました！")

            ownerUUID?.let { uuid ->
                Bukkit.getPlayer(uuid)?.let { owner->
                    Utility.sendMessage(owner, "§a§l${player.name}が住人になりました！")
                }
            }
        }
        else{
            Utility.sendMessage(player, "§c§l土地の居住人数が上限に達しています")
        }

    }

    fun setStatus(player:Player,newStatus: Status){

        if (player.hasPermission(Command.OP)){
            Utility.sendMessage(player,"§a§lID${id}の土地の状態を${Status.display(newStatus)}に変更しました")
            this.status=newStatus
            return
        }


        if (status ==Status.LOCK){
            Utility.sendMessage(player,"§c§lロック状態の土地のステータス変更はできません")
            return
        }

        if (ownerUUID == player.uniqueId) {
            Utility.sendMessage(player,"§e§l土地のステータスを${Status.display(newStatus)}に変更しました")
            this.status=newStatus
            return
        }

        if(newStatus==Status.LOCK){//OP以外がlockにできないように
            return
        }

        val user= User.get(player,id)?:run {
            Utility.sendMessage(player,"§c§l権限がありません")
            return
        }

        if(user.permissions.contains(Permission.ALL) && user.status == "Share"){
            Utility.sendMessage(player,"§e§l土地のステータスを${Status.display(newStatus)}に変更しました")
            this.status=newStatus
            return
        }


        Utility.sendMessage(player,"§c§l権限がありません")

    }

    fun setOwner(player:Player){
        ownerUUID=player.uniqueId
        ownerName=player.name
        User.get(player,id)?.asyncDelete()
        getUserList().forEach {user->
            Permission.values().forEach { permission ->
                user.permissions.remove(permission)
            }
            user.asyncSave()
        }
    }

    fun removeOwner():Boolean{
        ownerUUID=null
        ownerName=null
        return true
    }

    fun reloadBelongingCity(){
        data.city=City.where(teleport)?.cityId
    }

    fun isInRegion(location: Location):Boolean{//自前で作った方が良さそう
        return Utility.isWithinRange(location,startPosition,endPosition,location.world.name,Plugin.serverName)
    }

    fun showRegionData(p:Player){
        Utility.sendMessage(p, "§a==========${name}§a§lの情報==========")
        Utility.sendMessage(p, "§aID:$id")
        Utility.sendMessage(p, "§aステータス:${formatStatus(status)}")
        Utility.sendMessage(p, "§aオーナー:${ownerName}")
        Utility.sendMessage(p, "§a値段:${Utility.format(price)}")
        Utility.sendMessage(p, "§a税額:${Utility.format(City.getTax(id))}")
        if (taxStatus == Region.TaxStatus.WARN){
            Utility.sendMessage(p, "§c§l税金が未払いです")
        }
        Utility.sendMessage(p, "§a==========================================")

        Utility.sendClickMessage(
            p,
            "§d§lブックマークする！＝＞[ブックマーク！]",
            "mre bookmark $id",
            "ブックマークをすると、/mreメニューから テレポートをすることができます"
        )

        if (status == Status.ON_SALE){
            Utility.sendClickMessage(
                p,
                "§a§l§n[土地を買う！]",
                "mre buyconfirm $id",
                "§e§l値段:${Utility.format(price)}"
            )
        }
    }



    //////////////////
    //権限判定関連


    fun canEditBlock(player:Player):Boolean{
        if(player.isOp)return true
        if(status==Region.Status.LOCK)return false
        if(ownerUUID==player.uniqueId)return true
        if(status==Region.Status.DANGER)return true
        return User.get(player,id)?.hasPermission(Permission.BLOCK)?:false
    }

    fun canOpenContainer(player:Player):Boolean{
        if(player.isOp)return true
        if(status==Region.Status.LOCK)return false
        if(ownerUUID==player.uniqueId)return true
        if(status==Region.Status.DANGER)return true
        if(status==Region.Status.FREE)return true
        return User.get(player,id)?.hasPermission(Permission.INVENTORY)?:false
    }

    fun canUseDoor(player:Player):Boolean{
        if(player.isOp)return true
        if(status==Region.Status.LOCK)return false
        if(ownerUUID==player.uniqueId)return true
        if(status==Region.Status.DANGER)return true
        if(status==Region.Status.FREE)return true
        return User.get(player,id)?.hasPermission(Permission.DOOR)?:false
    }

    fun canEditItemFrame(player:Player):Boolean{
        if(player.isOp)return true
        if(status==Region.Status.LOCK)return false
        if(ownerUUID==player.uniqueId)return true
        if(status==Region.Status.DANGER)return true
        return User.get(player,id)?.hasPermission(Permission.ITEM_FRAME)?:false
    }

    fun canAllAction(player:Player):Boolean{
        if(player.isOp)return true
        if(status==Region.Status.LOCK)return false
        if(ownerUUID==player.uniqueId)return true
        if(status==Region.Status.DANGER)return true
        return User.get(player,id)?.hasPermission(Permission.ALL)?:false
    }

    fun canInteract(player: Player):Boolean{
        return player.isOp||ownerUUID==player.uniqueId||User.userMap.keys.contains(Pair(player.uniqueId,id))
    }

    fun isResident(player:Player):Boolean{
        return ownerUUID==player.uniqueId||User.userMap.keys.contains(Pair(player.uniqueId,id))
    }

    fun teleport(player: Player){
        player.teleport(teleport)
    }
    ///
    //////////////////


    data class RegionData(
        var denyTeleport : Boolean,
        var defaultPrice : Double,
        var tax : Double,
        //本当はcityNameもしくはcityIDにするべきだったけど保存名ズラすの面倒でそのままになってる
        var city:String?=null
    )

    enum class TaxStatus(val value : String){
        SUCCESS("SUCCESS"),
        WARN("WARN"),
        FREE("FREE")
    }

    enum class Status(val value : String){
        ON_SALE("OnSale"),
        PROTECTED("Protected"),
        LOCK("Lock"),
        FREE("Free"),
        DANGER("Danger");

        companion object{
            fun display(status: Status):String{
                return when(status){
                    ON_SALE->"販売中"
                    PROTECTED->"保護"
                    LOCK->"ロック"
                    FREE->"フリー"
                    DANGER->"無法地帯"
                }
            }
        }
    }
}