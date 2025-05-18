package top.xiaojiang233.jiaoPei

import net.md_5.bungee.api.ChatColor as BungeeColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Particle
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable

object Utils {
    private lateinit var plugin: JavaPlugin
    fun initialize(pluginInstance: JavaPlugin) {
        plugin = pluginInstance
    }

    // 获取插件实例
    fun getPlugin(): JavaPlugin {
        return plugin
    }

    fun colorize(message: String): String {
        return ChatColor.translateAlternateColorCodes('&', message)
    }

    fun prefixedMessage(message: String): String {
        return colorize("&b[&d教培&b] &f$message")
    }

    // 创建可点击的命令消息
    fun createClickableCommand(message: String, command: String, hoverText: String): TextComponent {
        val component = TextComponent(colorize(message))
        component.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, command)
        component.hoverEvent = HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            ComponentBuilder(colorize(hoverText)).create()
        )
        return component
    }

    // 发送可点击的命令消息，带前缀
    fun sendClickablePrefixedMessage(sender: CommandSender, message: String, command: String, hoverText: String) {
        if (sender !is Player) {
            sender.sendMessage(prefixedMessage(message))
            return
        }

        val prefix = TextComponent(colorize("&b[&d教培&b] &f"))
        val clickableComponent = createClickableCommand(message, command, hoverText)

        sender.spigot().sendMessage(prefix, clickableComponent)
    }

    fun hasBedInRange(playerName: String, range: Int): Boolean {
        val player = Bukkit.getPlayer(playerName) ?: return false
        val world = player.world
        val playerLocation = player.location

        for (x in -range..range) {
            for (y in -range..range) {
                for (z in -range..range) {
                    val block = world.getBlockAt(
                        playerLocation.blockX + x,
                        playerLocation.blockY + y,
                        playerLocation.blockZ + z
                    )
                    if (block.type.name.contains("BED", ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun isHoldingD1ck(playerName: String): Boolean {
        val a = Bukkit.getPlayer(playerName) ?: return false
        val item = a.inventory.itemInMainHand
        return item.type.name.equals("END_ROD", ignoreCase = true) || item.type.name.equals("LIGHTNING_ROD", ignoreCase = true)
    }

    // Powered by AI
    fun spawnHeartAboveEntity(entity: Entity) {
        val task = object : BukkitRunnable() {
            override fun run() {
                if (!entity.isValid) {
                    cancel()
                    return
                }
                val location = entity.location.add(0.0, 2.0, 0.0)
                entity.world.spawnParticle(Particle.HEART, location, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
        task.runTaskTimer(plugin, 0L, 5L)
        object : BukkitRunnable() {
            override fun run() {
                task.cancel()
            }
        }.runTaskLater(plugin, 20L * 60) // 1分钟后停止
    }

    fun drawParticleLine(
        from: Entity,
        to: Entity,
        particle: Particle = Particle.DRIPPING_WATER,
        step: Double = 0.2
    ) {
        val task = object : BukkitRunnable() {
            override fun run() {
                if (!from.isValid || !to.isValid) {
                    cancel()
                    return
                }
                val start = from.location.add(0.0, from.height / 2, 0.0)
                val end = to.location.add(0.0, to.height / 2, 0.0)
                val direction = end.toVector().subtract(start.toVector())
                val length = direction.length()
                val steps = (length / step).toInt()
                val increment = direction.normalize().multiply(step)

                var current = start.clone()
                for (i in 0..steps) {
                    from.world.spawnParticle(particle, current, 1, 0.0, 0.0, 0.0, 0.0)
                    current.add(increment)
                }
            }
        }
        task.runTaskTimer(plugin, 0L, 5L)
        object : BukkitRunnable() {
            override fun run() {
                task.cancel()
            }
        }.runTaskLater(plugin, 20L * 60) // 1分钟后停止
    }
}

