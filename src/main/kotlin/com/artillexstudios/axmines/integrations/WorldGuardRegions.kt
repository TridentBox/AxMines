package com.artillexstudios.axmines.integrations

import com.artillexstudios.axmines.AxMinesPlugin
import com.artillexstudios.axmines.mines.Mines
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.flags.Flags
import com.sk89q.worldguard.protection.flags.StateFlag
import com.sk89q.worldguard.protection.managers.RemovalStrategy
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion
import java.util.HexFormat
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldLoadEvent

internal class WorldGuardRegions(private val plugin: AxMinesPlugin) : Listener {
    private var syncScheduled = false

    fun scheduleSync() {
        if (syncScheduled) return
        syncScheduled = true
        Bukkit.getScheduler().runTask(plugin, Runnable {
            syncScheduled = false
            reconcile()
        })
    }

    @EventHandler
    fun onWorldLoad(event: WorldLoadEvent) {
        scheduleSync()
    }

    private fun reconcile() {
        val container = WorldGuard.getInstance().platform.regionContainer
        val minesByWorld = Mines.getTypes().mapNotNull { (name, mine) ->
            mine.loadedCuboid?.let { bounds -> Triple(bounds.world.uid, name, bounds) }
        }.groupBy { (worldId) -> worldId }

        for (world in Bukkit.getWorlds()) {
            val mines = minesByWorld[world.uid].orEmpty()
            val manager = container.get(BukkitAdapter.adapt(world))
            if (manager == null) {
                if (mines.isNotEmpty()) {
                    plugin.logger.warning("WorldGuard regions are unavailable in ${world.name}.")
                }
                continue
            }

            val desiredIds = mutableSetOf<String>()
            for ((_, name, bounds) in mines) {
                // '+' separates encoded names from literal IDs without introducing collisions.
                val id = PREFIX + if (name.matches(PLAIN_NAME)) name else "+" + HexFormat.of().formatHex(name.toByteArray())
                desiredIds.add(id)
                val min = BlockVector3.at(bounds.minX, bounds.minY, bounds.minZ)
                val max = BlockVector3.at(bounds.maxX, bounds.maxY, bounds.maxZ)
                val region = ProtectedCuboidRegion(id, min, max)
                val highestPriority = manager.getApplicableRegions(region)
                    .filterNot { it.id.startsWith(PREFIX) }
                    .maxOfOrNull { it.priority } ?: 0
                region.priority = highestPriority.coerceIn(0, Int.MAX_VALUE - 1) + 1
                region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.ALLOW)
                // Keep the surrounding regions' normal build and interaction checks.
                region.setFlag(Flags.PASSTHROUGH, StateFlag.State.ALLOW)

                val existing = manager.getRegion(id)
                if (existing !is ProtectedCuboidRegion || existing.minimumPoint != min ||
                    existing.maximumPoint != max || existing.priority != region.priority || existing.flags != region.flags
                ) {
                    manager.addRegion(region)
                }
            }

            for (id in manager.regions.keys) {
                if (id.startsWith(PREFIX) && id !in desiredIds) {
                    manager.removeRegion(id, RemovalStrategy.UNSET_PARENT_IN_CHILDREN)
                }
            }
        }
    }

    private companion object {
        const val PREFIX = "axmines-mine-"
        val PLAIN_NAME = Regex("[a-z0-9_-]+")
    }
}
