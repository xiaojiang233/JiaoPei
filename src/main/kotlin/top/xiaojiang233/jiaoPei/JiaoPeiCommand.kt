package top.xiaojiang233.jiaoPei

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import top.xiaojiang233.jiaoPei.manager.BdsmManager
import top.xiaojiang233.jiaoPei.subcommand.AcceptCommand
import top.xiaojiang233.jiaoPei.subcommand.BdsmCommand
import top.xiaojiang233.jiaoPei.subcommand.DoCommand
import top.xiaojiang233.jiaoPei.subcommand.LeashCommand

class JiaoPeiCommand(private val plugin: JiaoPei) : CommandExecutor, TabCompleter {
    private val subCommandManager = SubCommandManager()
    private val requestMap = mutableMapOf<String, String>()
    private val bdsmManager = plugin.bdsmManager

    init {
        subCommandManager.registerSubCommand("accept", AcceptCommand(requestMap, bdsmManager))
        subCommandManager.registerSubCommand("do", DoCommand(requestMap))
        subCommandManager.registerSubCommand("bdsm", BdsmCommand(bdsmManager))
        subCommandManager.registerSubCommand("leash", LeashCommand(plugin))
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(Utils.prefixedMessage("&c请指定一个子命令！&7可用命令: &e/jiaopei do <玩家>&7, &e/jiaopei accept <玩家>&7, &e/jiaopei bdsm, &e/jiaopei leash"))
            return false
        }

        val subCommand = subCommandManager.getSubCommand(args[0])
        if (subCommand == null) {
            sender.sendMessage(Utils.prefixedMessage("&c未知的子命令：&e${args[0]}&c，请使用有效的子命令！"))
            return false
        }

        return subCommand.execute(sender, args.drop(1).toTypedArray())
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        return when (args.size) {
            1 -> subCommandManager.getSubCommandNames().filter { it.startsWith(args[0], ignoreCase = true) }
            else -> subCommandManager.getSubCommand(args[0])?.tabComplete(sender, args.drop(1).toTypedArray())
        }
    }
}