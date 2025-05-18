package top.xiaojiang233.jiaoPei.subcommand

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import top.xiaojiang233.jiaoPei.SubCommand
import top.xiaojiang233.jiaoPei.Utils
import org.bukkit.Particle

class DoCommand(private val requestMap: MutableMap<String, String>) : SubCommand {
    // 记录邀请发送的时间，用于超时检查
    private val requestTimes = mutableMapOf<String, Long>()

    // 邀请超时时间（毫秒）
    private val invitationTimeout = 120000L // 2分钟

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val senderPlayer = sender as? Player ?: return false

        if (args.size < 1) {
            sender.sendMessage(Utils.prefixedMessage("&c请指定一个目标玩家！&7用法: &e/jiaopei do <玩家名>"))
            return false
        }

        val target = Bukkit.getPlayer(args[0])
        if (target == null) {
            sender.sendMessage(Utils.prefixedMessage("&c玩家 &e${args[0]} &c不在线！"))
            return false
        }

        if (target.name == sender.name) {
            sender.sendMessage(Utils.prefixedMessage("&c你不能对自己使用此命令！"))
            return false
        }

        // 检查玩家是否已经有发送中的邀请
        if (requestMap.containsKey(sender.name)) {
            sender.sendMessage(Utils.prefixedMessage("&c你已经有一个正在等待回应的教培邀请！"))
            return false
        }

        // 检查床和距离
        if (!checkBedAndDistance(senderPlayer, target)) {
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
                // 攻方发起邀请给受方
                sender.sendMessage(Utils.prefixedMessage("&a你是攻，&e${target.name} &a是受！&7等待对方接受请求..."))

                // 发送可点击的接受消息
                Utils.sendClickablePrefixedMessage(
                    target,
                    "&e${sender.name} &a想要教培你 &7[点击接受]",
                    "/jiaopei accept ${sender.name}",
                    "&a点击接受 &e${sender.name} &a的教培请求\n&7需要在对方5格内且床附近"
                )

                requestMap[sender.name] = target.name
                requestTimes[sender.name] = System.currentTimeMillis()
                setupTimeout(sender.name, target)
            }
            targetHasD1ck -> {
                // 受方发起请求给攻方
                sender.sendMessage(Utils.prefixedMessage("&a你是受，&e${target.name} &a是攻！&7等待对方接受请求..."))

                // 发送可点击的接受消息给攻方
                Utils.sendClickablePrefixedMessage(
                    target,
                    "&e${sender.name} &a想要被你教培 &7[点击接受]",
                    "/jiaopei accept ${sender.name}",
                    "&a点击接受教培 &e${sender.name}\n&7需要在对方5格内且床附近"
                )

                requestMap[sender.name] = target.name
                requestTimes[sender.name] = System.currentTimeMillis()
                setupTimeout(sender.name, target)
            }
            else -> {
                sender.sendMessage(Utils.prefixedMessage("&c你们俩至少有一个人需要拿迪克！&7请使用末地烛或避雷针作为迪克。"))
                target.sendMessage(Utils.prefixedMessage("&c你们俩至少有一个人需要拿迪克！&7请使用末地烛或避雷针作为迪克。"))
            }
        }

        return true
    }

    private fun setupTimeout(senderName: String, target: Player) {
        object : BukkitRunnable() {
            override fun run() {
                if (requestMap[senderName] == target.name) {
                    requestMap.remove(senderName)
                    requestTimes.remove(senderName)

                    val senderPlayer = Bukkit.getPlayer(senderName)
                    if (senderPlayer != null) {
                        senderPlayer.sendMessage(Utils.prefixedMessage("&c你对 &e${target.name} &c的教培请求已超时！"))
                    }
                    if (target.isOnline) {
                        target.sendMessage(Utils.prefixedMessage("&e${senderName} &c的教培请求已超时！"))
                    }
                }
            }
        }.runTaskLater(Utils.getPlugin(), invitationTimeout / 50)
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
        if (args.size == 1 && sender is Player) {
            return Bukkit.getOnlinePlayers()
                .map { it.name }
                .filter { it != sender.name && it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }
}
