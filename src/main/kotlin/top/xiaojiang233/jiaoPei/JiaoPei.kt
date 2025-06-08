package top.xiaojiang233.jiaoPei

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import top.xiaojiang233.jiaoPei.manager.BdsmManager

class JiaoPei : JavaPlugin() {

    // 公开BdsmManager实例，供命令类使用
    val bdsmManager = BdsmManager(this)

    override fun onEnable() {
        Utils.initialize(this)
        bdsmManager.initialize()
        this.getCommand("jiaopei")?.setExecutor(JiaoPeiCommand(this))
        this.logger.info("JiaoPei plugin has been enabled.")
    }

    override fun onDisable() {
        this.logger.info("JiaoPei plugin has been disabled.")
    }
}
