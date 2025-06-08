package top.xiaojiang233.jiaoPei.subcommand

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import top.xiaojiang233.jiaoPei.SubCommand
import top.xiaojiang233.jiaoPei.Utils
import top.xiaojiang233.jiaoPei.manager.BdsmManager
import top.xiaojiang233.jiaoPei.manager.DglabManager

class DglabSubCommand(private val bdsmManager: BdsmManager) : SubCommand {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Utils.prefixedMessage("&c此命令只能由玩家执行！"))
            return false
        }

        if (args.isEmpty()) {
            sendUsage(sender)
            return false
        }

        // 处理停止命令
        if (args[0].equals("stop", ignoreCase = true)) {
            if (args.size < 2) {
                sender.sendMessage(Utils.prefixedMessage("&c请指定要停止电击的玩家名!"))
                return false
            }

            val targetName = args[1]
            val target = Bukkit.getPlayer(targetName)

            if (target == null) {
                sender.sendMessage(Utils.prefixedMessage("&c找不到玩家 &e$targetName&c!"))
                return false
            }

            // 检查目标是否正在被电击
            if (!bdsmManager.isBeingDglabbed(target.uniqueId)) {
                sender.sendMessage(Utils.prefixedMessage("&c玩家 &e${target.name} &c当前没有被电击！"))
                return false
            }

            // 停止电击
            bdsmManager.stopDglab(target.uniqueId)
            sender.sendMessage(Utils.prefixedMessage("&a已停止对 &e${target.name} &a的电击"))
            target.sendMessage(Utils.prefixedMessage("&e${sender.name} &a停止了对你的电击"))
            return true
        }

        // 处理正常的电击命令
        if (args.size < 4) {
            sendUsage(sender)
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

        val typeArg = args[1].lowercase()
        val type = when (typeArg) {
            "continuous", "c" -> DglabManager.DglabType.CONTINUOUS
            "interval", "i" -> DglabManager.DglabType.INTERVAL
            else -> {
                sender.sendMessage(Utils.prefixedMessage("&c无效的电击类型! 使用 &econtinuous &c或 &einterval&c."))
                return false
            }
        }

        // 解析强度参数
        val strength: Int
        try {
            strength = args[2].toInt()
            if (strength < 0 || strength > 100) {
                sender.sendMessage(Utils.prefixedMessage("&c强度必须在 0-100 之间!"))
                return false
            }
        } catch (e: NumberFormatException) {
            sender.sendMessage(Utils.prefixedMessage("&c无效的强度值, 请使用0-100之间的数字!"))
            return false
        }

        // 解析持续时间
        var duration: Int
        try {
            duration = args[3].toInt()
            if (duration < 1 || duration > 60) {
                sender.sendMessage(Utils.prefixedMessage("&c持续时间必须在 1-60 秒之间! 已设置为临界值。"))
                duration = if (duration < 1) 1 else 60
            }
        } catch (e: NumberFormatException) {
            sender.sendMessage(Utils.prefixedMessage("&c无效的持续时间!"))
            return false
        }

        // 解析间隔时间 (仅对间隙模式有效)
        var interval = 20 // 默认20tick
        if (type == DglabManager.DglabType.INTERVAL && args.size >= 5) {
            try {
                interval = args[4].toInt()
                if (interval < 10 || interval > 100) {
                    sender.sendMessage(Utils.prefixedMessage("&c间隔必须在 10-100 tick之间! 已设置为临界值。"))
                    interval = if (interval < 10) 10 else 100
                }
            } catch (e: NumberFormatException) {
                sender.sendMessage(Utils.prefixedMessage("&c无效的间隔值, 使用默认值20tick。"))
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
                "&e${sender.name} &c想要对你使用 &d$typeString" +"电击 &7(强度: $strength) &7(点击接受)",
                "/jiaopei accept ${sender.name}",
                "&a点击接受来自 &e${sender.name} &a的电击\n&7类型: &e$typeString\n&7强度: &e$strength\n&7持续: &e${duration}秒" +
                        if (type == DglabManager.DglabType.INTERVAL) "\n&7间隔: &e${interval}tick" else ""
            )
        } else {
            sender.sendMessage(Utils.prefixedMessage("&c无法发送电击邀请，请确保对方没有正在进行的电击！"))
        }

        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> {
                // 第一个参数可以是"stop"或玩家名
                val completions = mutableListOf("stop")

                // 添加玩家名补全
                Bukkit.getOnlinePlayers()
                    .map { it.name }
                    .filter { it != sender.name }
                    .let { completions.addAll(it) }

                completions.filter { it.startsWith(args[0], ignoreCase = true) }
            }
            2 -> {
                if (args[0].equals("stop", ignoreCase = true)) {
                    // 如果第一个参数是stop，第二个参数是被电击的玩家名
                    Bukkit.getOnlinePlayers()
                        .filter {
                            bdsmManager.isBeingDglabbed(it.uniqueId)
                        }
                        .map { it.name }
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                } else {
                    // 否则是电击类型
                    listOf("continuous", "interval")
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                }
            }
            3 -> {
                // 补全电击强度
                listOf("10", "25", "50", "75", "100")
                    .filter { it.startsWith(args[2], ignoreCase = true) }
            }
            4 -> {
                // 补全持续时间
                listOf("5", "10", "20", "30", "60")
                    .filter { it.startsWith(args[3], ignoreCase = true) }
            }
            5 -> {
                // 如果是间隙模式, 补全间隔时间
                if (args[1].equals("interval", ignoreCase = true)) {
                    listOf("10", "20", "50", "100")
                        .filter { it.startsWith(args[4], ignoreCase = true) }
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage(Utils.prefixedMessage("&c使用方法:"))
        sender.sendMessage(Utils.prefixedMessage("&e/jiaopei bdsm dglab <玩家名> <类型> <强度> <时长> [间隔]"))
        sender.sendMessage(Utils.prefixedMessage("&e/jiaopei bdsm dglab stop <玩家名> &7- 停止对指定玩家的电击"))
        sender.sendMessage(Utils.prefixedMessage("&7类型: &econtinuous&7(持续) 或 &einterval&7(间隙)"))
        sender.sendMessage(Utils.prefixedMessage("&7强度: &e0-100&7, 数值越大伤害越高"))
        sender.sendMessage(Utils.prefixedMessage("&7时长: &e1-60&7秒"))
        sender.sendMessage(Utils.prefixedMessage("&7间隔: &e10-100&7tick (interval类型)"))
    }
}