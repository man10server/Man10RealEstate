package red.man10.realestate.region

import com.google.gson.Gson
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Server
import red.man10.realestate.Plugin
import red.man10.realestate.util.Logger
import red.man10.realestate.util.Utility
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class City constructor(val cityId:String){



    constructor(cityId:String,worldName:String,serverName:String,startPosition: Triple<Int,Int,Int>,endPosition: Triple<Int,Int,Int>,tax:Double):this(cityId){
        this.data=CityData(tax,worldName,serverName, startX = startPosition.first, startY = startPosition.second, startZ = startPosition.third
                , endX = endPosition.first, endY = endPosition.second, endZ = endPosition.third)
    }


    constructor(cityId: String,data:CityData):this(cityId){

        this.data=data

    }


    companion object{

        val cityMap = ConcurrentHashMap<String, City>()
        private val gson = Gson()

        fun getPartialMatchCities(str:String):List<City>{
            val prefix=str.startsWith("%")
            val suffix=str.endsWith("%")
            val cityName=str.removePrefix("%").removeSuffix("%")
            val cities=ArrayList<City>()

            if(prefix&&suffix){
                cityMap.forEach{
                    if(it.key.contains(cityName))cities.add(it.value)
                }
            }
            else if(prefix){
                cityMap.forEach{
                    if(it.key.endsWith(cityName))cities.add(it.value)
                }

            }
            else if(suffix){
                cityMap.forEach{
                    if(it.key.startsWith(cityName))cities.add(it.value)
                }
            }
            else{
                cityMap[cityName]?.let { cities.add(it) }
            }

            return cities.toList()
        }

        fun asyncLoad(){

            Plugin.async.execute {
                cityMap.clear()

                val files = File(Plugin.plugin.dataFolder, File.separator).listFiles()?.toMutableList()?:return@execute

                for (file in files){

                    if (!file.path.endsWith(".json") || file.isDirectory){
                        Bukkit.getLogger().info("${file.name} はJsonファイルではありません")
                        continue
                    }

                    try {

                        val name = file.name.replace(".json","")

                        val reader = FileReader(file)
                        val jsonStr = reader.readText()

                        reader.close()

                        val data = gson.fromJson(jsonStr, CityData::class.java)
                        cityMap[name]= City(name,data)

                        Bukkit.getLogger().info("load city : $name")


                    }catch (e: IOException){
                        Bukkit.getLogger().info(e.message)
                    }catch (e:java.lang.Exception){
                        Bukkit.getLogger().info("Error")
                    }
                }
            }
        }

        fun where(loc: Location):City?{
            for (city in cityMap.values){
                if (Utility.isWithinRange(loc,city.getStart(),city.getEnd(),city.data.world,city.data.server)){
                    return city
                }
            }
            return null
        }

        fun payTax(){
            //税金支払いが必要でかつ、滞納してない土地
            val rgList = Region.regionMap.values.filter { rg -> rg.taxStatus == Region.TaxStatus.SUCCESS && rg.ownerUUID != null }

            Bukkit.getLogger().warning("税金の支払いを行います")

            for (rg in rgList){
                val amount = getTax(rg.id)

                if (!Plugin.bank.withdraw(rg.ownerUUID!!,amount,
                        "Man10RealEstate Tax","税金の支払い")){
                    Logger.logger(rg.ownerUUID!!,"滞納税金の支払い失敗",rg.id)
                    rg.taxStatus = Region.TaxStatus.WARN
                    rg.asyncSave()
                    continue
                }
                rg.taxStatus = Region.TaxStatus.SUCCESS
                rg.asyncSave()
                continue

            }

            Bukkit.getLogger().warning("税金の支払い完了")
        }

        fun payTaxFromWarnRegion(){
            //警告つきの土地のみ
            val rgList = Region.regionMap.values.filter { rg -> rg.taxStatus == Region.TaxStatus.WARN && rg.ownerUUID != null }

            Bukkit.getLogger().warning("滞納都市の税金の支払いを行います")

            for (rg in rgList){
                val amount = getTax(rg.id)

                //ここで支払い失敗したら土地を手放す
                if (!Plugin.bank.withdraw(rg.ownerUUID!!,amount,
                        "Man10RealEstate Tax","税金の支払い(延滞)")){
                    Logger.logger(rg.ownerUUID!!,"税金の支払い失敗 初期化",rg.id)
                    rg.initialize()
                    continue
                }
                rg.taxStatus = Region.TaxStatus.SUCCESS
                rg.asyncSave()
                continue
            }

        }

        //税額を取得 ペナルティなども考慮済みの額
        fun getTax(rgID:Int):Double{
            val rg = Region.regionMap[rgID]?:return 0.0
            if (rg.taxStatus == Region.TaxStatus.FREE)return 0.0
            val city = where(rg.teleport)?:return 0.0

            if (rg.data.tax != 0.0) return rg.data.tax

            val width = rg.startPosition.first.coerceAtLeast(rg.endPosition.first) - rg.startPosition.first.coerceAtMost(rg.endPosition.first) + 1
            val height = rg.startPosition.third.coerceAtLeast(rg.endPosition.third) - rg.startPosition.third.coerceAtMost(rg.endPosition.third) + 1

            return if (rg.taxStatus == Region.TaxStatus.WARN) width * height * city.data.tax * Plugin.penalty else width * height * city.data.tax
        }
    }

    lateinit var data:CityData

    fun getStart(): Triple<Int, Int, Int> {
        return Triple(data.startX,data.startY,data.startZ)
    }
    fun getEnd(): Triple<Int, Int, Int> {
        return Triple(data.endX,data.endY,data.endZ)
    }
    fun setStart(triple: Triple<Int,Int,Int>){
        data.startX = triple.first
        data.startY = triple.second
        data.startZ = triple.third
    }
    fun setEnd(triple: Triple<Int,Int,Int>){
        data.endX = triple.first
        data.endY = triple.second
        data.endZ = triple.third
    }

    fun asyncSave(){

        Plugin.async.execute {

            cityMap[cityId] = this

            try {
                val file = File("${Plugin.plugin.dataFolder}/${cityId}.json")

//                if (file.exists()){
//                    file.delete()
//                }else{
//                    Bukkit.getLogger().info("存在しない都市")
//                    return@execute
//                }

                val jsonStr = gson.toJson(data)
                val writer = FileWriter(file)

                writer.write(jsonStr)
                writer.close()
            }catch (e:IOException){
                Bukkit.getLogger().info(e.message)
                Bukkit.getLogger().info(e.stackTraceToString())
            }

        }
    }

    fun asyncDelete(){
        Plugin.async.execute {
            try {
                val file = File("${Plugin.plugin.dataFolder}/${cityId}.json")
                file.delete()

                Region.regionMap.values.filter { region -> region.data.city==cityId }.forEach { region->
                    region.data.city=null
                    region.asyncSave()
                }

            }catch (e:IOException){
                Bukkit.getLogger().info(e.message)
                Bukkit.getLogger().info(e.stackTraceToString())
            }

        }
    }

    fun registerCityForRegion(){
        //cityの重複は認めない構造になっているので、nullのもののみ見る
        Region.regionMap.filterValues { region -> region.data.city==null  }.values.forEach {region ->
            if(Utility.isWithinRange(region.teleport,getStart(),getEnd(),data.world,data.server)){
                region.data.city=cityId
                region.asyncSave()
            }
        }
    }

    data class CityData(

            var tax:Double=0.0,

            var world:String,
            var server:String,

            var maxUser:Int=100,

            var ownerScore:Int=0,
            var liveScore:Int=0,

            var defaultPrice:Double=0.0,
            var regionLimitPerPlayer:Int=99,

            var startX:Int=0,
            var startY:Int=0,
            var startZ:Int=0,
            var endX :Int=0,
            var endY:Int=0,
            var endZ:Int=0
    )

}