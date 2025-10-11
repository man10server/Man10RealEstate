package red.man10.realestate.region.user

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import red.man10.realestate.Plugin
import red.man10.realestate.region.Region
import red.man10.realestate.util.MySQLManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap

//居住者に関するクラス
class User(val uuid: UUID,val region:Region) {


    companion object{
        val userMap = ConcurrentHashMap<Pair<UUID,Int>, User>()

        fun get(p:Player,id:Int): User?{
            return userMap[Pair(p.uniqueId,id)]
        }

        fun fromRegion(rg:Int): List<User> {
            return userMap.filterKeys { it.second == rg }.values.toList()
        }

        fun asyncLoad(){
            userMap.clear()
            Plugin.async.execute {
                while(!Region.finishLoading.get()){
                    Thread.sleep(1000)
                }
                val sql = MySQLManager(Plugin.plugin,"Man10RealEstate")
                val rs = sql.query("SELECT * FROM `region_user`")?:return@execute
                while (rs.next()){
                    val id = rs.getInt("region_id")
                    val uuid = UUID.fromString(rs.getString("uuid"))
                    val region=Region.regionMap[id]?:continue
                    val user = User(uuid, region)

                    user.status = rs.getString("status")
                    user.paid = rs.getTimestamp("paid_date").toLocalDateTime()
                    user.isRent = rs.getInt("is_rent") == 1
                    user.rentAmount = rs.getDouble("rent")

                    if(rs.getInt("allow_all") == 1){
                        user.permissions.add(Permission.ALL)
                    }
                    if(rs.getInt("allow_block") == 1){
                        user.permissions.add(Permission.BLOCK)
                    }
                    if(rs.getInt("allow_door") == 1){
                        user.permissions.add(Permission.DOOR)
                    }
                    if(rs.getInt("allow_inv") == 1){
                        user.permissions.add(Permission.INVENTORY)
                    }
                    if(rs.getInt("allow_item_frame") == 1){
                        user.permissions.add(Permission.ITEM_FRAME)
                    }

                    userMap[Pair(uuid,id)] = user
                }

                rs.close()
                sql.close()
            }
        }

        //指定リージョンの全ユーザーを削除
        fun asyncDeleteAllRegionUser(regionId: Int){
            MySQLManager.mysqlQueue.add("DELETE FROM `region_user` WHERE `region_id`=$regionId;")
            fromRegion(regionId).forEach { userMap.remove(Pair(it.uuid,it.region.id)) }
        }
    }

    var status = "Share"
    var isRent = false
    var paid = LocalDateTime.now()



    val permissions=ArrayList<Permission>()


    var rentAmount = 0.0

    //辞書にデータがなかったら新規情報として保存
    fun asyncSave(){
        //こっちは新規作成
        if (!userMap.containsKey(Pair(uuid,region.id))){
            val name = Bukkit.getOfflinePlayer(uuid).name
            MySQLManager.mysqlQueue.add("INSERT INTO region_user " +
                    "(region_id, player, uuid, created_time, status, is_rent, paid_date, rent) " +
                    "VALUES (${region.id}, '${name}', '${uuid}', now(), 'Share', ${if (rentAmount>0) 1 else 0}, now(), ${rentAmount});")
            userMap[Pair(uuid,region.id)] = this
            return
        }

        //存在する場合
        MySQLManager.mysqlQueue.add("UPDATE `region_user` " +
                "SET " +
                "`status`='${status}'," +
                "`paid_date`='${paid.format(DateTimeFormatter.ISO_LOCAL_DATE)}'," +
                "`is_rent`='${if (isRent){1}else{0}}',"+
                "`allow_all`='${if (permissions.contains(Permission.ALL)){1}else{0}}'," +
                "`allow_block`='${if (permissions.contains(Permission.BLOCK)){1}else{0}}'," +
                "`allow_inv`='${if (permissions.contains(Permission.INVENTORY)){1}else{0}}'," +
                "`allow_door`='${if (permissions.contains(Permission.DOOR)){1}else{0}}'," +
                "`allow_item_frame`='${if (permissions.contains(Permission.ITEM_FRAME)){1}else{0}}'," +
                "`rent`='${rentAmount}'" +
                " WHERE `uuid`='${uuid}' AND `region_id`='${region.id}';")
    }

    fun asyncDelete(){
        MySQLManager.mysqlQueue.add("DELETE FROM `region_user` WHERE `region_id`=${region.id} AND `uuid`='${uuid}';")
        userMap.remove(Pair(uuid,region.id))
    }

    fun payRent(){
        if (!isRent)return

        if (!Plugin.bank.withdraw(uuid,rentAmount,"Man10RealEstate Rent","賃料の支払い")){
            status = "Lock"
        }else{
            paid = LocalDateTime.now()
            val region = Region.regionMap[region.id]
            if (region?.ownerUUID != null){
                Plugin.bank.deposit(region.ownerUUID!!,rentAmount,"Man10RealEstate Rent","賃料の支払い")
            }
        }

        asyncSave()
    }

    fun switchPermission(permission:Permission){
        if(permissions.contains(permission))permissions.remove(permission)
        else permissions.add(permission)
    }


    fun hasPermission(permission: Permission):Boolean{

        if (status == "Lock")return false
        if (permissions.contains(Permission.ALL))return true

        return permissions.contains(permission)
    }

}