package com.artillexstudios.axmines.mines

import com.artillexstudios.axmines.AxMinesPlugin
import com.artillexstudios.axmines.utils.FileUtils
import java.io.IOException
import java.nio.file.Files
import java.util.Locale
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Mines {
    private val TYPES = HashMap<String, Mine>()
    private val MINES_FOLDER = FileUtils.PLUGIN_DIRECTORY.resolve("mines").toFile()
    private val LOGGER: Logger = LoggerFactory.getLogger(Mines::class.java)

    fun reload() {
        if (MINES_FOLDER.mkdirs()) {
            FileUtils.copyFromResource("mines")
        }

        val files = org.apache.commons.io.FileUtils.listFiles(MINES_FOLDER, arrayOf("yaml", "yml"), true)
            .filterNot { it.name.contains("_example") }
            .toSet()
        TYPES.entries.removeIf { it.value.file !in files }
        files.forEach { file ->
            val name = file.nameWithoutExtension.lowercase(Locale.ENGLISH)
            val mine = TYPES[name]
            when {
                mine == null -> Mine(file)
                mine.file == file -> mine.reload()
                else -> LOGGER.warn("Skipping duplicate mine '{}' from {}", name, file)
            }
        }
        AxMinesPlugin.INSTANCE.syncMineRegions()
    }

    fun delete(mine: Mine): Boolean {
        val name = mine.name.lowercase(Locale.ENGLISH)
        if (TYPES[name] !== mine) return false
        try {
            Files.deleteIfExists(mine.file.toPath())
        } catch (exception: IOException) {
            LOGGER.error("Could not delete mine file {}", mine.file, exception)
            return false
        }
        TYPES.remove(name)
        AxMinesPlugin.INSTANCE.syncMineRegions()
        return true
    }

    fun register(mine: Mine) {
        if (TYPES.containsKey(mine.name.lowercase(Locale.ENGLISH))) {
            LOGGER.warn("A mine named {} has already been registered! Skipping!", mine.name.lowercase(Locale.ENGLISH))
            return
        }

        TYPES[mine.name.lowercase(Locale.ENGLISH)] = mine
    }

    fun valueOf(name: String): Mine {
        return TYPES[name.lowercase(Locale.ENGLISH)] ?: throw IllegalArgumentException("Unknown mine type: $name!")
    }

    fun getTypes(): Map<String, Mine> {
        return TYPES
    }
}
