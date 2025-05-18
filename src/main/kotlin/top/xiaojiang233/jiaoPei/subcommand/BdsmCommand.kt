package top.xiaojiang233.jiaoPei.subcommand

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import top.xiaojiang233.jiaoPei.SubCommand
import top.xiaojiang233.jiaoPei.Utils
import top.xiaojiang233.jiaoPei.manager.BdsmManager
import top.xiaojiang233.jiaoPei.manager.DglabManager

class BdsmCommand(
    private val bdsmManager: BdsmManager
) : SubCommand {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(Utils.prefixedMessage("&c请指定一个BDSM子命令！"))
            sender.sendMessage(Utils.prefixedMessage("&7用法:"))
            sender.sendMessage(Utils.prefixedMessage("&e/jiaopei bdsm place <start/stop> <玩家名> [时长秒]"))
            sender.sendMessage(Utils.prefixedMessage("&e/jiaopei bdsm dglab <玩家名> <类型> <强度> <时长> [间隔]"))
            return false
        }

        when (args[0].lowercase()) {
            "place" -> return handlePlaceCommand(sender, args.drop(1).toTypedArray())
            "dglab" -> return handleDglabCommand(sender, args.drop(1).toTypedArray())
            else -> {
                sender.sendMessage(Utils.prefixedMessage("&c未知的BDSM子命令: &e${args[0]}&c！&7可用: &eplace&7, &edglab"))
                return false
            }
        }
    }

    private fun handlePlaceCommand(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Utils.prefixedMessage("&c此命令只能由玩家执行！"))
            return false
        }

        if (args.isEmpty()) {
            sender.sendMessage(Utils.prefixedMessage("&c请指定 &estart &c或 &estop&c！&7用法: &e/jiaopei bdsm place <start/stop> <玩家名> [时长秒]"))
            return false
        }

        when (args[0].lowercase()) {
            "start" -> return handlePlaceStart(sender, args.drop(1).toTypedArray())
            "stop" -> return handlePlaceStop(sender, args.drop(1).toTypedArray())
            else -> {
                sender.sendMessage(Utils.prefixedMessage("&c未知的place子命令: &e${args[0]}&c！&7应为 &estart &7或 &estop"))
                return false
            }
        }
    }

    private fun handleDglabCommand(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Utils.prefixedMessage("&c此命令只能由玩家执行！"))
            return false
        }

        if (args.isEmpty()) {
            showDglabUsage(sender)
            return false
        }

        val targetName = args[0]
        val target = Bukkit.getPlayer(targetName)

        if (target == null) {
            sender.sendMessage(Utils.prefixedMessage("&c找不到玩家 &e$targetName&c!"))
            return false
        }

        if (target.name == sender.name) {
            sender.sendMessage(Utils.prefixedMessage("&c你不能对自己使用电击！"))
            return false
        }

        /*if (!bdsmManager.isPlacedBy(target.uniqueId, sender.uniqueId)) {
            sender.sendMessage(Utils.prefixedMessage("&c你必须先束缚 &e${target.name} &c才能对其使用电击！"))
            return false
        }*/

        if (args.size < 4) {
            showDglabUsage(sender)
            return false
        }

        val typeArg = args[1].lowercase()
        val type = when (typeArg) {
            "continuous", "c" -> DglabManager.DglabType.CONTINUOUS
            "interval", "i" -> DglabManager.DglabType.INTERVAL
            else -> {
                sender.sendMessage(Utils.prefixedMessage("&c无效的电击类型！使用 &econtinuous &c或 &einterval&c。"))
                return false
            }
        }

        val strength = try {
            args[2].toInt().also {
                if (it !in 0..100) {
                    sender.sendMessage(Utils.prefixedMessage("&c强度必须在 0-100 之间！"))
                    return false
                }
            }
        } catch (e: NumberFormatException) {
            sender.sendMessage(Utils.prefixedMessage("&c无效的强度值，请使用0-100之间的数字！"))
            return false
        }

        val duration = try {
            args[3].toInt().also {
                if (it !in 1..60) {
                    sender.sendMessage(Utils.prefixedMessage("&c持续时间必须在 1-60 秒之间！"))
                    return false
                }
            }
        } catch (e: NumberFormatException) {
            sender.sendMessage(Utils.prefixedMessage("&c无效的持续时间，请使用1-60之间的数字！"))
            return false
        }

        var interval = 20
        if (type == DglabManager.DglabType.INTERVAL && args.size >= 5) {
            interval = try {
                args[4].toInt().also {
                    if (it !in 10..100) {
                        sender.sendMessage(Utils.prefixedMessage("&c间隔必须在 10-100 tick之间！"))
                        return false
                    }
                }
            } catch (e: NumberFormatException) {
                sender.sendMessage(Utils.prefixedMessage("&c无效的间隔值，请使用10-100之间的数字！"))
                return false
            }
        }

        // 发送电击邀请
        val success = bdsmManager.createDglabInvitation(
            sender.uniqueId,
            target.uniqueId,
            type,
            strength,
            duration,
            interval
        )

        if (success) {
            val typeString = if (type == DglabManager.DglabType.CONTINUOUS) "持续" else "间隙"
            sender.sendMessage(Utils.prefixedMessage("&a已向 &e${target.name} &a发送 &d$typeString &a电击邀请"))
            sender.sendMessage(Utils.prefixedMessage("&7强度: &d$strength&7, 持续: &d${duration}秒"))

            if (type == DglabManager.DglabType.INTERVAL) {
                sender.sendMessage(Utils.prefixedMessage("&7间隔: &d${interval}tick"))
            }

            // 发送可点击的接受消息
            Utils.sendClickablePrefixedMessage(
                target,
                "&e${sender.name} &c想要对你使用 &d$typeString" + "电击 &7(强度: $strength) &7[点击接受]",
                "/jiaopei accept ${sender.name}",
                "&a点击接受来自 &e${sender.name} &a的电击\n" +
                "&7类型: &e$typeString\n" +
                "&7强度: &e$strength\n" +
                "&7持续: &e${duration}秒" +
                if (type == DglabManager.DglabType.INTERVAL) "\n&7间隔: &e${interval}tick" else ""
            )
        } else {
            sender.sendMessage(Utils.prefixedMessage("&c无法发送电击邀请，请确保对方没有正在进行的电击！"))
        }

        return true
    }

    private fun handlePlaceStart(sender: Player, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(Utils.prefixedMessage("&c请指定一个目标玩家！&7用法: &e/jiaopei bdsm place start <玩家名> [时长秒]"))
            return false
        }

        val targetName = args[0]
        val target = Bukkit.getPlayer(targetName)

        if (target == null) {
            sender.sendMessage(Utils.prefixedMessage("&c找不到玩家 &e$targetName&c！"))
            return false
        }

        if (target.name == sender.name) {
            sender.sendMessage(Utils.prefixedMessage("&c你不能对自己使用此命令！"))
            return false
        }

        // 检查是否已经有邀请
        if (bdsmManager.hasPlaceInvitation(sender.uniqueId, target.uniqueId)) {
            sender.sendMessage(Utils.prefixedMessage("&c你已经对 &e${target.name} &c发送了place邀请，请等待对方接受或拒绝！"))
            return false
        }

        // 检查是否超过最大邀请数量
        if (bdsmManager.countActiveInvitations(sender.uniqueId) >= 2) {
            sender.sendMessage(Utils.prefixedMessage("&c你已经发送了太多BDSM邀请！&7最多同时发送2个邀请。"))
            return false
        }

        // 解析时长，默认为60秒，最大为300秒(5分钟)
        var duration = 60
        if (args.size > 1) {
            try {
                duration = args[1].toInt()
                if (duration <= 0) {
                    sender.sendMessage(Utils.prefixedMessage("&c时长必须为正数！"))
                    return false
                }
                if (duration > 300) {
                    sender.sendMessage(Utils.prefixedMessage("&c时长不能超过300秒(5分钟)！已设置为最大值300秒。"))
                    duration = 300
                }
            } catch (e: NumberFormatException) {
                sender.sendMessage(Utils.prefixedMessage("&c无效的时长: &e${args[1]}&c！请使用整数。"))
                return false
            }
        }

        // 发送BDSM请求
        val success = bdsmManager.createPlaceInvitation(sender.uniqueId, target.uniqueId, duration)

        if (!success) {
            sender.sendMessage(Utils.prefixedMessage("&c无法发送BDSM邀请！你已达到最大邀请数量(2个)。"))
            return false
        }

        sender.sendMessage(Utils.prefixedMessage("&a你向 &e${target.name} &a发送了BDSM place邀请，持续时间: &d${duration}秒&7，邀请将在2分钟后过期"))

        // 发送可点击的接受消息
        Utils.sendClickablePrefixedMessage(
            target,
            "&e${sender.name} &d想要将你束缚 &d${duration}秒 &7(点击接受)",
            "/jiaopei accept ${sender.name}",
            "&a点击接受 &e${sender.name} &a的BDSM邀请\n&7你将被束缚${duration}秒\n&7(邀请2分钟内有效)"
        )

        return true
    }

    private fun handlePlaceStop(sender: Player, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(Utils.prefixedMessage("&c请指定一个目标玩家！&7用法: &e/jiaopei bdsm place stop <玩家名>"))
            return false
        }

        val targetName = args[0]
        val target = Bukkit.getPlayer(targetName)

        if (target == null) {
            sender.sendMessage(Utils.prefixedMessage("&c找不到玩家 &e$targetName&c！"))
            return false
        }

        // 检查该玩家是否被当前玩家place
        if (!bdsmManager.isPlacedBy(target.uniqueId, sender.uniqueId)) {
            sender.sendMessage(Utils.prefixedMessage("&c玩家 &e${target.name} &c不是由你束缚的！"))
            return false
        }

        // 释放玩家（这会同时停止电击效果）
        bdsmManager.releasePlacedPlayer(target.uniqueId)

        sender.sendMessage(Utils.prefixedMessage("&a你释放了 &e${target.name} &a的束缚！"))
        target.sendMessage(Utils.prefixedMessage("&a你被 &e${sender.name} &a释放了！"))

        return true
    }

    private fun showDglabUsage(sender: CommandSender) {
        sender.sendMessage(Utils.prefixedMessage("&c使用方法: &e/jiaopei bdsm dglab <玩家名> <类型> <强度> <时长> [间隔]"))
        sender.sendMessage(Utils.prefixedMessage("&7类型: &econtinuous&7(持续) 或 &einterval&7(间隙)"))
        sender.sendMessage(Utils.prefixedMessage("&7强度: &e0-100&7, 数值越大伤害越高"))
        sender.sendMessage(Utils.prefixedMessage("&7时长: &e1-60&7秒"))
        sender.sendMessage(Utils.prefixedMessage("&7间隔: &e10-100&7tick (interval类型)"))
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        when (args.size) {
            1 -> return listOf("place", "dglab").filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> {
                return when (args[0].lowercase()) {
                    "place" -> listOf("start", "stop").filter { it.startsWith(args[1], ignoreCase = true) }
                    "dglab" -> Bukkit.getOnlinePlayers()
                        .map { it.name }
                        .filter { it != sender.name && it.startsWith(args[1], ignoreCase = true) }
                    else -> emptyList()
                }
            }
            3 -> {
                if (args[0].equals("place", ignoreCase = true)) {
                    if (args[1].equals("start", ignoreCase = true) || args[1].equals("stop", ignoreCase = true)) {
                        return Bukkit.getOnlinePlayers()
                            .map { it.name }
                            .filter { it != sender.name && it.startsWith(args[2], ignoreCase = true) }
                    }
                } else if (args[0].equals("dglab", ignoreCase = true)) {
                    return listOf("continuous", "interval")
                        .filter { it.startsWith(args[2], ignoreCase = true) }
                }
            }
            4 -> {
                if (args[0].equals("dglab", ignoreCase = true)) {
                    return listOf("10", "25", "50", "75", "100")
                        .filter { it.startsWith(args[3], ignoreCase = true) }
                }
            }
            5 -> {
                if (args[0].equals("dglab", ignoreCase = true)) {
                    return listOf("5", "10", "20", "30", "60")
                        .filter { it.startsWith(args[4], ignoreCase = true) }
                }
            }
            6 -> {
                if (args[0].equals("dglab", ignoreCase = true) &&
                    args[2].equals("interval", ignoreCase = true)) {
                    return listOf("10", "20", "50", "100")
                        .filter { it.startsWith(args[5], ignoreCase = true) }
                }
            }
        }
        return emptyList()
    }
}
