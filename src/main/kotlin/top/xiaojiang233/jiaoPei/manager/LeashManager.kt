package top.xiaojiang233.jiaoPei.manager

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import top.xiaojiang233.jiaoPei.Utils
import java.util.*
import kotlin.collections.HashMap

/**
 * 牵引管理器，负责管理玩家被栓绳拴住的状态
 */
class LeashManager(private val plugin: JavaPlugin) : Listener {

    // 保存牵引邀请的Map: masterUUID -> targetUUID -> 邀请信息
    private val leashInvitations = HashMap<UUID, MutableMap<UUID, LeashInvitation>>()

    // 保存被牵引的玩家: 被牵引者UUID -> 牵引信息
    private val leashedPlayers = HashMap<UUID, LeashData>()

    // 保存栓在栅栏上的玩家: 被牵引者UUID -> 栅栏位置
    private val fenceLeashed = HashMap<UUID, Location>()

    // 邀请超时（毫秒）
    private val invitationTimeout = 120000L // 2分钟

    // 每个玩家最大牵引邀请数量
    private val maxLeashInvitationsPerPlayer = 2

    fun initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        // 定期清理过期邀请
        object : BukkitRunnable() {
            override fun run() {
                cleanupExpiredInvitations()
            }
        }.runTaskTimer(plugin, 1200L, 1200L)

        // 定期处理牵引效果
        object : BukkitRunnable() {
            override fun run() {
                processLeashEffects()
            }
        }.runTaskTimer(plugin, 1L, 1L)

        // 定期渲染栓绳
        object : BukkitRunnable() {
            override fun run() {
                renderLeashes()
            }
        }.runTaskTimer(plugin, 4L, 4L)
    }

    // 创建牵引邀请
    fun createLeashInvitation(masterUUID: UUID, targetUUID: UUID): Boolean {
        // 检查目标是否已经被牵引
        if (isLeashed(targetUUID)) {
            return false
        }

        // 获取或创建主人的邀请Map
        val masterInvitations = leashInvitations.getOrPut(masterUUID) { mutableMapOf() }

        // 检查是否已经存在邀请
        if (hasLeashInvitation(masterUUID, targetUUID)) {
            return false
        }

        // 检查主人是否已经有太多邀请
        if (countActiveInvitations(masterUUID) >= maxLeashInvitationsPerPlayer) {
            return false
        }

        // 创建新邀请
        masterInvitations[targetUUID] = LeashInvitation(
            masterUUID,
            targetUUID,
            System.currentTimeMillis() + invitationTimeout
        )

        return true
    }

    // 统计玩家的有效邀请数量
    fun countActiveInvitations(masterUUID: UUID): Int {
        val currentTime = System.currentTimeMillis()
        return leashInvitations[masterUUID]?.count { it.value.expiryTime > currentTime } ?: 0
    }

    // 检查是否有未过期的牵引邀请
    fun hasLeashInvitation(masterUUID: UUID, targetUUID: UUID): Boolean {
        val invitation = getLeashInvitation(masterUUID, targetUUID) ?: return false
        return invitation.expiryTime > System.currentTimeMillis()
    }

    // 获取邀请信息
    fun getLeashInvitation(masterUUID: UUID, targetUUID: UUID): LeashInvitation? {
        return leashInvitations[masterUUID]?.get(targetUUID)
    }

    // 接受邀请并开始牵引
    fun acceptLeashInvitation(masterUUID: UUID, targetUUID: UUID): Boolean {
        if (!hasLeashInvitation(masterUUID, targetUUID)) {
            return false
        }

        // 删除邀请
        leashInvitations[masterUUID]?.remove(targetUUID)

        // 创建牵引关系
        leashedPlayers[targetUUID] = LeashData(masterUUID, targetUUID, maxLength = 10.0)

        // 通知双方
        val master = Bukkit.getPlayer(masterUUID)
        val target = Bukkit.getPlayer(targetUUID)

        if (master != null && target != null) {
            master.sendMessage(Utils.prefixedMessage("&a你现在牵引着 &e${target.name}"))
            target.sendMessage(Utils.prefixedMessage("&e${master.name} &a现在牵引着你"))
        }

        return true
    }

    // 检查玩家是否被牵引
    fun isLeashed(playerUUID: UUID): Boolean {
        return leashedPlayers.containsKey(playerUUID)
    }

    // 检查玩家是否被特定master牵引
    fun isLeashedBy(playerUUID: UUID, masterUUID: UUID): Boolean {
        val leashData = leashedPlayers[playerUUID] ?: return false
        return leashData.masterUUID == masterUUID
    }

    // 释放被牵引的玩家
    fun releaseLeashed(playerUUID: UUID) {
        if (!isLeashed(playerUUID)) {
            return
        }

        // 如果玩家栓在栅栏上，也要解除
        fenceLeashed.remove(playerUUID)

        val leashData = leashedPlayers.remove(playerUUID) ?: return
        val master = Bukkit.getPlayer(leashData.masterUUID)
        val target = Bukkit.getPlayer(playerUUID)

        if (master != null && target != null) {
            master.sendMessage(Utils.prefixedMessage("&a你释放了 &e${target.name}"))
            target.sendMessage(Utils.prefixedMessage("&e${master.name} &a释放了你"))
        }
    }

    // 设置栓绳长度
    fun setLeashLength(playerUUID: UUID, length: Double) {
        val leashData = leashedPlayers[playerUUID] ?: return
        leashedPlayers[playerUUID] = leashData.copy(maxLength = length)
    }

    // 获取牵引玩家的master UUID
    fun getMasterUUID(playerUUID: UUID): UUID? {
        return leashedPlayers[playerUUID]?.masterUUID
    }

    // 检查玩家是否栓在栅栏上
    fun isTiedToFence(playerUUID: UUID): Boolean {
        return fenceLeashed.containsKey(playerUUID)
    }

    // 将玩家栓到栅栏上
    fun tieToFence(playerUUID: UUID, fenceLocation: Location): Boolean {
        if (!isLeashed(playerUUID)) {
            return false
        }

        fenceLeashed[playerUUID] = fenceLocation
        return true
    }

    // 从栅栏上解开玩家
    fun untieFromFence(playerUUID: UUID): Boolean {
        return fenceLeashed.remove(playerUUID) != null
    }

    // 处理栓绳右键点击玩家事件
    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) {
            return
        }

        val master = event.player
        val target = event.rightClicked

        if (target !is Player) {
            return
        }

        // 检查是否持有牵引物品(拴绳)
        val itemInHand = master.inventory.itemInMainHand
        if (itemInHand.type != Material.LEAD) {
            return
        }

        // 阻止事件触发minecraft原版功能
        event.isCancelled = true

        // 如果已经牵引该玩家，则释放
        if (isLeashedBy(target.uniqueId, master.uniqueId)) {
            releaseLeashed(target.uniqueId)
            return
        }

        // 检查目标是否已被牵引
        if (isLeashed(target.uniqueId)) {
            master.sendMessage(Utils.prefixedMessage("&c该玩家已经被其他人牵引了！"))
            return
        }

        // 发送牵引邀请
        val success = createLeashInvitation(master.uniqueId, target.uniqueId)
        if (success) {
            master.sendMessage(Utils.prefixedMessage("&a已向 &e${target.name} &a发送牵引邀请"))

            // 发送可点击的接受消息
            Utils.sendClickablePrefixedMessage(
                target,
                "&e${master.name} &a想要牵引你 &7(点击接受)",
                "/jiaopei accept ${master.name}",
                "&a点击接受来自 &e${master.name} &a的牵引请求"
            )
        } else {
            master.sendMessage(Utils.prefixedMessage("&c无法发送牵引邀请！"))
        }
    }

    // 处理玩家与栅栏的交互事件
    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteractBlock(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return

        val player = event.player
        val clickedBlock = event.clickedBlock ?: return

        // 检查是否点击的是栅栏
        if (!isFence(clickedBlock)) return

        // 检查栅栏位置是否已经栓有玩家
        val fenceLocation = clickedBlock.location
        val tiedPlayer = fenceLeashed.entries.find {
            it.value.blockX == fenceLocation.blockX &&
                    it.value.blockY == fenceLocation.blockY &&
                    it.value.blockZ == fenceLocation.blockZ
        }

        // 如果栅栏上已经拴有玩家
        if (tiedPlayer != null) {
            val tiedPlayerUUID = tiedPlayer.key
            val leashData = leashedPlayers[tiedPlayerUUID]

            // 检查是否是该玩家牵引的
            if (leashData != null && leashData.masterUUID == player.uniqueId) {
                // 从栅栏上解开玩家
                if (untieFromFence(tiedPlayerUUID)) {
                    val target = Bukkit.getPlayer(tiedPlayerUUID)
                    if (target != null) {
                        player.sendMessage(Utils.prefixedMessage("&a你将 &e${target.name} &a从栅栏上解开了"))
                        target.sendMessage(Utils.prefixedMessage("&e${player.name} &a将你从栅栏上解开了"))
                    }
                }
                event.isCancelled = true
                return
            }
        }

        // 检查玩家是否是牵引者
        val leashedTargets = leashedPlayers.entries.filter { it.value.masterUUID == player.uniqueId }
        if (leashedTargets.isEmpty()) return

        // 找到第一个被牵引但没有栓在栅栏上的玩家
        val targetEntry = leashedTargets.firstOrNull { !isTiedToFence(it.key) } ?: return
        val targetUUID = targetEntry.key
        val target = Bukkit.getPlayer(targetUUID) ?: return

        // 将玩家栓到栅栏上
        if (tieToFence(targetUUID, clickedBlock.location)) {
            player.sendMessage(Utils.prefixedMessage("&a你将 &e${target.name} &a栓在了栅栏上"))
            target.sendMessage(Utils.prefixedMessage("&e${player.name} &a将你栓在了栅栏上"))
            event.isCancelled = true
        }
    }

    // 判断方块是否是栅栏
    private fun isFence(block: Block): Boolean {
        val type = block.type.name
        return type.contains("FENCE") || type == "NETHER_BRICK_FENCE" || type == "CHAIN"
    }

    // 处理玩家移动事件，实现弹性牵引限制
    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val playerUUID = player.uniqueId

        // 如果玩家被牵引
        if (isLeashed(playerUUID)) {
            // 检查是否栓在栅栏上
            if (isTiedToFence(playerUUID)) {
                val fenceLocation = fenceLeashed[playerUUID] ?: return
                val newLocation = event.to ?: return
                val distance = fenceLocation.distance(newLocation)
                val maxDistance = 3.0 // 栅栏最大距离

                if (distance > maxDistance) {
                    // 计算方向向量并推回玩家
                    val direction = newLocation.toVector().subtract(fenceLocation.toVector()).normalize()
                    val limitedLocation = fenceLocation.clone().add(direction.multiply(maxDistance))

                    // 保持原始Y值以允许跳跃
                    limitedLocation.y = newLocation.y

                    // 设置限制后的位置，而不是取消事件
                    event.to.x = limitedLocation.x
                    event.to.z = limitedLocation.z

                }
                return
            }

            // 获取牵引数据
            val leashData = leashedPlayers[playerUUID] ?: return
            val master = Bukkit.getPlayer(leashData.masterUUID) ?: return

            // 计算移动后的新位置到主人的距离
            val newLocation = event.to ?: return
            val masterLocation = master.location
            val distance = masterLocation.distance(newLocation)

            // 如果超出最大距离，不直接取消移动，而是限制位置
            if (distance > leashData.maxLength) {
                // 计算方向向量并将玩家限制在最大范围内
                val direction = newLocation.toVector().subtract(masterLocation.toVector()).normalize()
                val limitedLocation = masterLocation.clone().add(direction.multiply(leashData.maxLength))

                // 保持原始Y值以允许跳跃
                limitedLocation.y = newLocation.y

                // 设置限制后的位置，而不是取消事件
                event.to.x = limitedLocation.x
                event.to.z = limitedLocation.z

            }
        }
    }

    // 简化牵引效果，如果主人移动太远会拉动被牵引者
    private fun processLeashEffects() {
        // 遍历所有被牵引的玩家
        for (entry in leashedPlayers) {
            val playerUUID = entry.key
            val leashData = entry.value

            // 如果玩家被栓在栅栏上，跳过此玩家
            if (isTiedToFence(playerUUID)) {
                continue
            }

            val player = Bukkit.getPlayer(playerUUID) ?: continue
            val master = Bukkit.getPlayer(leashData.masterUUID) ?: continue

            // 计算玩家与主人之间的距离
            val distance = master.location.distance(player.location)

            // 如果距离超过最大值的120%，主人会拉动被牵引者
            if (distance > leashData.maxLength * 1.2) {
                // 计算从玩家到主人的方向向量
                val direction = Vector(
                    master.location.x - player.location.x,
                    0.0, // 不在垂直方向拉动
                    master.location.z - player.location.z
                ).normalize().multiply(0.5) // 增加拉动速度，使效果更明显

                // 应用向量到玩家的速度
                player.velocity = direction
            }
        }
    }

    // 渲染栓绳效果
    private fun renderLeashes() {
        for (entry in leashedPlayers) {
            val playerUUID = entry.key
            val player = Bukkit.getPlayer(playerUUID) ?: continue

            // 如果玩家栓在栅栏上，从栅栏渲染到玩家
            if (isTiedToFence(playerUUID)) {
                val fenceLocation = fenceLeashed[playerUUID] ?: continue
                fenceLocation.world.spawnParticle(
                    org.bukkit.Particle.CRIT,
                    fenceLocation.clone().add(0.5, 0.5, 0.5),
                    1,
                    0.0, 0.0, 0.0, 0.0
                )

                // 渲染从栅栏到玩家的线
                val playerLoc = player.location.clone().add(0.0, 1.0, 0.0)
                val fenceLoc = fenceLocation.clone().add(0.5, 0.5, 0.5)
                drawParticleLine(fenceLoc, playerLoc)
            } else {
                // 从主人渲染到玩家
                val master = Bukkit.getPlayer(entry.value.masterUUID) ?: continue

                // 渲染从主人到玩家的线
                val masterLoc = master.location.clone().add(0.0, 1.0, 0.0)
                val playerLoc = player.location.clone().add(0.0, 1.0, 0.0)
                drawParticleLine(masterLoc, playerLoc)
            }
        }
    }

    // 绘制粒子线
    private fun drawParticleLine(from: Location, to: Location) {
        val direction = to.clone().subtract(from).toVector()
        val length = direction.length()
        direction.normalize()

        val step = 0.5 // 粒子间距
        val steps = (length / step).toInt()

        for (i in 0..steps) {
            val point = from.clone().add(direction.clone().multiply(i * step))
            from.world.spawnParticle(
                org.bukkit.Particle.CRIT,
                point,
                1,
                0.0, 0.0, 0.0, 0.0
            )
        }
    }

    // 清理过期的邀请
    private fun cleanupExpiredInvitations() {
        val currentTime = System.currentTimeMillis()
        leashInvitations.entries.removeIf { (_, invitations) ->
            invitations.entries.removeIf { (_, invitation) ->
                invitation.expiryTime < currentTime
            }
            invitations.isEmpty()
        }
    }

    // 玩家下线时释放牵引关系
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerUUID = event.player.uniqueId

        // 如果玩家是被牵引者，释放牵引
        if (isLeashed(playerUUID)) {
            releaseLeashed(playerUUID)
        }

        // 如果玩家是牵引者，释放所有被他牵引的玩家
        val leashedByThisPlayer = leashedPlayers.filter { it.value.masterUUID == playerUUID }
        leashedByThisPlayer.forEach { (targetUUID, _) ->
            releaseLeashed(targetUUID)
        }
    }

    // 玩家死亡时释放牵引关系
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val playerUUID = event.entity.uniqueId

        // 如果玩家是被牵引者，释放牵引
        if (isLeashed(playerUUID)) {
            releaseLeashed(playerUUID)
        }

        // 如果玩家是牵引者，释放所有被他牵引的玩家
        val leashedByThisPlayer = leashedPlayers.filter { it.value.masterUUID == playerUUID }
        leashedByThisPlayer.forEach { (targetUUID, _) ->
            releaseLeashed(targetUUID)
        }
    }

    // 数据类
    data class LeashInvitation(
        val masterUUID: UUID,
        val targetUUID: UUID,
        val expiryTime: Long
    )

    data class LeashData(
        val masterUUID: UUID,
        val targetUUID: UUID,
        val maxLength: Double = 10.0   // 绳子的最大长度，可以通过命令修改
    )
}