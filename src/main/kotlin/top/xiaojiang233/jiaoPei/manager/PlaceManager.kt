package top.xiaojiang233.jiaoPei.manager

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import top.xiaojiang233.jiaoPei.Utils
import java.util.*
import kotlin.collections.HashMap

/**
 * Place管理器，负责管理玩家束缚状态
 */
class PlaceManager(private val plugin: JavaPlugin) : Listener {

    // 保存Place邀请的Map: masterUUID -> targetUUID -> 邀请信息
    private val placeInvitations = HashMap<UUID, MutableMap<UUID, PlaceInvitation>>()

    // 保存被束缚的玩家: 被束缚者UUID -> 束缚信息
    private val placedPlayers = HashMap<UUID, PlaceData>()

    // 邀请超时（毫秒）
    private val invitationTimeout = 120000L // 2分钟

    // 每个玩家最大Place邀请数量
    private val maxPlaceInvitationsPerPlayer = 2

    fun initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        // 定期清理过期邀请
        object : BukkitRunnable() {
            override fun run() {
                cleanupExpiredInvitations()
            }
        }.runTaskTimer(plugin, 1200L, 1200L) // 每分钟运行一次
    }


    // 创建place邀请
    fun createPlaceInvitation(masterUUID: UUID, targetUUID: UUID, duration: Int): Boolean {
        // 检查邀请数量限制
        if (countActiveInvitations(masterUUID) >= maxPlaceInvitationsPerPlayer) {
            return false
        }

        val masterInvitations = placeInvitations.getOrPut(masterUUID) { mutableMapOf() }
        masterInvitations[targetUUID] = PlaceInvitation(
            masterUUID,
            targetUUID,
            duration,
            System.currentTimeMillis() + invitationTimeout
        )

        // 设置超时任务
        object : BukkitRunnable() {
            override fun run() {
                if (hasPlaceInvitation(masterUUID, targetUUID)) {
                    // 超时移除邀请
                    masterInvitations.remove(targetUUID)

                    val master = Bukkit.getPlayer(masterUUID)
                    val target = Bukkit.getPlayer(targetUUID)

                    if (master != null) {
                        master.sendMessage(Utils.prefixedMessage("&c你对 &e${target?.name ?: "未知玩家"} &c的束缚邀请已超时！"))
                    }

                    if (target != null) {
                        target.sendMessage(Utils.prefixedMessage("&e${master?.name ?: "未知玩家"} &c的束缚邀请已超时！"))
                    }
                }
            }
        }.runTaskLater(plugin, (invitationTimeout / 50L)) // 转换为tick

        return true
    }

    // 统计玩家的有效邀请数量
    fun countActiveInvitations(masterUUID: UUID): Int {
        val invitations = placeInvitations[masterUUID] ?: return 0
        val currentTime = System.currentTimeMillis()

        // 过滤掉已过期的邀请
        return invitations.values.count { it.expiryTime > currentTime }
    }

    // 检查是否有未过期的place邀请
    fun hasPlaceInvitation(masterUUID: UUID, targetUUID: UUID): Boolean {
        val invitations = placeInvitations[masterUUID] ?: return false
        val invitation = invitations[targetUUID] ?: return false

        // 检查邀请是否过期
        if (invitation.expiryTime < System.currentTimeMillis()) {
            invitations.remove(targetUUID)
            return false
        }

        return true
    }

    // 获取邀请信息
    fun getPlaceInvitation(masterUUID: UUID, targetUUID: UUID): PlaceInvitation? {
        val invitations = placeInvitations[masterUUID] ?: return null
        return invitations[targetUUID]
    }

    // 接受邀请并开始束缚
    fun acceptPlaceInvitation(masterUUID: UUID, targetUUID: UUID): Boolean {
        val invitation = getPlaceInvitation(masterUUID, targetUUID) ?: return false

        // 移除邀请
        placeInvitations[masterUUID]?.remove(targetUUID)

        // 获取玩家对象
        val master = Bukkit.getPlayer(masterUUID) ?: return false
        val target = Bukkit.getPlayer(targetUUID) ?: return false

        // 创建束缚数据
        val placeData = PlaceData(
            masterUUID,
            targetUUID,
            invitation.duration,
            System.currentTimeMillis() + (invitation.duration * 1000L)
        )

        // 存储束缚数据
        placedPlayers[targetUUID] = placeData

        // 应用效果
        target.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, invitation.duration * 20, 0, false, false))

        // 通知玩家
        master.sendMessage(Utils.prefixedMessage("&e${target.name} &a接受了你的束缚邀请！他们将被束缚 &d${invitation.duration}秒"))
        target.sendMessage(Utils.prefixedMessage("&a你接受了 &e${master.name} &a的束缚邀请，你将被束缚 &d${invitation.duration}秒"))

        // 设置计时器以自动释放
        object : BukkitRunnable() {
            override fun run() {
                // 只有当束缚者仍然是该master时才自动释放
                if (isPlacedBy(targetUUID, masterUUID)) {
                    releasePlacedPlayer(targetUUID)
                    val targetPlayer = Bukkit.getPlayer(targetUUID)
                    val masterPlayer = Bukkit.getPlayer(masterUUID)

                    if (targetPlayer != null) {
                        targetPlayer.sendMessage(Utils.prefixedMessage("&a你的束缚状态已结束！"))
                    }

                    if (masterPlayer != null) {
                        masterPlayer.sendMessage(Utils.prefixedMessage("&e${targetPlayer?.name ?: "玩家"} &a的束缚状态已结束！"))
                    }
                }
            }
        }.runTaskLater(plugin, invitation.duration * 20L)

        return true
    }

    // 检查玩家是否被束缚
    fun isPlaced(playerUUID: UUID): Boolean {
        val placeData = placedPlayers[playerUUID] ?: return false

        // 检查束缚是否已过期
        if (placeData.endTime < System.currentTimeMillis()) {
            placedPlayers.remove(playerUUID)
            return false
        }

        return true
    }

    // 检查玩家是否被特定master束缚
    fun isPlacedBy(playerUUID: UUID, masterUUID: UUID): Boolean {
        val placeData = placedPlayers[playerUUID] ?: return false

        // 检查束缚是否已过期
        if (placeData.endTime < System.currentTimeMillis()) {
            placedPlayers.remove(playerUUID)
            return false
        }

        return placeData.masterUUID == masterUUID
    }

    // 释放被束缚的玩家
    fun releasePlacedPlayer(playerUUID: UUID) {
        placedPlayers.remove(playerUUID)

        // 移除效果
        val player = Bukkit.getPlayer(playerUUID) ?: return
        player.removePotionEffect(PotionEffectType.DARKNESS)
    }

    // 获取束缚玩家的master UUID
    fun getMasterUUID(playerUUID: UUID): UUID? {
        val placeData = placedPlayers[playerUUID] ?: return null

        // 检查束缚是否已过期
        if (placeData.endTime < System.currentTimeMillis()) {
            placedPlayers.remove(playerUUID)
            return null
        }

        return placeData.masterUUID
    }

    // 处理玩家移动事件，阻止被束缚的玩家移动
    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val playerUUID = event.player.uniqueId

        // 如果玩家被束缚，阻止移动（允许头部旋转）
        if (isPlaced(playerUUID)) {
            val from = event.from
            val to = event.to ?: return

            // 允许头部旋转，但不允许移动位置
            if (from.x != to.x || from.y != to.y || from.z != to.z) {
                event.isCancelled = true
            }
        }
    }

    // 处理玩家聊天事件，修改被束缚玩家的消息
    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val playerUUID = event.player.uniqueId

        // 如果玩家被束缚，修改消息
        if (isPlaced(playerUUID)) {
            event.message = "唔...♥"
        }
    }

    // 清理过期的邀请
    fun cleanupExpiredInvitations() {
        val currentTime = System.currentTimeMillis()
        val iterator = placeInvitations.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val innerIterator = entry.value.entries.iterator()

            while (innerIterator.hasNext()) {
                val innerEntry = innerIterator.next()
                if (innerEntry.value.expiryTime < currentTime) {
                    innerIterator.remove()
                }
            }

            if (entry.value.isEmpty()) {
                iterator.remove()
            }
        }
    }

    // 数据类
    data class PlaceInvitation(
        val masterUUID: UUID,
        val targetUUID: UUID,
        val duration: Int,
        val expiryTime: Long
    )

    data class PlaceData(
        val masterUUID: UUID,
        val targetUUID: UUID,
        val duration: Int,
        val endTime: Long
    )
}
