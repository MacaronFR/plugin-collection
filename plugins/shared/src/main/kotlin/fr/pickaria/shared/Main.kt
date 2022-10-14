package fr.pickaria.shared

import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.sql.Database

class Main : JavaPlugin() {
	private lateinit var database: Database

	override fun onEnable() {
		super.onEnable()

		database = openDatabase(dataFolder.absolutePath + "/database")

		logger.info("Shared plugin loaded!")
	}
}