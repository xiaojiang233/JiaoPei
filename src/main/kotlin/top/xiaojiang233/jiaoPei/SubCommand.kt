package top.xiaojiang233.jiaoPei

import org.bukkit.command.CommandSender

interface SubCommand {
    fun execute(sender: CommandSender, args: Array<out String>): Boolean
    fun tabComplete(sender: CommandSender, args: Array<out String>): List<String>
}

class SubCommandManager {
    private val subCommands = mutableMapOf<String, SubCommand>()

    fun registerSubCommand(name: String, command: SubCommand) {
        subCommands[name.lowercase()] = command
    }

    fun getSubCommand(name: String): SubCommand? {
        return subCommands[name.lowercase()]
    }

    fun getSubCommandNames(): List<String> {
        return subCommands.keys.toList()
    }
}