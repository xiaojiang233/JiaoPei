package top.xiaojiang233.jiaoPei.subcommand

import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import top.xiaojiang233.jiaoPei.SubCommand
import top.xiaojiang233.jiaoPei.Utils
import top.xiaojiang233.jiaoPei.manager.BdsmManager
import java.util.*

class AcceptCommand(
    private val requestMap: MutableMap<String, String>,
    private val bdsmManager: BdsmManager
) : SubCommand {
    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val senderEntity = sender as? Player ?: return false

        if (args.size < 1) {
            sender.sendMessage(Utils.prefixedMessage("&c请指定一个玩家！&7用法: &e/jiaopei accept <玩家名>"))
            return false
        }

        val targetName = args[0]
        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            sender.sendMessage(Utils.prefixedMessage("&c玩家 &e${targetName} &c不在线！"))
            return false
        }

        // 获取双方UUID
        val targetUUID = target.uniqueId
        val senderUUID = senderEntity.uniqueId

        // 检查BDSM相关邀请（电击、束缚或牵引）
        if (bdsmManager.hasDglabInvitation(targetUUID, senderUUID)) {
            // 处理电击邀请
            val accepted = bdsmManager.acceptDglabInvitation(targetUUID, senderUUID)
            if (accepted) {
                return true
            }
        } else if (bdsmManager.hasPlaceInvitation(targetUUID, senderUUID)) {
            // 处理束缚邀请
            val accepted = bdsmManager.acceptPlaceInvitation(targetUUID, senderUUID)
            if (accepted) {
                return true
            }
        } else if (bdsmManager.hasLeashInvitation(targetUUID, senderUUID)) {
            // 处理牵引邀请
            val accepted = bdsmManager.acceptLeashInvitation(targetUUID, senderUUID)
            if (accepted) {
                return true
            }
        }

        // 处理教培邀请
        if (requestMap[target.name] != sender.name) {
            sender.sendMessage(Utils.prefixedMessage("&c没有来自 &e${target.name} &c的请求！"))
            return false
        }

        // 检查床和距离条件
        if (!checkBedAndDistance(senderEntity, target)) {
            return false
        }

        val senderHasD1ck = Utils.isHoldingD1ck(sender.name)
        val targetHasD1ck = Utils.isHoldingD1ck(target.name)

        when {
            senderHasD1ck && targetHasD1ck -> {
                sender.sendMessage(Utils.prefixedMessage("&d你们俩要高级吗？&7双方都持有迪克！"))
                target.sendMessage(Utils.prefixedMessage("&d你们俩要高级吗？&7双方都持有迪克！"))
            }
            senderHasD1ck -> {
                // 攻方接受受方的请求
                sender.sendMessage(Utils.prefixedMessage("&a你是攻，&e${target.name} &a是受！&d♥ 约会成功！&d♥"))
                target.sendMessage(Utils.prefixedMessage("&e${sender.name} &a是攻，&a你是受！&d♥ 约会成功！&d♥"))
                Utils.spawnHeartAboveEntity(sender)
                Utils.spawnHeartAboveEntity(target)
                Utils.drawParticleLine(sender, target, Particle.WHITE_SMOKE)
                target.chat("嗯...啊♥")
            }
            targetHasD1ck -> {
                // 受方接受攻方的请求
                sender.sendMessage(Utils.prefixedMessage("&e${target.name} &a是攻，&a你是受！&d♥ 约会成功！&d♥"))
                target.sendMessage(Utils.prefixedMessage("&a你是攻，&e${sender.name} &a是受！&d♥ 约会成功！&d♥"))
                Utils.spawnHeartAboveEntity(sender)
                Utils.spawnHeartAboveEntity(target)
                Utils.drawParticleLine(target, sender, Particle.WHITE_SMOKE)
                sender.chat("嗯...啊♥")
            }
            else -> {
                sender.sendMessage(Utils.prefixedMessage("&c你们俩至少有一个人需要拿迪克！&7请使用末地烛或避雷针作为迪克。"))
                target.sendMessage(Utils.prefixedMessage("&c你们俩至少有一个人需要拿迪克！&7请使用末地烛或避雷针作为迪克。"))
            }
        }

        requestMap.remove(target.name)
        return true
    }

    private fun checkBedAndDistance(sender: Player, target: Player): Boolean {
        // 检查双方是否都在床附近
        if (!Utils.hasBedInRange(sender.name, 5)) {
            sender.sendMessage(Utils.prefixedMessage("&c你的周围5格内必须有一张床！"))
            return false
        }
        if (!Utils.hasBedInRange(target.name, 5)) {
            sender.sendMessage(Utils.prefixedMessage("&c对方的周围5格内必须有一张床！"))
            return false
        }

        // 检查双方距离
        val distance = sender.location.distance(target.location)
        if (distance > 5) {
            sender.sendMessage(Utils.prefixedMessage("&c你们之间的距离不能超过5格！当前距离: &e${String.format("%.1f", distance)}&c格"))
            return false
        }

        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        val completions = mutableListOf<String>()

        if (sender is Player) {
            val senderUUID = sender.uniqueId

            // 添加BDSM邀请的玩家补全
            Bukkit.getOnlinePlayers().forEach { player ->
                if (player.name != sender.name &&
                    (bdsmManager.hasPlaceInvitation(player.uniqueId, senderUUID) ||
                            bdsmManager.hasDglabInvitation(player.uniqueId, senderUUID) ||
                            bdsmManager.hasLeashInvitation(player.uniqueId, senderUUID)) &&
                    player.name.startsWith(args.getOrNull(0) ?: "", ignoreCase = true)
                ) {
                    completions.add(player.name)
                }
            }

            // 添加教培请求的玩家补全
            Bukkit.getOnlinePlayers().forEach { player ->
                if (requestMap[player.name] == sender.name &&
                    player.name.startsWith(args.getOrNull(0) ?: "", ignoreCase = true)
                ) {
                    completions.add(player.name)
                }
            }
        }

        return completions.distinct()
    }
}
