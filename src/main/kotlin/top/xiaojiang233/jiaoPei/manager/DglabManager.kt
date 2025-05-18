package top.xiaojiang233.jiaoPei.manager

import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import top.xiaojiang233.jiaoPei.Utils
import java.util.*
import kotlin.math.max
import kotlin.math.min

class DglabManager(private val plugin: JavaPlugin) : Listener {

    // 记录正在被电击的玩家及其电击任务
    private val activeDglabs = HashMap<UUID, DglabTask>()

    // 记录电击邀请（仅用于未被束缚的玩家）
    private val dglabInvitations = HashMap<UUID, MutableMap<UUID, DglabInvitation>>()

    // 邀请超时时间（毫秒）
    private val invitationTimeout = 120000L // 2分钟

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)

        // 定期清理过期邀请
        object : BukkitRunnable() {
            override fun run() {
                cleanupExpiredInvitations()
            }
        }.runTaskTimer(plugin, 1200L, 1200L)
    }

    // 电击类型枚举
    enum class DglabType {
        CONTINUOUS, // 持续电击
        INTERVAL   // 间隙电击
    }

    // 创建电击邀请（仅用于未被束缚的玩家）
    fun createDglabInvitation(masterUUID: UUID, targetUUID: UUID, type: DglabType, strength: Int, duration: Int, interval: Int = 20): Boolean {
        val masterInvitations = dglabInvitations.getOrPut(masterUUID) { mutableMapOf() }

        // 检查是否已经有对该玩家的邀请
        if (hasInvitation(masterUUID, targetUUID)) {
            return false
        }

        masterInvitations[targetUUID] = DglabInvitation(
            masterUUID,
            targetUUID,
            type,
            strength,
            duration,
            interval,
            System.currentTimeMillis() + invitationTimeout
        )

        // 设置超时任务
        object : BukkitRunnable() {
            override fun run() {
                if (hasInvitation(masterUUID, targetUUID)) {
                    masterInvitations.remove(targetUUID)

                    val master = Bukkit.getPlayer(masterUUID)
                    val target = Bukkit.getPlayer(targetUUID)

                    master?.sendMessage(Utils.prefixedMessage("&c你对 &e${target?.name ?: "未知玩家"} &c的电击邀请已超时！"))
                    target?.sendMessage(Utils.prefixedMessage("&e${master?.name ?: "未知玩家"} &c的电击邀请已超时！"))
                }
            }
        }.runTaskLater(plugin, invitationTimeout / 50)

        return true
    }

    // 检查邀请是否存在且有效
    fun hasInvitation(masterUUID: UUID, targetUUID: UUID): Boolean {
        val invitations = dglabInvitations[masterUUID] ?: return false
        val invitation = invitations[targetUUID] ?: return false

        if (invitation.expiryTime < System.currentTimeMillis()) {
            invitations.remove(targetUUID)
            return false
        }

        return true
    }

    // 接受电击邀请
    fun acceptDglabInvitation(masterUUID: UUID, targetUUID: UUID): Boolean {
        val invitation = dglabInvitations[masterUUID]?.get(targetUUID) ?: return false

        // 移除邀请
        dglabInvitations[masterUUID]?.remove(targetUUID)

        // 开始电击
        return startDglab(
            masterUUID,
            targetUUID,
            invitation.type,
            invitation.strength,
            invitation.duration,
            invitation.interval,
            false // 已经通过邀请验证，不需要再次验证
        )
    }

    // 开始电击
    fun startDglab(masterUUID: UUID, targetUUID: UUID, type: DglabType, strength: Int, duration: Int, interval: Int = 20, requireInvitation: Boolean): Boolean {
        val target = Bukkit.getPlayer(targetUUID) ?: return false

        if (requireInvitation && !hasInvitation(masterUUID, targetUUID)) {
            return false
        }

        // 如果玩家已经在电击中，取消之前的电击
        stopDglab(targetUUID)

        // 应用电击效果
        val actualStrength = min(100, max(0, strength))
        val actualDuration = min(60, max(1, duration))
        val actualInterval = min(100, max(10, interval))

        val dglabTask = when (type) {
            DglabType.CONTINUOUS -> ContinuousDglabTask(target, actualStrength, actualDuration)
            DglabType.INTERVAL -> IntervalDglabTask(target, actualStrength, actualDuration, actualInterval)
        }

        activeDglabs[targetUUID] = dglabTask
        dglabTask.start()

        return true
    }

    // 停止电击
    fun stopDglab(playerUUID: UUID): Boolean {
        val task = activeDglabs[playerUUID] ?: return false
        task.cancel()
        activeDglabs.remove(playerUUID)
        return true
    }

    // 检查玩家是否在电击中
    fun isBeingDglabbed(playerUUID: UUID): Boolean {
        return activeDglabs.containsKey(playerUUID)
    }

    // 处理玩家聊天事件
    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val playerUUID = event.player.uniqueId
        if (isBeingDglabbed(playerUUID)) {
            event.message = "啊啊啊啊啊呜♥"
        }
    }

    // 清理过期邀请
    private fun cleanupExpiredInvitations() {
        val currentTime = System.currentTimeMillis()
        dglabInvitations.entries.removeIf { (_, invitations) ->
            invitations.entries.removeIf { it.value.expiryTime < currentTime }
            invitations.isEmpty()
        }
    }

    // 电击任务基类
    abstract inner class DglabTask(protected val target: Player, protected val strength: Int, protected val durationSeconds: Int) {
        protected var isCancelled = false

        abstract fun start()

        fun cancel() {
            isCancelled = true
        }

        // 应用单次电击效果
        protected fun applyDglabEffect() {
            if (!target.isOnline || target.isDead || isCancelled) {
                return
            }

            // 播放音效
            target.world.playSound(target.location, Sound.ENTITY_BEE_HURT, 1.0f, 2.0f)
            target.world.playSound(target.location, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.5f, 1.5f)

            // 特效
            target.world.spawnParticle(
                Particle.ELECTRIC_SPARK,
                target.location.add(0.0, 1.0, 0.0),
                15,
                0.3, 0.5, 0.3,
                0.05
            )
            target.world.spawnParticle(
                Particle.WAX_OFF,
                target.location,
                10,
                0.2, 0.5, 0.2,
                0.02
            )

            // 造成伤害，强度越高伤害越大，但不会致死
            val damage = strength / 100.0 * 2.0 // 最大强度时每次造成1颗心的伤害
            if (target.health > damage) {
                target.damage(damage)
            } else if (target.health > 0.5) {
                target.health = 0.5
            }

            target.sendMessage(Utils.prefixedMessage("&c♥ 啊啊啊啊啊呜！！电击！ ♥"))
        }
    }

    // 持续电击任务
    inner class ContinuousDglabTask(target: Player, strength: Int, durationSeconds: Int) : DglabTask(target, strength, durationSeconds) {
        override fun start() {
            object : BukkitRunnable() {
                var elapsedTicks = 0
                val totalTicks = durationSeconds * 20

                override fun run() {
                    if (!target.isOnline || target.isDead || isCancelled) {
                        cancel()
                        activeDglabs.remove(target.uniqueId)
                        return
                    }

                    // 每5ticks(0.25秒)电击一次
                    if (elapsedTicks % 5 == 0) {
                        applyDglabEffect()
                    }

                    elapsedTicks++
                    if (elapsedTicks >= totalTicks) {
                        cancel()
                        activeDglabs.remove(target.uniqueId)
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L)
        }
    }

    // 间隙电击任务
    inner class IntervalDglabTask(
        target: Player,
        strength: Int,
        durationSeconds: Int,
        private val intervalTicks: Int
    ) : DglabTask(target, strength, durationSeconds) {

        override fun start() {
            object : BukkitRunnable() {
                var elapsedTicks = 0
                val totalTicks = durationSeconds * 20

                override fun run() {
                    if (!target.isOnline || target.isDead || isCancelled) {
                        cancel()
                        activeDglabs.remove(target.uniqueId)
                        return
                    }

                    // 按指定间隔应用效果
                    if (elapsedTicks % intervalTicks == 0) {
                        applyDglabEffect()
                    }

                    elapsedTicks++
                    if (elapsedTicks >= totalTicks) {
                        cancel()
                        activeDglabs.remove(target.uniqueId)
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L)
        }
    }

    // 邀请数据类
    data class DglabInvitation(
        val masterUUID: UUID,
        val targetUUID: UUID,
        val type: DglabType,
        val strength: Int,
        val duration: Int,
        val interval: Int,
        val expiryTime: Long
    )
}
