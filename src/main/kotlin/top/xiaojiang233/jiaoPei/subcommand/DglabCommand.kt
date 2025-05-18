package top.xiaojiang233.jiaoPei.subcommand

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import top.xiaojiang233.jiaoPei.SubCommand
import top.xiaojiang233.jiaoPei.Utils
import top.xiaojiang233.jiaoPei.manager.BdsmManager
import top.xiaojiang233.jiaoPei.manager.DglabManager

class DglabCommand(private val bdsmManager: BdsmManager) : SubCommand {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Utils.prefixedMessage("&c此命令只能由玩家执行！"))
            return false
        }

        if (args.size < 4) {
            showUsage(sender)
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

        // 检查电击类型和参数
        val typeArg = args[1].lowercase()
        val type = when (typeArg) {
            "continuous", "c" -> DglabManager.DglabType.CONTINUOUS
            "interval", "i" -> DglabManager.DglabType.INTERVAL
            else -> {
                sender.sendMessage(Utils.prefixedMessage("&c无效的电击类型！使用 &econtinuous &c或 &einterval&c。"))
                return false
            }
        }

        // 检查是否需要邀请（如果目标被sender束缚则不需要邀请）
        val requireInvitation = !bdsmManager.isPlacedBy(target.uniqueId, sender.uniqueId)

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
                        sender.sendMessage(Utils.prefixedMessage("&c间隔必须在 10-100 tick之间! 已设置为临界值。"))
                        interval = if (it < 10) 10 else 100
                    }
                }
            } catch (e: NumberFormatException) {
                sender.sendMessage(Utils.prefixedMessage("&c无效的间隔值, 使用默认值20tick。"))
                20
            }
        }

        if (requireInvitation) {
            // 如果需要邀请，发送邀请
            val success = bdsmManager.getDglabManager().createDglabInvitation(
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
                    "&e${sender.name} &c想要对你使用 &d${typeString}电击 &7(强度: $strength) &7[点击接受]",
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
        } else {
            // 如果目标被sender束缚，直接开始电击
            val success = bdsmManager.getDglabManager().startDglab(
                sender.uniqueId,
                target.uniqueId,
                type,
                strength,
                duration,
                interval,
                false // 不需要邀请
            )

            if (success) {
                val typeString = if (type == DglabManager.DglabType.CONTINUOUS) "持续" else "间隙"
                sender.sendMessage(Utils.prefixedMessage("&a已对 &e${target.name} &a开始 &d$typeString &a电击"))
                sender.sendMessage(Utils.prefixedMessage("&7强度: &d$strength&7, 持续: &d${duration}秒"))

                if (type == DglabManager.DglabType.INTERVAL) {
                    sender.sendMessage(Utils.prefixedMessage("&7间隔: &d${interval}tick"))
                }

                target.sendMessage(Utils.prefixedMessage("&c你被 &e${sender.name} &c强制电击了！"))
                target.sendMessage(Utils.prefixedMessage("&7类型: &e$typeString&7, 强度: &e$strength&7, 持续: &e${duration}秒"))
            } else {
                sender.sendMessage(Utils.prefixedMessage("&c无法开始电击，请稍后再试！"))
            }
        }

        return true
    }

    private fun showUsage(sender: CommandSender) {
        sender.sendMessage(Utils.prefixedMessage("&c使用方法: &e/jiaopei bdsm dglab <玩家名> <类型> <强度> <时长> [间隔]"))
        sender.sendMessage(Utils.prefixedMessage("&7类型: &econtinuous&7(持续) 或 &einterval&7(间隙)"))
        sender.sendMessage(Utils.prefixedMessage("&7强度: &e0-100&7, 数值越大伤害越高"))
        sender.sendMessage(Utils.prefixedMessage("&7时长: &e1-60&7秒"))
        sender.sendMessage(Utils.prefixedMessage("&7间隔: &e10-100&7tick (interval类型)"))
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> {
                // 补全玩家名
                Bukkit.getOnlinePlayers()
                    .map { it.name }
                    .filter { it != sender.name && it.startsWith(args[0], ignoreCase = true) }
            }
            2 -> {
                // 补全电击类型
                listOf("continuous", "interval")
                    .filter { it.startsWith(args[1], ignoreCase = true) }
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
                // 如果是间隙模式，补全间隔时间
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
}
