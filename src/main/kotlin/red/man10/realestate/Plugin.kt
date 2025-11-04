/*
    Author forest611,takatronix
 */


package red.man10.realestate

import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.BankAPI
import red.man10.realestate.region.*
import red.man10.realestate.region.user.User
import red.man10.realestate.util.Logger
import red.man10.realestate.util.MenuFramework
import red.man10.realestate.util.MySQLManager
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.concurrent.Executors


class Plugin : JavaPlugin(), Listener {

    companion object{

        lateinit var bank : BankAPI
        lateinit var vault : VaultManager
        lateinit var plugin: Plugin

        val async = Executors.newSingleThreadExecutor()

        const val WAND_NAME = "範囲指定ワンド"
        const val prefix = "[§5Man10RealEstate§f]"

        //保護を無効にするワールド
        var disableWorld = mutableListOf<String>()

        var serverName = "paper"
        var penalty = 2.5 //税金の支払いに失敗した時のペナルティ
        var limitDayOfMonth = 15 //滞納した時に、次に支払いを求める日付(15ならn月15日に)

        var ownableCityNum=-1 //住める都市の数

        var useIFP=true

        private var payTax = true

        var supportedProxy = false
        var otherMREServers = listOf<String>()
    }

    override fun onEnable() { // Plugin startup logic
        if (supportedProxy()){
            server.messenger.registerOutgoingPluginChannel(this, "BungeeCord")
            server.pluginManager.registerEvents(ProxyTeleport,this)
            supportedProxy = true
        }

        saveDefaultConfig()

        vault = VaultManager(this)
        bank = BankAPI(this)
        plugin = this
        MenuFramework.setup(this)

        server.pluginManager.registerEvents(this, this)
        server.pluginManager.registerEvents(Event, this)
        server.pluginManager.registerEvents(MenuFramework.MenuListener,this)

        getCommand("mre")!!.setExecutor(Command)
        getCommand("mreop")!!.setExecutor(Command)

        loadConfig()
        MySQLManager.mysqlQueue(this)

        Logger.logger("プラグイン起動")

        City.asyncLoad()
        Region.asyncLoad()
        User.asyncLoad()


        runDailyTask()
    }

    override fun onDisable() { // Plugin shutdown logic
        Logger.logger("プラグイン終了")
        async.shutdown()
    }


    fun loadConfig(){
        reloadConfig()

        disableWorld = config.getStringList("disableWorld")
        serverName = config.getString("server","paper")!!
        penalty = config.getDouble("penalty",2.5)
        Event.maxContainers = config.getInt("containerAmount",24)
        payTax = config.getBoolean("taxTimer",true)
        saveResource("config.yml", false)
        ownableCityNum=config.getInt("ownableCityNum",-1)
        useIFP=config.getBoolean("useIFP",true)
        otherMREServers=config.getStringList("otherMREServers")
    }

    private fun supportedProxy(): Boolean{
        val spigotConfig = server.spigot().spigotConfig
        val paperConfig = server.spigot().paperConfig
        if (spigotConfig.getBoolean("settings.bungeecord", false)){
            return true
        }
        if (paperConfig.getBoolean("proxies.velocity.enabled", false)) {
            return true
        }

        return false
    }

    private fun runDailyTask(){

        Thread{

            var lastDay = LocalDateTime.now()
            var lastMonth = LocalDateTime.now()

            while (true){

                val now = LocalDateTime.now()
                val isChangeDay = lastDay.dayOfYear != now.dayOfYear
                val isChangeMonth = lastMonth.month != now.month
                val isTaxDay = now.dayOfMonth == limitDayOfMonth

                //日変更
                if (isChangeDay){
                    Logger.logger("日付の変更を検知")
                    lastDay = LocalDateTime.now()
                    Region.regionMap.filterValues { it.span == 2 }.values.forEach { it.payRent() }
                }

                //滞納支払日
                if (isChangeDay && isTaxDay){
                    Logger.logger("滞納日を検知")
                    if (payTax){City.payTaxFromWarnRegion()}
                }

                //週変更(月曜日)
                if (isChangeDay && now.dayOfWeek == DayOfWeek.MONDAY){
                    Logger.logger("週の変更を検知")
                    Region.regionMap.filterValues { it.span == 1 }.values.forEach { it.payRent() }
                }

                //月変更
                if (isChangeMonth){
                    Logger.logger("月の変更を検知")
                    lastMonth = LocalDateTime.now()
                    Region.regionMap.filterValues { it.span == 0 }.values.forEach { it.payRent() }

                    if (payTax){City.payTax()}

                }

                Thread.sleep(1000*60)
            }
        }.start()

    }

}