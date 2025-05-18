package top.xiaojiang233.jiaoPei

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class JiaoPei : JavaPlugin() {

    override fun onEnable() {
        Utils.initialize(this)
        this.getCommand("jiaopei")?.setExecutor(JiaoPeiCommand())
        this.logger.info("JiaoPei plugin has been enabled.")
    }

    override fun onDisable() {
        this.logger.info("JiaoPei plugin has been disabled.")
    }
}
