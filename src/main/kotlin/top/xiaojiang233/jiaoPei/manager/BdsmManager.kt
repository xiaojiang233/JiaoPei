package top.xiaojiang233.jiaoPei.manager

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import top.xiaojiang233.jiaoPei.Utils
import java.util.*

/**
 * BDSM功能总管理器，用于协调Place和Dglab功能
 */
class BdsmManager(private val plugin: JavaPlugin) {
    private val placeManager = PlaceManager(plugin)
    private val dglabManager = DglabManager(plugin)

    // 初始化
    init {
        Bukkit.getPluginManager().registerEvents(placeManager, plugin)
        Bukkit.getPluginManager().registerEvents(dglabManager, plugin)
    }

    // Place相关方法委托
    fun createPlaceInvitation(masterUUID: UUID, targetUUID: UUID, duration: Int) =
        placeManager.createPlaceInvitation(masterUUID, targetUUID, duration)

    fun hasPlaceInvitation(masterUUID: UUID, targetUUID: UUID) =
        placeManager.hasPlaceInvitation(masterUUID, targetUUID)

    fun acceptPlaceInvitation(masterUUID: UUID, targetUUID: UUID) =
        placeManager.acceptPlaceInvitation(masterUUID, targetUUID)

    fun isPlaced(playerUUID: UUID) =
        placeManager.isPlaced(playerUUID)

    fun isPlacedBy(playerUUID: UUID, masterUUID: UUID) =
        placeManager.isPlacedBy(playerUUID, masterUUID)

    fun releasePlacedPlayer(playerUUID: UUID) {
        placeManager.releasePlacedPlayer(playerUUID)
        // 当释放玩家时，同时停止电击效果
        dglabManager.stopDglab(playerUUID)
    }

    fun countActiveInvitations(masterUUID: UUID) =
        placeManager.countActiveInvitations(masterUUID)

    // Dglab相关方法委托
    fun createDglabInvitation(masterUUID: UUID, targetUUID: UUID, type: DglabManager.DglabType,
                             strength: Int, duration: Int, interval: Int = 20) =
        dglabManager.createDglabInvitation(masterUUID, targetUUID, type, strength, duration, interval)

    fun hasDglabInvitation(masterUUID: UUID, targetUUID: UUID) =
        dglabManager.hasInvitation(masterUUID, targetUUID)

    fun acceptDglabInvitation(masterUUID: UUID, targetUUID: UUID) =
        dglabManager.acceptDglabInvitation(masterUUID, targetUUID)

    fun stopDglab(playerUUID: UUID) =
        dglabManager.stopDglab(playerUUID)

    fun isBeingDglabbed(playerUUID: UUID) =
        dglabManager.isBeingDglabbed(playerUUID)

    // 获取DglabManager实例，供子命令使用
    fun getDglabManager() = dglabManager
}
