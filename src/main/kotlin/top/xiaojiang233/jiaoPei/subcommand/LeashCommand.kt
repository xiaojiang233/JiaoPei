package top.xiaojiang233.jiaoPei.subcommand

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import top.xiaojiang233.jiaoPei.JiaoPei
import top.xiaojiang233.jiaoPei.SubCommand
import top.xiaojiang233.jiaoPei.Utils

/**
 * 处理牵引相关子命令
 */
class LeashCommand(private val plugin: JiaoPei) : SubCommand {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendHelpMessage(sender)
            return true
        }

        when (args[0].lowercase()) {
            "release" -> {
                return handleRelease(sender, args)
            }
            "length" -> {
                return handleSetLength(sender, args)
            }
            else -> {
                sendHelpMessage(sender)
                return true
            }
        }
    }

    // 处理释放牵引
    private fun handleRelease(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Utils.prefixedMessage("&c只有玩家才能释放牵引！"))
            return true
        }

        if (args.size < 2) {
            sender.sendMessage(Utils.prefixedMessage("&c请指定要释放的玩家名！用法: /jiaopei leash release <玩家名>"))
            return true
        }

        val targetName = args[1]
        val target = Bukkit.getPlayer(targetName)

        if (target == null) {
            sender.sendMessage(Utils.prefixedMessage("&c找不到名为 &e$targetName &c的玩家"))
            return true
        }

        // 检查是否为牵引者
        if (!plugin.bdsmManager.isLeashedBy(target.uniqueId, sender.uniqueId)) {
            sender.sendMessage(Utils.prefixedMessage("&c你没有牵引 &e${target.name}"))
            return true
        }

        // 释放玩家
        plugin.bdsmManager.releaseLeashed(target.uniqueId)
        sender.sendMessage(Utils.prefixedMessage("&a已释放 &e${target.name}"))
        target.sendMessage(Utils.prefixedMessage("&e${sender.name} &a解除了对你的牵引"))
        return true
    }

    // 处理设置栓绳长度
    private fun handleSetLength(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Utils.prefixedMessage("&c只有玩家才能设置栓绳长度！"))
            return true
        }

        if (args.size < 3) {
            sender.sendMessage(Utils.prefixedMessage("&c请指定玩家和长度！用法: /jiaopei leash length <玩家名> <长度>"))
            return true
        }

        val targetName = args[1]
        val target = Bukkit.getPlayer(targetName)

        if (target == null) {
            sender.sendMessage(Utils.prefixedMessage("&c找不到名为 &e$targetName &c的玩家"))
            return true
        }

        // 检查是否为牵引者
        if (!plugin.bdsmManager.isLeashedBy(target.uniqueId, sender.uniqueId)) {
            sender.sendMessage(Utils.prefixedMessage("&c你没有牵引 &e${target.name}"))
            return true
        }

        // 解析长度参数
        val length: Double
        try {
            length = args[2].toDouble()
            if (length < 2.0 || length > 20.0) {
                sender.sendMessage(Utils.prefixedMessage("&c栓绳长度必须在 2.0-20.0 之间！"))
                return true
            }
        } catch (e: NumberFormatException) {
            sender.sendMessage(Utils.prefixedMessage("&c无效的长度值，请使用数字！"))
            return true
        }

        // 设置栓绳长度
        plugin.bdsmManager.setLeashLength(target.uniqueId, length)
        sender.sendMessage(Utils.prefixedMessage("&a已将 &e${target.name} &a的栓绳长度设置为 &d$length"))
        target.sendMessage(Utils.prefixedMessage("&e${sender.name} &a将你的栓绳长度设置为 &d$length"))
        return true
    }

    // 发送帮助信息
    private fun sendHelpMessage(sender: CommandSender) {
        sender.sendMessage(Utils.prefixedMessage("&6=== 牵引命令帮助 ==="))
        sender.sendMessage(Utils.prefixedMessage("&e/jiaopei accept <玩家名> &7- 接受来自指定玩家的牵引邀请"))
        sender.sendMessage(Utils.prefixedMessage("&e/jiaopei leash release <玩家名> &7- 释放一个你正在牵引的玩家"))
        sender.sendMessage(Utils.prefixedMessage("&e/jiaopei leash length <玩家名> <长度> &7- 设置栓绳长度 (2.0-20.0)"))
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        if (args.isEmpty()) {
            return listOf("release", "length")
        }

        when (args[0].lowercase()) {
            "release", "length" -> {
                if (args.size == 2) {
                    return Bukkit.getOnlinePlayers()
                        .filter { it.name != sender.name }
                        .map { it.name }
                }
                if (args[0].lowercase() == "length" && args.size == 3) {
                    return listOf("5.0", "10.0", "15.0", "20.0")
                }
            }
        }

        return emptyList()
    }
}